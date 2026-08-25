package com.jaguarm.nauvislogistics.transport;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A burner inserter: takes one item from the block behind it, puts it into the block in front,
 * over and over, burning coal to do it.
 *
 * <p>It knows nothing about what is on either side. Both are reached through
 * {@code Capabilities.Item.BLOCK}, so a vanilla chest, a furnace, an assembling machine and
 * another mod's machine are all the same thing to it. That is the whole reason inserters are
 * worth building before belts: one block makes every container in the game automatable.
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
public class InserterBlockEntity extends BlockEntity {

    /** The only slot: what it burns. Fuel goes in, nothing comes out. */
    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /**
     * Ticks per item moved.
     *
     * <p>The one number in this mod that is not from Factorio's dump, because the dump is
     * recipes and this is behaviour. Factorio's burner inserter manages roughly 0.6 items a
     * second, which is 33 ticks here; 30 is that rounded to something a person can count. Being
     * visibly slow is the point of the burner tier - it is the one you are supposed to want to
     * replace.
     */
    public static final int SWING_TICKS = 30;

    private final InserterFuel fuel = new InserterFuel(SLOT_COUNT, this::onFuelChanged);

    /** What a player or another inserter can put fuel into. Insert-only: see {@link FuelAccess}. */
    private final ResourceHandler<ItemResource> fuelAccess = new FuelAccess(fuel);

    /** Ticks of fuel left. Burns only while actually swinging, so an idle inserter wastes none. */
    private int burnTime;

    /** What the last item of fuel was worth, so a progress display can show a fraction. */
    private int burnTimeTotal;

    /** Ticks into the current swing. Reaching {@link #SWING_TICKS} delivers the item. */
    private int swing;

    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> source;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> destination;

    public InserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSERTER.get(), pos, state);
    }

    public ResourceHandler<ItemResource> fuelAccess() {
        return fuelAccess;
    }

    public InserterFuel fuel() {
        return fuel;
    }

    public int burnTime() {
        return burnTime;
    }

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
        if (burnTime <= 0 && !refuel(level)) {
            // Out of coal. Fuel arriving in the slot wakes it; nothing else can help.
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

        burnTime--;
        swing++;

        if (swing >= SWING_TICKS) {
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

    /** Burns one item of fuel. @return whether there is now fuel to spend. */
    private boolean refuel(ServerLevel level) {
        ItemResource candidate = fuel.getResource(FUEL_SLOT);
        if (candidate.isEmpty()) {
            return false;
        }

        // getBurnTime rather than FuelValues.burnDuration, which is deprecated in favour of it -
        // the null recipe type asks for the plain furnace-fuel value.
        int worth = candidate.toStack(1).getBurnTime(null, level.fuelValues());
        if (worth <= 0) {
            return false;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            if (fuel.extract(FUEL_SLOT, candidate, 1, transaction) != 1) {
                return false;
            }
            transaction.commit();
        }

        burnTime = worth;
        burnTimeTotal = worth;
        setChanged();
        return true;
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

    private void onFuelChanged() {
        setChanged();
        wake();
    }

    /**
     * Spilled when the inserter is broken. See the note in the assembler: this is the hook, not
     * {@code Block#affectNeighborsAfterRemoval}, and getting it wrong silently eats the coal.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        int amount = fuel.getAmountAsInt(FUEL_SLOT);
        if (amount > 0) {
            ItemStack stack = fuel.getResource(FUEL_SLOT).toStack(amount);
            fuel.set(FUEL_SLOT, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.serialize(output.child("Fuel"));
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
        output.putInt("Swing", swing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fuel").ifPresent(fuel::deserialize);
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
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
