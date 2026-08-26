package com.jaguarm.nauvispower.generator;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A generator's energy buffer as the outside world may use it: take, never give.
 *
 * <p>A steam engine is not a battery. Letting a network push energy back into one would make it a
 * place for the grid to dump surplus, which is not what it is, and would let two engines shuffle
 * the same joule between each other forever.
 *
 * <p>The wake-up is the other half. A generator whose buffer is full has stopped, and the only
 * thing that can give it work again is somebody drawing - which happens right here, so this is
 * where it says so. Waking on a draw that later rolls back is harmless: the machine spends one
 * tick discovering it is still full and goes back to sleep.
 */
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
