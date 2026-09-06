package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A generator's buffer as the grid may use it: draw from it, never fill it.
 *
 * <p>The mirror image of {@link PowerAccess}. A steam engine or a solar panel makes electricity
 * and must never be handed any, or the network would have somewhere to park a surplus that is
 * not a battery.
 *
 * <p>{@code onDrawn} is the wake-up. A generator whose buffer is full has nothing to tick for
 * and stops scheduling itself; the one thing that gives it work again is somebody taking charge
 * out, which happens here, on a thread of control the generator is not part of.
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
