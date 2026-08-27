package com.jaguarm.nauvislogistics.transport;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * An inserter's buffer as the grid may use it: fill it, never empty it.
 *
 * <p>The twin of the same class in {@code nauvis_machines}, and duplicated rather than shared for
 * the reason non-negotiable #3 gives: no subsystem mod depends on another, and a capability
 * wrapper is not a crafting concern, so Facrafting is not its home either.
 *
 * <p>The restriction is the same one {@link FuelAccess} makes about coal, for the same reason. A
 * network collects supply by asking every endpoint to give, and an inserter that gave energy back
 * would be somewhere for the grid to store surplus - so two half-charged inserters would pass the
 * same joule between each other while the belt stood still.
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
