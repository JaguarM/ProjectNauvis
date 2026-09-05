package com.jaguarm.nauvisfluids.fluid;

import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Crude oil: what a pumpjack draws out of an oil well and what a refinery is fed.
 *
 * <p>Never in the world, like steam. The oil that <em>is</em> in the world is not this fluid at
 * all but {@code CrudeOilBlock}, a resource block the way Factorio's {@code crude-oil} is a
 * resource entity: something a pumpjack stands on rather than a puddle you could bucket up. That
 * split is what makes an oil field unmovable and finite-by-rule while the oil in the pipes stays
 * a real fluid.
 */
public class CrudeOilFluid extends ContainedFluid {

    @Override
    public FluidType getFluidType() {
        return ModFluids.CRUDE_OIL_TYPE.get();
    }
}
