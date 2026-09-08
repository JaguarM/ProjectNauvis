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
 * An inserter: takes one item from the block behind it, puts it into the block in front, over
 * and over. Everything here except what pays for the swing.
 */
public abstract class InserterBlockEntity extends BlockEntity {

    /**
     * How long an inserter that reaches past its own neighbours waits before looking again.
     *
     * <p>A second: long enough that a thousand of them cost fifty simulated moves a tick between
     * them, short enough that the pause after a gap reads as an arm swinging rather than as a
     * jam. Only a tier with {@link InserterBlock#reach()} above one ever uses it, and the class
     * comment says why one has to exist at all.
     */
    public static final int IDLE_RECHECK_TICKS = 20;

    /** Ticks into the current swing. Reaching {@link #swingTicks} delivers the item, or holds there until it can. */
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
     * The block this inserter is wearing, which is where a tier's numbers live.
     *
     * <p>A hard cast, because the block entity type is registered against these blocks and no
     * others. If it ever fails the registration is wrong, and saying so loudly beats running with
     * some other tier's reach.
     */
    protected InserterBlock tier() {
        return (InserterBlock) getBlockState().getBlock();
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
        int hand = tier().handSize(level);
        if (swing == 0 && !move(hand, false)) {
            lookAgainIfOutOfEarshot(level);
            return;
        }

        if (swing < swingTicks()) {
            spendOneTick();
            swing++;
            setChanged();
        }

        if (swing >= swingTicks()) {
            if (!move(hand, true)) {
                // The item went away mid-swing, or the destination filled up. Hold the swing,
                // already paid for, and sleep: a wake from either side costs one attempt and
                // nothing else. Unless it reaches too far to hear either side, which is what the
                // re-check is for.
                lookAgainIfOutOfEarshot(level);
                return;
            }
            swing = 0;
            setChanged();
        }

        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Going to sleep, for an inserter whose ends are too far away to wake it.
     *
     * <p>Nothing at all for a reach of one, which is every inserter that hears its own
     * neighbours; one scheduled look for a reach of two, which is one that cannot. See the class
     * comment: the wake signal travels exactly one block, and no vanilla hook carries it further.
     */
    private void lookAgainIfOutOfEarshot(ServerLevel level) {
        if (tier().reach() > 1) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), IDLE_RECHECK_TICKS);
        }
    }

    /**
     * Moves a handful of one item from the block behind to the block in front, all or nothing.
     *
     * @param hand   how many items the tier's hand holds this swing. See {@link InserterBlock#handSize}.
     * @param commit false to ask whether a move is possible without performing it.
     * @return whether anything was, or would have been, moved.
     */
    private boolean move(int hand, boolean commit) {
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
                int fits;
                try (Transaction probe = Transaction.open(transaction)) {
                    int taken = from.extract(index, item, hand, probe);
                    fits = taken == 0 ? 0 : to.insert(item, taken, probe);
                    // Deliberately not committed: the probe only measured.
                }
                if (fits == 0) {
                    continue;
                }
                if (from.extract(index, item, fits, transaction) != fits
                        || to.insert(item, fits, transaction) != fits) {
                    // Rolls back on the way out of the block: everything is still in the source.
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
     * What this inserter is picking up from, or null if there is nothing within reach behind
     * it.
     */
    protected @Nullable ResourceHandler<ItemResource> sourceHandler() {
        return handler(source);
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

    /**
     * Where this inserter's two ends are.
     *
     * <p>{@link InserterBlock#reach()} blocks away rather than one, and nothing looks at what is
     * in between: a long-handed inserter reaches straight over a belt, a wall or a machine
     * without asking, exactly as it does in Factorio. A capability query at a position does not
     * have to travel there.
     */
    private void buildCaches(ServerLevel level) {
        Direction facing = getBlockState().getValue(InserterBlock.FACING);
        int reach = tier().reach();

        // The context is the face of the block being touched, which points back at us.
        source = BlockCapabilityCache.create(
                Capabilities.Item.BLOCK, level, worldPosition.relative(facing.getOpposite(), reach), facing);
        destination = BlockCapabilityCache.create(
                Capabilities.Item.BLOCK, level, worldPosition.relative(facing, reach), facing.getOpposite());
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
