package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvislib.transfer.FluidOutputAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.SingleFluidTank;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.OilProgress;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** A pumpjack: stands over an oil well, spends electricity, and fills its tank with crude oil. */
public class PumpjackBlockEntity extends BlockEntity implements MenuProvider {

    /** Ten a minute, Factorio's figure. */
    public static final double POLLUTION_PER_MINUTE = 10;

    /** 90 kW at the pack's ratio of 120 FE/t to a 900 kW engine. The ratio is what is kept. */
    public static final int ENERGY_PER_TICK = 12;

    /** Five seconds of work: enough to ride out a gap, too small to be somewhere the grid parks a surplus. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 100;

    /** One second: Factorio's {@code mining_time} over the pumpjack's {@code mining_speed}. */
    public static final int CYCLE_TICKS = 20;

    /** Factorio's output fluid box holds a thousand. */
    public static final int TANK_CAPACITY = 1000;

    /** Ten units a cycle from a 100% well. Identity. */
    public static final int UNITS_PER_CYCLE_AT_NORMAL = 10;

    /** Factorio's pumpjack takes two modules. */
    public static final int MODULE_SLOTS = 2;

    /** Factorio's modifier for mining productivity research, which reaches pumpjacks as well as drills. */
    public static final String MINING_PRODUCTIVITY = "mining-drill-productivity-bonus";

    /**
     * A cycle's output in units is {@code UNITS_PER_CYCLE_AT_NORMAL * amount / NORMAL}, which is
     * {@code amount / 30000}. The remainder is carried in these units, so nothing is lost.
     */
    public static final long UNIT_DIVISOR = CrudeOilBlockEntity.NORMAL / UNITS_PER_CYCLE_AT_NORMAL;

