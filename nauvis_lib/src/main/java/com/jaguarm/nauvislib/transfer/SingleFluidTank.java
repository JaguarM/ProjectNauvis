package com.jaguarm.nauvislib.transfer;

import java.util.function.Supplier;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/** A tank that holds one named fluid and nothing else. */
public class SingleFluidTank extends FluidStacksResourceHandler {

    private final Supplier<? extends Fluid> fluid;
    private final Runnable onChanged;

    public SingleFluidTank(int capacity, Supplier<? extends Fluid> fluid, Runnable onChanged) {
        super(1, capacity);
        this.fluid = fluid;
        this.onChanged = onChanged;
    }

    /** The one thing this tank takes, for whoever fills or empties it. */
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
