package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** A machine's electricity buffer. */
public class MachinePower extends SimpleEnergyHandler {

    private final Runnable onChanged;

    public MachinePower(int capacity, Runnable onChanged) {
        super(capacity, capacity, capacity);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
