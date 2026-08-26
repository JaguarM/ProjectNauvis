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
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

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
     * <p>Factorio's steam engine is 900 kW. There is no honest conversion between that and FE, so
     * this is a round number chosen to feel right next to vanilla's generators rather than derived
     * from anything. It is the sort of number to tune once there is something to spend it on.
     */
    public static final int ENERGY_PER_TICK = 90;

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

    private int steam;

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
        return steam;
    }

    /** Called by {@link SteamEngineBlock}, and only ever on a tick this engine asked for. */
    public void serverTick(ServerLevel level) {
        if (energy.getAmountAsInt() >= ENERGY_CAPACITY) {
            // Full. Nothing wakes it but something drawing energy, which GeneratorAccess does.
            return;
        }

        if (steam < STEAM_PER_TICK && !drawSteam()) {
            // No steam anywhere it can reach. A boiler gaining fuel, or a boiler being placed
            // beside it, both arrive as a neighbour change.
            return;
        }

        steam -= STEAM_PER_TICK;
        energy.set(Math.min(ENERGY_CAPACITY, energy.getAmountAsInt() + ENERGY_PER_TICK));
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Fills the engine's small steam buffer from any boiler it touches.
     *
     * <p>All six sides, including above and below, because a boiler stack is a reasonable thing to
     * build and refusing it would be an arbitrary rule the player has to learn.
     */
    private boolean drawSteam() {
        if (level == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (steam >= STEAM_CAPACITY) {
                break;
            }
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof BoilerBlockEntity boiler) {
                steam += boiler.drawSteam(STEAM_CAPACITY - steam);
            }
        }
        return steam >= STEAM_PER_TICK;
    }

    @Override
    public void onLoad() {
        super.onLoad();
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
        output.putInt("Steam", steam);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        steam = input.getIntOr("Steam", 0);
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
