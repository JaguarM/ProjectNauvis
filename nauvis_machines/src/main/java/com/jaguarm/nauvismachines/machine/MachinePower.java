package com.jaguarm.nauvismachines.machine;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A machine's electricity buffer.
 *
 * <p>The callback is the whole reason this is not a bare {@link SimpleEnergyHandler}. A machine
 * that ran dry has stopped scheduling ticks, and the one thing that can give it work again is
 * energy arriving - which happens here, from whatever is filling it, on a thread of control the
 * machine is not part of. Without this a machine that stopped for want of power would sleep
 * through the grid coming back, which is the same class of bug the boiler's fuel slot has.
 *
 * <p>Unrestricted on purpose: the machine spends from it, so it must be able to. What the outside
 * world gets is {@link PowerAccess}.
 */
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
