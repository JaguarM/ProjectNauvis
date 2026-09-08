package com.jaguarm.nauvispower.storage;

import com.jaguarm.nauvislib.transfer.BufferAccess;
import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** An accumulator: a battery for the grid, filled by surplus and emptied by shortfall. */
public class AccumulatorBlockEntity extends BlockEntity {

    /** 5 MJ at the pack's ratio of 120 FE/t to 900 kW. */
    public static final int CAPACITY = 13_333;

    /** 300 kW at the same ratio, in and out. */
    public static final int RATE = 40;

    /** Unrestricted between the machine and its own buffer; the rate is on each call. */
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, RATE, RATE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            moved(getAmountAsInt() - previousAmount);
        }
    };

    /** What the grid sees: both ways, and marked as a battery so the network knows when. */
    private final EnergyHandler gridView = new BufferAccess(energy);

    /** The last change, signed, and the game tick it happened on. */
    private int lastDelta;
    private long lastMoved = Long.MIN_VALUE;

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCUMULATOR.get(), pos, state);
    }

    public EnergyHandler gridView() {
        return gridView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /**
     * FE moved on the last tick: positive charging, negative discharging, zero when nothing has
     * moved for a tick or more.
     */
    public int flow() {
        if (level == null || level.getGameTime() - lastMoved > 1) {
            return 0;
        }
        return lastDelta;
    }

    /** Writes the charge directly. For {@code /research}-style tooling and the gametests. */
    public void setStored(int amount) {
        energy.set(Math.clamp(amount, 0, CAPACITY));
    }

    private void moved(int delta) {
        if (level != null) {
            lastDelta = delta;
            lastMoved = level.getGameTime();
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
    }
}
