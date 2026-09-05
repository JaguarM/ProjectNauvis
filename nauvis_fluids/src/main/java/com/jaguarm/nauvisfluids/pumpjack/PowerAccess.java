package com.jaguarm.nauvisfluids.pumpjack;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's buffer as the grid may use it: fill it, never empty it.
 *
 * <p>The same wrapper {@code nauvis_machines} gives its assembler, duplicated for the reason
 * non-negotiable #3 gives. The restriction is not politeness: an electric network collects supply
 * by asking every endpoint to give, and a machine that gave energy back would be a place for the
 * grid to store surplus.
 */
public record PowerAccess(EnergyHandler backing) implements EnergyHandler {

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

    /** Always nothing. The whole point of this wrapper. */
    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }
}
