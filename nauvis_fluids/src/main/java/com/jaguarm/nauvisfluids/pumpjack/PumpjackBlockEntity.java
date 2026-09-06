package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.OilProgress;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A pumpjack: stands over an oil well, spends electricity, and fills its tank with crude oil.
 *
 * <p>Factorio's numbers, and only Factorio's numbers:
 *
 * <ul>
 *   <li><b>one cycle a second</b> - {@code mining_time 1} over {@code mining_speed 1};
 *   <li><b>ten units times the well's yield</b> per cycle, so ten a second from a 100% well and
 *       two a second from one at its 20% floor;
 *   <li>the well loses {@code infinite_depletion_amount = 10} per cycle, which is what makes the
 *       yield fall - one percent every three hundred cycles;
 *   <li><b>90 kW</b>, which at this pack's ratio - a 900 kW steam engine is 120 FE a tick - is
 *       {@value #ENERGY_PER_TICK} FE a tick while pumping;
 *   <li>and a tank of {@value #TANK_CAPACITY}, Factorio's output fluid box - which is also the
 *       most one cycle can produce. Factorio caps a pumpjack's cycle at its fluid box volume, so
 *       a well of any richness fills the tank in a second and no faster.
 * </ul>
 *
 * <p>The yield is a fraction, and the output is integer units, so the fraction is carried between
 * cycles rather than rounded away: a 57.3% well produces 5.73 a cycle exactly, over time.
 *
 * <h2>Sleeping</h2>
 *
 * <p>No ticker. The machine schedules its own tick while it has a well, power and room, and stops
 * scheduling the moment it lacks any of the three. Each of those comes back from outside:
 * electricity arriving reports through {@link MachinePower}, room appearing reports through
 * {@link OutputAccess} when a pipe draws, and the well is a neighbour, so
 * {@code neighborChanged} covers it. {@code pumpjack_sleeps} is the test that fails if any of
 * the three wakes stops working.
 *
 * <h2>Pushing</h2>
 *
 * <p>It does not. Like the boiler, it fills its own tank and offers it extract-only at the outlet;
 * the pipe run pulls, which is what lets both ends sleep and what makes a pumpjack with nothing
 * connected simply fill up and stop.
 */
public class PumpjackBlockEntity extends BlockEntity {

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

    /**
     * A cycle's output in units is {@code UNITS_PER_CYCLE_AT_NORMAL * amount / NORMAL}, which is
     * {@code amount / 30000}. The remainder is carried in these units, so nothing is lost.
     */
    public static final long UNIT_DIVISOR = CrudeOilBlockEntity.NORMAL / UNITS_PER_CYCLE_AT_NORMAL;

    /** Unrestricted, because the machine spends from it. What the grid sees is {@link #gridView}. */
    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::onPowerChanged);

    /** Insert only: a machine is not a battery, and a grid must not be able to drain one. */
    private final EnergyHandler gridView = new PowerAccess(energy);

    private final CrudeOilTank tank = new CrudeOilTank(TANK_CAPACITY, this::onTankChanged);

    /** What a pipe sees at the outlet: extraction only, and a wake-up on the way out. */
    private final ResourceHandler<FluidResource> output = new OutputAccess(tank, this::wake);

    /** Ticks into the current cycle. At {@link #CYCLE_TICKS} the cycle is done and waiting to bank. */
    private int progress;

    /** The fraction of a unit carried over from the last cycle, in {@link #UNIT_DIVISOR}ths. */
    private long owed;

    private PumpjackStatus status = PumpjackStatus.NO_WELL;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPJACK.get(), pos, state);
    }

    /** What a power pole fills. Registered as {@code Capabilities.Energy.BLOCK}. */
    public EnergyHandler gridView() {
        return gridView;
    }

    /** What pipes draw from. Registered as {@code Capabilities.Fluid.BLOCK} at the outlet. */
    public ResourceHandler<FluidResource> output() {
        return output;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** How much crude oil is in the tank. Read by Jade and by tests. */
    public int stored() {
        return tank.getAmountAsInt(0);
    }

    public PumpjackStatus status() {
        return status;
    }

    /** The well under the middle of the machine, or null if there is not one. */
    public @Nullable CrudeOilBlockEntity well() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof CrudeOilBlockEntity well
                ? well
                : null;
    }

    /** Called by {@link PumpjackBlock}, and only ever on a tick this machine asked for. */
    public void serverTick(ServerLevel level) {
        CrudeOilBlockEntity well = well();
        if (well == null) {
            // Nothing to pump. A well cannot normally go away, but /setblock can take it, and a
            // machine placed by a structure or a test may be standing on nothing. neighborChanged
            // is what wakes this.
            settle(PumpjackStatus.NO_WELL);
            return;
        }

        if (progress < CYCLE_TICKS) {
            if (energy.getAmountAsInt() < ENERGY_PER_TICK) {
                // Out of power, holding the cycle where it stands. Nothing here can wake it - the
                // grid can, and MachinePower is what tells us it has.
                settle(PumpjackStatus.NO_POWER);
                return;
            }
            energy.set(energy.getAmountAsInt() - ENERGY_PER_TICK);
            progress++;
        }

        if (progress >= CYCLE_TICKS) {
            if (!bank(level, well)) {
                // Finished a cycle and the tank will not take it. Hold the cycle, keep the well as
                // it is, and sleep until a pipe draws - OutputAccess wakes us then.
                settle(PumpjackStatus.OUTPUT_FULL);
                return;
            }
            progress = 0;
        }

        status = PumpjackStatus.PUMPING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Puts one cycle's oil in the tank and takes one cycle off the well, or does neither.
     *
     * <p>The two happen together or not at all: a tank that cannot take the oil leaves the well
     * exactly as rich as it was, so a blocked pumpjack wastes nothing, which is Factorio's
     * behaviour too.
     */
    private boolean bank(ServerLevel level, CrudeOilBlockEntity well) {
        long due = owed + well.amount();
        // Factorio caps a cycle at the fluid box's volume. Above the cap the excess is simply not
        // produced - there is nothing to carry - and below it the fraction is.
        boolean capped = due / UNIT_DIVISOR >= TANK_CAPACITY;
        int units = capped ? TANK_CAPACITY : (int) (due / UNIT_DIVISOR);
        if (units > 0) {
            try (Transaction transaction = Transaction.openRoot()) {
                if (tank.insert(FluidResource.of(ModFluids.CRUDE_OIL.get()), units, transaction) != units) {
                    return false;
                }
                transaction.commit();
            }
        }
        owed = capped ? 0 : due % UNIT_DIVISOR;
        well.deplete();
        // One cycle, whatever the yield: Factorio's mine-entity counts mining operations. This is
        // what finishes oil processing, through whoever is listening.
        OilProgress.report(level, well.getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(ModBlocks.CRUDE_OIL.get()), 1);
        return true;
    }

    /** Stops without rescheduling, remembering why for the readout. */
    private void settle(PumpjackStatus why) {
        status = why;
        setChanged();
    }

    /**
     * A chunk that has just loaded has a pumpjack that has never been woken.
     *
     * <p>One scheduled tick per machine per chunk load, paid once. Without it a pumpjack that slept
     * with power and room before a restart would never start again on its own.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /**
     * Schedules the next tick unless one is already coming.
     *
     * <p>The guard is what makes this safe to call from anywhere: a grid filling the buffer and a
     * pipe drawing from the tank may both report in one tick, and without it each would queue its
     * own visit.
     */
    void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    /** Electricity arrived, or was spent. The wake is the half that matters. */
    private void onPowerChanged() {
        setChanged();
        wake();
    }

    /** Room in the tank is the one thing that gives a full pumpjack work again. */
    private void onTankChanged() {
        setChanged();
        wake();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
        tank.serialize(output.child("Tank"));
        output.putInt("Progress", progress);
        output.putLong("Owed", owed);
        output.putInt("Status", status.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        input.child("Tank").ifPresent(tank::deserialize);
        progress = input.getIntOr("Progress", 0);
        owed = input.getLongOr("Owed", 0);
        status = PumpjackStatus.byOrdinal(input.getIntOr("Status", PumpjackStatus.NO_WELL.ordinal()));
    }
}
