package com.jaguarm.nauvismachines.machine;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's buffer as the grid may use it: fill it, never empty it.
 *
 * <p>The mirror image of {@code GeneratorAccess} in {@code nauvis_power}, and duplicated rather
 * than shared for the reason non-negotiable #3 gives: no subsystem mod depends on another, and a
 * capability wrapper is not a crafting concern, so it cannot live in Facrafting either.
 *
 * <p>The restriction is not politeness. An electric network collects supply by asking every
 * endpoint to give, and a machine that gave energy back would be a place for the grid to store
 * surplus - so two half-charged assemblers would spend the day passing the same joule between
 * each other while the factory stood still.
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