    /** Unrestricted, because the machine spends from it. What the grid sees is {@link #gridView}. */
    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::onPowerChanged);

    /** Insert only: a machine is not a battery, and a grid must not be able to drain one. */
    private final EnergyHandler gridView = new PowerAccess(energy);

    private final SingleFluidTank tank = new SingleFluidTank(TANK_CAPACITY, ModFluids.CRUDE_OIL, this::onTankChanged);

    /** What a pipe sees at the outlet: extraction only, and a wake-up on the way out. */
    private final ResourceHandler<FluidResource> output = new FluidOutputAccess(tank, this::wake);

    /** The two module slots, and the free cycle they and the research work towards. */
    private final ModuleSlots modules = new ModuleSlots(MODULE_SLOTS, this::onModulesChanged);
    private final Productivity productivity = new Productivity();

    /** Ticks into the current cycle. At {@link #cycleTicks} the cycle is done and waiting to bank. */
    private int progress;

    /** The cycle's length and draw under the modules read as it started. */
    private int cycleTicks = CYCLE_TICKS;
    private int draw = ENERGY_PER_TICK;

    /** Fractions of a unit carried between cycles. */
    private long owed;

    private PumpjackStatus status = PumpjackStatus.NO_WELL;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case PumpjackMenu.DATA_PROGRESS -> progress;
                case PumpjackMenu.DATA_CYCLE_TICKS -> cycleTicks;
                case PumpjackMenu.DATA_ENERGY -> energy.getAmountAsInt();
                case PumpjackMenu.DATA_ENERGY_CAPACITY -> ENERGY_CAPACITY;
                case PumpjackMenu.DATA_STORED -> stored();
                case PumpjackMenu.DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative.
        }

        @Override
        public int getCount() {
            return PumpjackMenu.DATA_COUNT;
        }
    };

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPJACK.get(), pos, state);
    }

    public EnergyHandler gridView() {
        return gridView;
    }

    public ResourceHandler<FluidResource> output() {
        return output;
    }

    public ModuleSlots modules() {
        return modules;
    }

    public Productivity productivity() {
        return productivity;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    public int stored() {
        return tank.getAmountAsInt(0);
    }

    public PumpjackStatus status() {
        return status;
    }

    /** The cycle's length under the modules read as it started, for the test and the screen. */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** The draw under the modules in it right now. */
    public int currentEnergyPerTick() {
        return modules.effect().scaleEnergy(ENERGY_PER_TICK);
    }

    public @Nullable CrudeOilBlockEntity well() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof CrudeOilBlockEntity well
                ? well
                : null;
    }

    public void serverTick(ServerLevel level) {
        CrudeOilBlockEntity well = well();
        if (well == null) {
            // Nothing to pump. A well cannot normally go away, but /setblock can take it, and a
            // machine placed by a structure or a test may be standing on nothing. neighborChanged
            // is what wakes this.
            settle(PumpjackStatus.NO_WELL);
            return;
        }

        // The modules are read as the cycle starts and hold for the cycle, which is Factorio's rule.
        if (progress == 0) {
            ModuleEffect effect = modules.effect();
            cycleTicks = Math.max(1, (int) Math.round(CYCLE_TICKS / effect.speedFactor()));
            draw = effect.scaleEnergy(ENERGY_PER_TICK);
        }

        if (progress < cycleTicks) {
            if (energy.getAmountAsInt() < draw) {
                // Out of power, holding the cycle where it stands. Nothing here can wake it - the
                // grid can, and MachinePower is what tells us it has.
                settle(PumpjackStatus.NO_POWER);
                return;
            }
            energy.set(energy.getAmountAsInt() - draw);
            progress++;
            Pollution.emitTick(level, worldPosition, POLLUTION_PER_MINUTE, modules.effect().energyFactor());
        }

        if (progress >= cycleTicks) {
            if (!bank(level, well)) {
                // Finished a cycle and the tank will not take it. Hold the cycle, keep the well as
                // it is, and sleep until a pipe draws - FluidOutputAccess wakes us then.
                settle(PumpjackStatus.OUTPUT_FULL);
                return;
            }
            progress = 0;
        }

        status = PumpjackStatus.PUMPING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    private boolean bank(ServerLevel level, CrudeOilBlockEntity well) {
        long due = owed + well.amount();
        // Factorio caps a cycle at the fluid box's volume. Above the cap the excess is simply not
        // produced - there is nothing to carry - and below it the fraction is.
        boolean capped = due / UNIT_DIVISOR >= TANK_CAPACITY;
        int units = capped ? TANK_CAPACITY : (int) (due / UNIT_DIVISOR);
        if (units > 0 && !insert(units)) {
            return false;
        }
        owed = capped ? 0 : due % UNIT_DIVISOR;
        well.deplete();
        // One cycle, whatever the yield: Factorio's mine-entity counts mining operations. This is
        // what finishes oil processing, through whoever is listening.
        OilProgress.report(level, well.getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(ModBlocks.CRUDE_OIL.get()), 1);

        // Productivity: every cycle earns a fraction of a free one, from the modules and from the
        // world's mining productivity research; a whole one is banked without touching the well,
        // and stays owed if the tank has no room for it.
        productivity.earn(modules.effect().productivityBonus() + Bonuses.of(level, MINING_PRODUCTIVITY));
        if (productivity.owed() && units > 0 && insert(units)) {
            productivity.pay();
        }
        return true;
    }

    private boolean insert(int units) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (tank.insert(tank.resource(), units, transaction) != units) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    private void settle(PumpjackStatus why) {
        status = why;
        setChanged();
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

    private void onPowerChanged() {
        setChanged();
        wake();
    }

    private void onTankChanged() {
        setChanged();
        wake();
    }

    private void onModulesChanged() {
        setChanged();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PumpjackMenu(containerId, playerInventory, modules, menuData, worldPosition);
    }

    /** The modules spill when the machine is broken. The oil in the tank does not; it is a fluid. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < modules.size(); slot++) {
            int amount = modules.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource resource = modules.getResource(slot);
            modules.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(amount));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
        tank.serialize(output.child("Tank"));
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        output.putInt("Progress", progress);
        output.putInt("CycleTicks", cycleTicks);
        output.putInt("Draw", draw);
        output.putLong("Owed", owed);
        output.putInt("Status", status.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        input.child("Tank").ifPresent(tank::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        progress = input.getIntOr("Progress", 0);
        cycleTicks = input.getIntOr("CycleTicks", CYCLE_TICKS);
        draw = input.getIntOr("Draw", ENERGY_PER_TICK);
        owed = input.getLongOr("Owed", 0);
        status = PumpjackStatus.byOrdinal(input.getIntOr("Status", PumpjackStatus.NO_WELL.ordinal()));
    }
}
