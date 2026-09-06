package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.transfer.GeneratorAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A boiler's steam as a pipe may use it: take, never give.
 *
 * <p>A boiler makes steam; it is not somewhere to put it. Letting a pipe push steam back in would
 * make the boiler a tank for the rest of the system to dump into, and would let two boilers shuffle
 * the same steam between each other for ever. The same argument, and the same shape, as
 * {@code GeneratorAccess} for electricity.
 *
 * <p>The wake-up is the other half. A boiler whose tank is full has stopped burning, and the only
 * thing that can give it work again is somebody drawing - which happens right here.
 */
public record SteamAccess(ResourceHandler<FluidResource> backing, Runnable onDrawn)
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

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return backing.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return backing.isValid(index, resource);
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
