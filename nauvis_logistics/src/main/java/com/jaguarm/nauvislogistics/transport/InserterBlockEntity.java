package com.jaguarm.nauvislogistics.transport;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An inserter: takes one item from the block behind it, puts it into the block in front, over and
 * over. Everything here except what pays for the swing.
 *
 * <p>It knows nothing about what is on either side. Both are reached through
 * {@code Capabilities.Item.BLOCK}, so a vanilla chest, a furnace, an assembling machine and
 * another mod's machine are all the same thing to it. That is the whole reason inserters are
 * worth building before belts: one block makes every container in the game automatable.
 *
 * <p>What differs between tiers is only the drive - {@link BurnerInserterBlockEntity} burns coal,
 * {@link ElectricInserterBlockEntity} draws from the grid - and how fast it swings. Reach, filters
 * and stack size will join them later; none of those is a different block entity either.
 *
 * <h2>Sleeping, and how it hears about work</h2>
 *
 * <p>Non-negotiable #5, and harder here than for a machine. An assembler can sleep perfectly
 * because everything that gives it work touches its own inventory. An inserter's work arrives
 * in <em>somebody else's</em> inventory, and a chest does not know the inserter exists. Vanilla
 * solves this for hoppers by ticking forever with a cooldown, which is exactly what a base of
 * thousands cannot afford.
 *
 * <p>It turns out no polling is needed. Every {@link BlockEntity#setChanged()} runs
 * {@code Level.updateNeighbourForOutputSignal}, which calls {@code onNeighborChange} on all six
 * neighbours — NeoForge widened it from vanilla's horizontal comparator check. So a chest
 * gaining an item, a furnace finishing a smelt or an assembler banking a craft all reach the
 * inserter beside them for free, as an exact signal rather than a poll. {@link InserterBlock}
 * turns that into a scheduled tick, and this entity stops scheduling the moment it has nothing
 * to do. An idle inserter is not visited at all.
 *
 * <p>{@link BlockCapabilityCache} does the other half: it holds each neighbour's handler so a
 * swing is not a lookup, and drops it by itself when that neighbour is replaced or its chunk
 * cycles. Its invalidation listener is deliberately <em>not</em> used as a wake-up — the
 * contract forbids touching the level from inside it, and every case it would report is one
 * {@code neighborChanged} already reports from a context where scheduling is safe.
 */
public abstract class InserterBlockEntity extends BlockEntity {

    /** Ticks into the current swing. Reaching {@link #swingTicks} delivers the item. */
    private int swing;

    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> source;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> destination;

    protected InserterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Ticks per item moved. Behaviour rather than identity, so tiers may tune it. */
    public abstract int swingTicks();

    /**
     * Whether there is anything to pay a tick of swinging with, readying it if there is.
     *
     * <p>Called before the inserter looks for work, so a tier that has to light something - the
     * burner - lights it here rather than in the middle of a swing.
     */
    protected abstract boolean readyToSwing(ServerLevel level);

    /** Spends one tick's worth of whatever {@link #readyToSwing} promised. */
    protected abstract void spendOneTick();

    /** Whether it could run right now, for the status line a player sees. */
    public abstract boolean running();

    public int swing() {
        return swing;
    }

    /**
     * A chunk that has just loaded has an inserter that has never been woken.
     *
     * <p>Without this, an inserter that went to sleep before a restart, beside a chest that is
     * already full, would stay asleep forever: nothing changes, so nothing notifies it. One
     * scheduled tick per inserter per chunk load is the price, and it is paid once.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /** Called by {@link InserterBlock}, and only ever on a tick this inserter asked for. */
    public void serverTick(ServerLevel level) {
        if (!readyToSwing(level)) {
            // Out of coal, or out of electricity. Whatever supplies it wakes it again; nothing
            // this inserter can do will.
            if (swing != 0) {
                swing = 0;
                setChanged();
            }
            return;
        }

        // Whether there is anything to move is asked once, as a swing starts. Checking it every
        // tick would be a simulated transaction per inserter per tick across a whole base.
        if (swing == 0 && !move(false)) {
            return;
        }

        spendOneTick();
        swing++;

        if (swing >= swingTicks()) {
            if (!move(true)) {
                // The item went away mid-swing, or the destination filled up. Hold the swing and
                // sleep; either side changing wakes it again.
                setChanged();
                return;
            }
            swing = 0;
        }

        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Moves one item from the block behind to the block in front, all or nothing.
     *
     * <p>One transaction spans both halves, so an item that cannot be delivered is never taken.
     * Without that, an inserter aimed at a full chest would destroy one item per swing.
     *
     * @param commit false to ask whether a move is possible without performing it.
     * @return whether an item was, or would have been, moved.
     */
    private boolean move(boolean commit) {
        ResourceHandler<ItemResource> from = handler(source);
        ResourceHandler<ItemResource> to = handler(destination);
        if (from == null || to == null) {
            return false;
        }

        for (int index = 0; index < from.size(); index++) {
            ItemResource item = from.getResource(index);
            if (item.isEmpty()) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                if (from.extract(index, item, 1, transaction) != 1) {
                    continue;
                }
                if (to.insert(item, 1, transaction) != 1) {
                    // Rolls back on the way out of the block: the item is still in the source.
                    continue;
                }
                if (commit) {
                    transaction.commit();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The neighbour's item handler, through a cache built on first use.
     *
     * <p>The caches cannot be made in the constructor: a block entity has no level yet, and the
     * facing is read off the block state. Building them here means one lookup per inserter for
     * the lifetime of the chunk rather than one per swing.
     */
    private @Nullable ResourceHandler<ItemResource> handler(
            @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache) {
        return cache == null ? null : cache.getCapability();
    }

    private void buildCaches(ServerLevel level) {
        Direction facing = getBlockState().getValue(InserterBlock.FACING);

        // The context is the face of the neighbour being touched, which points back at us.
        source = BlockCapabilityCache.create(
                Capabilities.Item.BLOCK, level, worldPosition.relative(facing.getOpposite()), facing);
        destination = BlockCapabilityCache.create(
                Capabilities.Item.BLOCK, level, worldPosition.relative(facing), facing.getOpposite());
    }

    /**
     * Schedules the next tick unless one is already coming.
     *
     * <p>The guard is what makes this safe to call from anywhere, and it matters more here than
     * in a machine: {@code onNeighborChange} fires for every block entity beside this one every
     * time any of them is saved, which in a working factory is constantly.
     */
    void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        if (source == null) {
            buildCaches(serverLevel);
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    /** Anything that could give this inserter work again. */
    protected void onSupplyChanged() {
        setChanged();
        wake();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Swing", swing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        swing = input.getIntOr("Swing", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
