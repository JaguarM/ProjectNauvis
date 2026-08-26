package com.jaguarm.nauvispower.generator;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A boiler: burns solid fuel and makes steam.
 *
 * <p>Steam is an int, not a fluid. PLAN.md defers fluids to milestone 4 and takes barrels rather
 * than pipes even then, so a boiler that needed water would need a whole subsystem this pack has
 * decided not to build yet. What survives is the shape that matters - fuel goes in one end,
 * steam comes out, and a steam engine turns steam into electricity - and the ratio: this makes
 * {@value #STEAM_PER_TICK} steam a tick and an engine burns {@value SteamEngineBlockEntity#STEAM_PER_TICK},
 * so one boiler feeds two engines exactly as it does in Factorio.
 *
 * <p>What it does <em>not</em> do is push. Engines pull, which is what lets both ends sleep: the
 * boiler only runs when its own buffer has room, and its buffer only gains room when an engine
 * takes some. See {@link #drawSteam}.
 */
public class BoilerBlockEntity extends BlockEntity {

    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /** Two a tick against an engine's one: Factorio's one boiler to two engines. */
    public static final int STEAM_PER_TICK = 2;

    /** A few seconds of buffer. Big enough to ride out a gap, small enough to be worth refilling. */
    public static final int STEAM_CAPACITY = 200;

    private final BoilerFuel fuel = new BoilerFuel(SLOT_COUNT, this::onFuelChanged);
    private final ResourceHandler<ItemResource> fuelAccess = new FuelAccess(fuel);

    private int steam;
    private int burnTime;
    private int burnTimeTotal;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state);
    }

    public ResourceHandler<ItemResource> fuelAccess() {
        return fuelAccess;
    }

    public BoilerFuel fuel() {
        return fuel;
    }

    public int steam() {
        return steam;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    /**
     * Takes steam out, and wakes the boiler because there is now room to make more.
     *
     * <p>This is the whole of the engine-to-boiler contract, and the reason the boiler can sleep
     * with a full buffer and a hopper full of coal: nothing is burnt until somebody draws.
     *
     * @return how much was actually taken, which may be less than asked for or nothing at all.
     */
    public int drawSteam(int wanted) {
        int taken = Math.min(wanted, steam);
        if (taken <= 0) {
            return 0;
        }
        steam -= taken;
        setChanged();
        wake();
        return taken;
    }

    /** Called by {@link BoilerBlock}, and only ever on a tick this boiler asked for. */
    public void serverTick(ServerLevel level) {
        if (steam >= STEAM_CAPACITY) {
            // Nothing to do until an engine draws. Burning fuel to make steam that will not fit
            // is how a burner ends up eating a chest of coal while the factory sits idle.
            return;
        }

        if (burnTime <= 0 && !refuel(level)) {
            return;
        }

        burnTime--;
        steam = Math.min(STEAM_CAPACITY, steam + STEAM_PER_TICK);
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Burns one item of fuel. @return whether there is now fuel to spend. */
    private boolean refuel(ServerLevel level) {
        ItemResource candidate = fuel.getResource(FUEL_SLOT);
        if (candidate.isEmpty()) {
            return false;
        }

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
     * A chunk that has just loaded has a boiler that has never been woken.
     *
     * <p>One scheduled tick per boiler per chunk load, paid once. Without it a boiler that slept
     * with a part-full buffer before a restart would never start again on its own.
     */
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

    private void onFuelChanged() {
        setChanged();
        wake();
    }

    /** See the assembler: this is the hook, not {@code Block#affectNeighborsAfterRemoval}. */
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
        output.putInt("Steam", steam);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fuel").ifPresent(fuel::deserialize);
        steam = input.getIntOr("Steam", 0);
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
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
