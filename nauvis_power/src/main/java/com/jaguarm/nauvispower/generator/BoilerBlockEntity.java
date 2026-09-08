package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.transfer.FluidOutputAccess;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvislib.transfer.SingleFluidTank;
import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** A boiler: burns solid fuel to turn water into steam. */
public class BoilerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /** Thirty a minute, Factorio's figure: a boiler is the dirtiest thing in the early game. */
    public static final double POLLUTION_PER_MINUTE = 30;

    /** Two a tick against an engine's one: Factorio's one boiler to two engines. */
    public static final int STEAM_PER_TICK = 2;

    /** One water for one steam. Factorio's boiler, and the reason a pump is twenty boilers. */
    public static final int WATER_PER_TICK = STEAM_PER_TICK;

    /** A few seconds of buffer. Big enough to ride out a gap, small enough to be worth refilling. */
    public static final int STEAM_CAPACITY = 200;

    /** Factorio's water box on a boiler holds two hundred. */
    public static final int WATER_CAPACITY = 200;

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
                case BoilerMenu.DATA_WATER -> water();
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

    /** What a pipe sees at the steam port: extraction only, and a wake-up on the way out. */
    private final ResourceHandler<FluidResource> steamAccess = new FluidOutputAccess(steam, this::wake);

    /**
     * The water waiting to be boiled. Vanilla's water and nothing else, so a run of steam or oil
     * touching the wrong end of a boiler is refused rather than swallowed.
     */
    private final SingleFluidTank water = new SingleFluidTank(WATER_CAPACITY, () -> Fluids.WATER, this::onWaterChanged);

    /** The blocks just outside the two water ports, which is where a neighbouring boiler would be. */
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> westEnd;
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> eastEnd;

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

    /** How much water is waiting. Read by the screen, by Jade, and by tests. */
    public int water() {
        return water.getAmountAsInt(0);
    }

    /** Whether there is water enough for a tick of boiling. What a stopped boiler is asked first. */
    public boolean hasWater() {
        return water() >= WATER_PER_TICK;
    }

    /** What pipes and engines draw from. Registered as {@code Capabilities.Fluid.BLOCK} at the steam port. */
    public ResourceHandler<FluidResource> steamAccess() {
        return steamAccess;
    }

    /**
     * What pipes fill and a neighbouring boiler draws from. Registered as
     * {@code Capabilities.Fluid.BLOCK} at both water ports.
     *
     * <p>The tank itself, both ways round: a pipe run pushes into it, which is what makes the run
     * treat a boiler as a sink and never as a source of water, and the boiler next along takes
     * from it, which is what makes a row of boilers one line of water.
     */
    public ResourceHandler<FluidResource> waterAccess() {
        return water;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    /** Called by {@link BoilerBlock}, and only ever on a tick this boiler asked for. */
    public void serverTick(ServerLevel level) {
        int room = STEAM_CAPACITY - steam();
        if (room <= 0) {
            // Nothing to do until an engine draws. Burning fuel to make steam that will not fit
            // is how a burner ends up eating a chest of coal while the factory sits idle.
            return;
        }

        if (water() < WATER_CAPACITY) {
            drawWater(level);
        }
        if (!hasWater()) {
            // Dry, and nothing changed, so no setChanged: it would reach this machine's own
            // cells, whose onNeighborChange wakes the anchor, and a dry boiler would tick for
            // ever asking itself. Water arriving wakes it through the tank, and the boiler next
            // along gaining some wakes it through onNeighborChange.
            return;
        }

        if (burnTime <= 0 && !refuel(level)) {
            return;
        }

        burnTime--;
        Pollution.emitTick(level, worldPosition, POLLUTION_PER_MINUTE, 1.0);
        int made = Math.min(STEAM_PER_TICK, Math.min(room, water()));
        try (Transaction transaction = Transaction.openRoot()) {
            water.extract(water.resource(), made, transaction);
            steam.insert(SteamTank.steamResource(), made, transaction);
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

    /** Levels this tank with whatever is just outside either water port. */
    private void drawWater(ServerLevel level) {
        if (westEnd == null) {
            buildCaches(level);
        }
        levelWith(westEnd);
        levelWith(eastEnd);
    }

    private void levelWith(
            @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache) {
        if (cache == null) {
            return;
        }
        ResourceHandler<FluidResource> source = cache.getCapability();
        if (source == null) {
            return;
        }

        FluidResource resource = water.resource();
        long theirs = 0;
        for (int index = 0; index < source.size(); index++) {
            if (source.getResource(index).equals(resource)) {
                theirs += source.getAmountAsLong(index);
            }
        }
        int take = (int) Math.min(WATER_CAPACITY - water(), (theirs - water()) / 2);
        if (take <= 0) {
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            int taken = source.extract(resource, take, transaction);
            if (taken > 0 && water.insert(resource, taken, transaction) == taken) {
                transaction.commit();
            }
        }
    }

    /**
     * The two blocks just outside the boiler's water ports.
     *
     * <p>Read off {@link BoilerShape} rather than stepping from the middle, so that the ends stay
     * the ends if the machine is ever reshaped.
     */
    private void buildCaches(ServerLevel level) {
        Direction facing = getBlockState().getValue(BoilerBlock.FACING);
        westEnd = endCache(level, facing, BoilerShape.WEST_END, Direction.WEST);
        eastEnd = endCache(level, facing, BoilerShape.EAST_END, Direction.EAST);
    }

    private BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> endCache(
            ServerLevel level, Direction facing, int end, Direction port) {
        BlockPos cell = BoilerShape.SHAPE.cellPos(worldPosition, end, facing);
        Direction out = MachineShape.toWorld(port, facing);
        return BlockCapabilityCache.create(
                Capabilities.Fluid.BLOCK, level, cell.relative(out), out.getOpposite());
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

    /** Room in the tank is one of the things that gives a stopped boiler work again. */
    private void onSteamChanged() {
        setChanged();
        wake();
    }

    /** Water arriving is the other. */
    private void onWaterChanged() {
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
        water.serialize(output.child("Water"));
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fuel").ifPresent(fuel::deserialize);
        input.child("Steam").ifPresent(steam::deserialize);
        input.child("Water").ifPresent(water::deserialize);
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
