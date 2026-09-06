package com.jaguarm.nauvislib.transfer;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's tank as a pipe may use it: take, never give.
 *
 * <p>A boiler makes steam, a pumpjack crude oil, an offshore pump water; none of them is
 * somewhere to put any of it. Letting a pipe push fluid back in would make the machine a tank
 * for the rest of the system to dump into, and would let two of them shuffle the same fluid
 * between each other for ever. The same argument, and the same shape, as {@link GeneratorAccess}
 * for electricity.
 *
 * <p>The wake-up is the other half. A machine whose tank is full has stopped, and the only thing
 * that can give it work again is somebody drawing - which happens right here. It is also what a
 * pipe run reads as "this is a source": a handler that refuses insertion gives and never takes.
 */
public record FluidOutputAccess(ResourceHandler<FluidResource> backing, Runnable onDrawn)
        implements ResourceHandler<FluidResource> {

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return backing.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return backing.getAmountAsLong(index);
    }

    /**
     * Nothing fits, because nothing may be put in. Said here as well as in {@link #insert},
     * because a pipe run that finds a tank full asks these two whether it is looking at a sink
     * that happens to be full or at a source, and a source that reported its tank's capacity
     * would be drained into the run the moment the run had room.
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return 0;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return false;
    }

    /** Always nothing. The whole point of this wrapper. */
    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        int taken = backing.extract(index, resource, amount, transaction);
        if (taken > 0) {
            onDrawn.run();
        }
        return taken;
    }
}
