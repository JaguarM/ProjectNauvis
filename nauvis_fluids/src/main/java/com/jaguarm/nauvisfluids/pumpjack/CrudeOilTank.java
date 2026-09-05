package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A tank that holds crude oil and nothing else.
 *
 * <p>The pumpjack's output fluid box. Refusing everything but crude oil is what keeps a pipe run
 * carrying steam from being emptied into a pumpjack - the run asks each endpoint whether it would
 * take what the run holds, and this one only ever says yes to oil.
 */
public class CrudeOilTank extends FluidStacksResourceHandler {

    private final Runnable onChanged;

    public CrudeOilTank(int capacity, Runnable onChanged) {
        super(1, capacity);
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.getFluid() == ModFluids.CRUDE_OIL.get();
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }
}
