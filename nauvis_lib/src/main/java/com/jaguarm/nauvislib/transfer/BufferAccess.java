package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A battery's buffer as the grid may use it: fill it and empty it, by the grid's rule. */
public record BufferAccess(EnergyHandler backing) implements EnergyHandler, EnergyBuffer {

    @Override
    public long getAmountAsLong() {
        return backing.getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        return backing.getCapacityAsLong();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return backing.insert(amount, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return backing.extract(amount, transaction);
    }
}
