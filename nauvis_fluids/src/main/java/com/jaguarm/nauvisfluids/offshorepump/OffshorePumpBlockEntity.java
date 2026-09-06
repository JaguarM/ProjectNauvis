package com.jaguarm.nauvisfluids.offshorepump;

import com.jaguarm.nauvisfluids.fluid.OutputAccess;
import com.jaguarm.nauvisfluids.fluid.SingleFluidTank;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An offshore pump: stands at the water's edge and fills its tank with water, for nothing.
 *
 * <p>Factorio's numbers, at the pack's ratio:
 *
 * <ul>
 *   <li><b>no power and no fuel.</b> Factorio's offshore pump has had no energy source since
 *       0.17; water is free, and the cost of it is having to build out to where it is;
 *   <li><b>{@value #WATER_PER_TICK} a tick.</b> Factorio's pump gives 1200 a second, and its
 *       boiler takes 60 a second, so one pump feeds twenty boilers. A boiler here makes two steam
 *       a tick - {@code nauvis_power} keeps Factorio's ratios rather than its units - so twenty
 *       boilers' worth of water is forty a tick. The ratio is what is kept;
 *   <li><b>a tank of {@value #TANK_CAPACITY}</b>, Factorio's fluid box for the machine.
 * </ul>
 *
 * <p>What comes out is {@code minecraft:water}, not the natural water it stands in. Factorio's
 * water fluid is mapped to vanilla's in {@code data/mapping.json}, a pipe or a tank from any mod
 * knows what that is, and it is what a bucket of the lake becomes too. Natural water is the
 * thing in the world; water is the thing in the pipe.
 *
 * <h2>Sleeping</h2>
 *
 * <p>No ticker. The machine schedules its own tick while it has water and room, and stops
 * scheduling the moment it lacks either. Both come back from outside: room appears when a pipe
 * draws, which {@link OutputAccess} reports, and water at the intake is a neighbour of the
 * intake cell, so {@code neighborChanged} covers it. {@code offshore_pump_sleeps} is the test
 * that fails if either wake stops working.
 *
 * <h2>Pushing</h2>
 *
 * <p>It does not. Like the boiler and the pumpjack, it fills its own tank and offers it
 * extract-only at the outlet; the pipe run pulls, which is what lets both ends sleep and what
 * makes a pump with nothing connected simply fill up and stop.
 */
public class OffshorePumpBlockEntity extends BlockEntity {

    /** Factorio's 1200 a second, at the pack's ratio: twenty boilers at two a tick each. */
    public static final int WATER_PER_TICK = 40;

    /** Factorio's fluid box for the offshore pump: a base area of one, two high. */
    public static final int TANK_CAPACITY = 200;

    private final SingleFluidTank tank = new SingleFluidTank(TANK_CAPACITY, () -> Fluids.WATER, this::onTankChanged);

    /** What a pipe sees at the outlet: extraction only, and a wake-up on the way out. */
    private final ResourceHandler<FluidResource> output = new OutputAccess(tank, this::wake);

    private OffshorePumpStatus status = OffshorePumpStatus.NO_WATER;

    public OffshorePumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFSHORE_PUMP.get(), pos, state);
    }

    /** What pipes draw from. Registered as {@code Capabilities.Fluid.BLOCK} at the outlet. */
    public ResourceHandler<FluidResource> output() {
        return output;
    }

    /** How much water is in the tank. Read by Jade and by tests. */
    public int stored() {
        return tank.getAmountAsInt(0);
    }

    public OffshorePumpStatus status() {
        return status;
    }

    /** Called by {@link OffshorePumpBlock}, and only ever on a tick this machine asked for. */
    public void serverTick(ServerLevel level) {
        OffshorePumpBlock.Intake intake = OffshorePumpBlock.intake(level, worldPosition,
                getBlockState().getValue(OffshorePumpBlock.FACING));
        if (intake != OffshorePumpBlock.Intake.NATURAL) {
            // Nothing to draw. A lake cannot normally go away, but a bucket can take the one
            // block the intake reaches, and a pump placed by a structure or a test may stand
            // beside anything. neighborChanged is what wakes this.
            settle(intake == OffshorePumpBlock.Intake.OTHER
                    ? OffshorePumpStatus.WRONG_WATER
                    : OffshorePumpStatus.NO_WATER);
            return;
        }

        int room = TANK_CAPACITY - stored();
        if (room <= 0) {
            // Full, and nothing is drawing. Sleep until a pipe does - OutputAccess wakes us then.
            settle(OffshorePumpStatus.OUTPUT_FULL);
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            tank.insert(tank.resource(), Math.min(WATER_PER_TICK, room), transaction);
            transaction.commit();
        }
        status = OffshorePumpStatus.PUMPING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Stops without rescheduling, remembering why for the readout. */
    private void settle(OffshorePumpStatus why) {
        status = why;
        setChanged();
    }

    /**
     * A chunk that has just loaded has a pump that has never been woken.
     *
     * <p>One scheduled tick per machine per chunk load, paid once. Without it a pump that slept
     * with room before a restart would never start again on its own.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /**
     * Schedules the next tick unless one is already coming.
     *
     * <p>The guard is what makes this safe to call from anywhere: a pipe drawing from the tank and
     * a neighbour changing may both report in one tick, and without it each would queue its own
     * visit.
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

    /** Room in the tank is the one thing that gives a full pump work again. */
    private void onTankChanged() {
        setChanged();
        wake();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("Tank"));
        output.putInt("Status", status.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Tank").ifPresent(tank::deserialize);
        status = OffshorePumpStatus.byOrdinal(input.getIntOr("Status", OffshorePumpStatus.NO_WATER.ordinal()));
    }
}
