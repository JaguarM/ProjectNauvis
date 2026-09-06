package com.jaguarm.nauvisfluids.fluid;

import java.util.function.Supplier;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A tank that holds one named fluid and nothing else.
 *
 * <p>A machine's output fluid box. Refusing everything but its own fluid is what keeps a pipe run
 * carrying steam from being emptied into a pumpjack: the run asks each endpoint whether it would
 * take what the run holds, and a tank of crude oil only ever says yes to oil, a tank of water only
 * ever to water.
 */
public class SingleFluidTank extends FluidStacksResourceHandler {

    private final Supplier<? extends Fluid> fluid;
    private final Runnable onChanged;

    public SingleFluidTank(int capacity, Supplier<? extends Fluid> fluid, Runnable onChanged) {
        super(1, capacity);
        this.fluid = fluid;
        this.onChanged = onChanged;
    }

    /** The one thing this tank takes, for whoever fills it. */
    public FluidResource resource() {
        return FluidResource.of(fluid.get());
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.getFluid() == fluid.get();
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }
}
