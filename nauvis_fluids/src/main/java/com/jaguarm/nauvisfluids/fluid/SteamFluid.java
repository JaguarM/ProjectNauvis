package com.jaguarm.nauvisfluids.fluid;

import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Steam: the one substance a boiler makes and a steam engine drinks.
 *
 * <p>PLAN.md put fluids in milestone 4 and took barrels rather than pipes even then. Steam was the
 * exception because it is not really a fluid subsystem, and {@code data/mapping.json} has always
 * called it {@code nauvis_fluids:steam}. Everything about never being in the world is
 * {@link ContainedFluid}'s.
 */
public class SteamFluid extends ContainedFluid {

    @Override
    public FluidType getFluidType() {
        return ModFluids.STEAM_TYPE.get();
    }
}
