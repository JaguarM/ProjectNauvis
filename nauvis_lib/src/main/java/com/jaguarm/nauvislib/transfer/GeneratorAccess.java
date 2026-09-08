package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A generator's buffer as the grid may use it: draw from it, never fill it. */
public record GeneratorAccess(EnergyHandler backing, Runnable onDrawn) implements EnergyHandler {

    @Override
    public long getAmountAsLong() {
        return backing.getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        return backing.getCapacityAsLong();
    }

    /** Always nothing. The whole point of this wrapper. */
    @Override
    public int insert(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int taken = backing.extract(amount, transaction);
        if (taken > 0) {
            onDrawn.run();
        }
        return taken;
    }
}
