package com.jaguarm.nauvispower.generator;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A steam engine: turns steam into electricity, and is the first thing in the pack that makes any.
 *
 * <p>It pulls its own steam from whichever boilers it touches rather than being pushed at. That
 * choice is what makes the whole chain sleep: an engine with a full energy buffer stops, so it
 * stops drawing steam, so the boiler's buffer fills, so the boiler stops burning coal. A factory
 * with nothing to do costs nothing all the way back to the fuel, which is non-negotiable #5
 * applied to three blocks at once rather than one.
 *
 * <p>Power is Minecraft FE, buffered per machine - PLAN.md settled that over a first-party grid so
 * third-party cables keep working. What the engine publishes is an extract-only view: a generator
 * is not a battery, and a network must not be able to push energy back into it.
 */
public class SteamEngineBlockEntity extends BlockEntity {

    /** One a tick against a boiler's two: Factorio's two engines to one boiler. */
    public static final int STEAM_PER_TICK = 1;

    /**
     * FE made from one tick's steam.
     *
     * <p>Factorio's steam engine is 900 kW and its assembling machine 1 draws 75, so one engine
     * runs twelve assemblers and one boiler - two engines - runs twenty-four. There is no honest
     * conversion between kilowatts and FE, so what is kept is that ratio: this is twelve times
     * the assembler's per-tick cost, exactly. Neither number is identity, so both are tunable,
     * but they are tunable together.
     */
    public static final int ENERGY_PER_TICK = 120;

    /** Five seconds of output. Enough to ride out a stutter in the coal supply. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 100;

    /** One tick's worth. The engine is a converter, not a tank; the boiler does the buffering. */
    public static final int STEAM_CAPACITY = 10;

    /**
     * Unrestricted, so the engine can fill it. What the outside world gets is {@link #cableView}.
     */
    private final SimpleEnergyHandler energy =
            new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_CAPACITY, ENERGY_CAPACITY);

    /**
     * What cables and machines see: extraction only, and a wake-up on the way out.
     *
     * <p>The wake matters as much as the limit. An engine whose buffer is full has gone to sleep,
     * and the thing that gives it work again is somebody taking energy - which happens here.
     */
    private final EnergyHandler cableView = new GeneratorAccess(energy, this::wake);

    /**
     * Steam in, along the engine's own axis.
     *
     * <p>Not extract-only: an engine is a length of pipe that happens to consume, which is what
     * makes a row of them work. The one at the far end pulls from the one before it, and so on
     * back to the boiler - Factorio's arrangement, where you build engines in a line and feed the
     * first.
     */
    private final SteamTank steam = new SteamTank(STEAM_CAPACITY, this::onSteamChanged);

    /**
     * The two neighbours an engine can draw from, cached so a draw is not a lookup.
     *
     * <p>Built on first use rather than in the constructor: a block entity has no level yet, and
     * the axis is read off the block state.
     */
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> behind;
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> ahead;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ENGINE.get(), pos, state);
    }

    public EnergyHandler cableView() {
        return cableView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    public int steam() {
        return steam.getAmountAsInt(0);
    }

    /** What a pipe or the next engine along sees. Registered as {@code Capabilities.Fluid.BLOCK}. */
    public ResourceHandler<FluidResource> steamAccess() {
        return steam;
    }

    /** Called by {@link SteamEngineBlock}, and only ever on a tick this engine asked for. */
    public void serverTick(ServerLevel level) {
        if (energy.getAmountAsInt() >= ENERGY_CAPACITY) {
            // Full. Nothing wakes it but something drawing energy, which GeneratorAccess does.
            return;
        }

        if (steam() < STEAM_PER_TICK && !drawSteam(level)) {
            // No steam anywhere it can reach. A boiler gaining fuel, or a boiler being placed
            // beside it, both arrive as a neighbour change.
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            steam.extract(SteamTank.steamResource(), STEAM_PER_TICK, transaction);
            transaction.commit();
        }
        energy.set(Math.min(ENERGY_CAPACITY, energy.getAmountAsInt() + ENERGY_PER_TICK));
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Fills the engine's small buffer from whatever is on either end of it.
     *
     * <p>Two faces, not six: an engine has fluid connections along its own axis, which is what
     * makes its facing mean something and what lets a row of them chain. It reaches through
     * {@code Capabilities.Fluid.BLOCK}, so a boiler, another engine and a pipe from a mod this one
     * does not compile against are all the same thing to it.
     *
     * <p>It only pulls from a neighbour holding <em>more</em> than it does. Without that rule two
     * engines side by side would pass the same steam back and forth for ever; with it, steam runs
     * downhill from the boiler, which holds far more than any engine, to whichever engine is
     * emptiest.
     */
    private boolean drawSteam(ServerLevel level) {
        if (behind == null) {
            buildCaches(level);
        }
        pullFrom(behind);
        pullFrom(ahead);
        return steam() >= STEAM_PER_TICK;
    }

    private void pullFrom(
            @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache) {
        if (cache == null) {
            return;
        }
        ResourceHandler<FluidResource> source = cache.getCapability();
        if (source == null) {
            return;
        }

        int room = STEAM_CAPACITY - steam();
        if (room <= 0) {
            return;
        }

        FluidResource resource = SteamTank.steamResource();
        // Downhill only. See the note above about two engines and one lump of steam.
        long theirs = 0;
        for (int index = 0; index < source.size(); index++) {
            if (source.getResource(index).equals(resource)) {
                theirs += source.getAmountAsLong(index);
            }
        }
        if (theirs <= steam()) {
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            int taken = source.extract(resource, room, transaction);
            if (taken > 0 && steam.insert(resource, taken, transaction) == taken) {
                transaction.commit();
            }
        }
    }

    private void buildCaches(ServerLevel level) {
        Direction facing = getBlockState().getValue(SteamEngineBlock.FACING);
        behind = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level,
                worldPosition.relative(facing.getOpposite()), facing);
        ahead = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level,
                worldPosition.relative(facing), facing.getOpposite());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /** Steam arriving is one of the two things that restarts a stopped engine. */
    private void onSteamChanged() {
        setChanged();
        wake();
    }

    void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
        steam.serialize(output.child("Steam"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        input.child("Steam").ifPresent(steam::deserialize);
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
