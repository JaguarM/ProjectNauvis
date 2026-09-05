package com.jaguarm.nauvisfluids.pumpjack;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A pumpjack's electricity buffer.
 *
 * <p>The callback is the whole reason this is not a bare {@link SimpleEnergyHandler}. A machine
 * that ran dry has stopped scheduling ticks, and the one thing that can give it work again is
 * energy arriving - which happens here, from whatever is filling it, on a thread of control the
 * machine is not part of. Without this a pumpjack that stopped for want of power would sleep
 * through the grid coming back.
 *
 * <p>The same three lines as {@code MachinePower} in {@code nauvis_machines}, duplicated rather
 * than shared: no subsystem mod depends on another, and an energy buffer is not a crafting
 * concern, so it cannot live in Facrafting either.
 */
public class PumpjackPower extends SimpleEnergyHandler {

    private final Runnable onChanged;

    public PumpjackPower(int capacity, Runnable onChanged) {
        super(capacity, capacity, capacity);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
