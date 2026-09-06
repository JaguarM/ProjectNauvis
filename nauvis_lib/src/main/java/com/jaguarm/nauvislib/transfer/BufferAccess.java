package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A battery's buffer as the grid may use it: fill it and empty it, by the grid's rule.
 *
 * <p>The third of the three views. {@link PowerAccess} is a machine's, insert only;
 * {@link GeneratorAccess} is a generator's, extract only; this one allows both and carries the
 * {@link EnergyBuffer} marker that tells a network <em>when</em> - surplus in, shortfall out, and
 * never between two buffers. The backing handler's per-call limits are the charge and discharge
 * rates, because a network asks a buffer once a tick each way at most.
 *
 * <p>No wake-up callback: a battery has no work of its own to schedule. What it has to do when its
 * charge moves - remember it, and say so on the readout - the backing handler does in
 * {@code onEnergyChanged}.
 */
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
