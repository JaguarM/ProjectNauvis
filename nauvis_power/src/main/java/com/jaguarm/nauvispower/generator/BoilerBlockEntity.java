package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.transfer.MachineAccess;
import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
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
 * takes some - which happens through {@link SteamAccess}, the extract-only view a pipe or an
 * engine sees.
 */
public class BoilerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /** Two a tick against an engine's one: Factorio's one boiler to two engines. */
    public static final int STEAM_PER_TICK = 2;

    /** A few seconds of buffer. Big enough to ride out a gap, small enough to be worth refilling. */
    public static final int STEAM_CAPACITY = 200;

    private final BoilerFuel fuel = new BoilerFuel(SLOT_COUNT, this::onFuelChanged,
            () -> level == null ? null : level.fuelValues());
    private final ResourceHandler<ItemResource> fuelAccess = new MachineAccess(fuel, SLOT_COUNT);

    /** What an open screen reads. Ints only, which is all a boiler has to say. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case BoilerMenu.DATA_BURN_TIME -> burnTime;
                case BoilerMenu.DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case BoilerMenu.DATA_STEAM -> steam();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return BoilerMenu.DATA_COUNT;
        }
    };

    /**
     * Real steam, in a real tank, rather than an int.
     *
     * <p>It costs nothing over a counter and buys the thing that matters: a pipe from
     * {@code nauvis_fluids} can take from it through {@code Capabilities.Fluid.BLOCK} without
     * either mod compiling against the other. See {@link SteamTank}.
     */
    private final SteamTank steam = new SteamTank(STEAM_CAPACITY, this::onSteamChanged);

    /** What a pipe sees: extraction only, and a wake-up on the way out. */
    private final ResourceHandler<FluidResource> steamAccess = new SteamAccess(steam, this::wake);

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

    /** How much steam is banked. Read by the screen, by Jade, and by tests. */
    public int steam() {
        return steam.getAmountAsInt(0);
    }

    /** What pipes and engines draw from. Registered as {@code Capabilities.Fluid.BLOCK}. */
    public ResourceHandler<FluidResource> steamAccess() {
        return steamAccess;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    /** Called by {@link BoilerBlock}, and only ever on a tick this boiler asked for. */
    public void serverTick(ServerLevel level) {
        if (steam() >= STEAM_CAPACITY) {
            // Nothing to do until an engine draws. Burning fuel to make steam that will not fit
            // is how a burner ends up eating a chest of coal while the factory sits idle.
            return;
        }

        if (burnTime <= 0 && !refuel(level)) {
            return;
        }

        burnTime--;
        try (Transaction transaction = Transaction.openRoot()) {
            steam.insert(SteamTank.steamResource(), STEAM_PER_TICK, transaction);
            transaction.commit();
        }
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

    /** Room in the tank is the one thing that gives a stopped boiler work again. */
    private void onSteamChanged() {
        setChanged();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nauvis_power.boiler");
    }

    /**
     * The fuel slot as the player sees it: the real handler, not the insert-only
     * {@link #fuelAccess} an inserter gets. Somebody standing in front of the machine may take
     * their coal back out; a hopper underneath may not.
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BoilerMenu(containerId, playerInventory, fuel, menuData,
                ContainerLevelAccess.create(level, worldPosition));
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
        steam.serialize(output.child("Steam"));
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fuel").ifPresent(fuel::deserialize);
        input.child("Steam").ifPresent(steam::deserialize);
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
