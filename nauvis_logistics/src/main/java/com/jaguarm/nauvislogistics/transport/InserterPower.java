package com.jaguarm.nauvislogistics.transport;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * An electric inserter's buffer.
 *
 * <p>The callback is the whole reason this is not a bare {@link SimpleEnergyHandler}. An inserter
 * that ran out of electricity has stopped scheduling ticks, and the only thing that can give it
 * work again is the grid delivering - which happens here, from a thread of control the inserter
 * is not part of. It is the same shape as the fuel slot's change callback and it exists for the
 * same reason: everything that can restart a sleeping block has to say so.
 *
 * <p>Unrestricted, because the inserter spends from it. What the grid sees is {@link PowerAccess}.
 */
public class InserterPower extends SimpleEnergyHandler {

    private final Runnable onChanged;

    public InserterPower(int capacity, Runnable onChanged) {
        super(capacity, capacity, capacity);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
