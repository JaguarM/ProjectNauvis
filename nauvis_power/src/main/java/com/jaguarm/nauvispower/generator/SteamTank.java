package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.transfer.SingleFluidTank;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A tank that holds steam and nothing else.
 *
 * <p>The fluid is looked up by id rather than imported. {@code nauvis_fluids} owns
 * {@code nauvis_fluids:steam} - {@code data/mapping.json} says so and always has - and
 * non-negotiable #3 forbids this mod from compiling against that one. A registry lookup by
 * {@link Identifier} is how the pack's own tests reach across mods, and it is what lets a boiler
 * and a pipe from two different jars agree on what is in them.
 *
 * <p>If {@code nauvis_fluids} is absent the lookup finds nothing and this tank rejects everything,
 * which is the correct behaviour for a boiler that has nowhere to put steam: {@code nauvis_power}
 * still loads, and its recipes already fall back the same way.
 */
public class SteamTank extends SingleFluidTank {

    public static final Identifier STEAM_ID =
            Identifier.fromNamespaceAndPath("nauvis_fluids", "steam");

    public SteamTank(int capacity, Runnable onChanged) {
        super(capacity, SteamTank::steam, onChanged);
    }

    /** The steam fluid, or null when {@code nauvis_fluids} is not installed. */
    public static Fluid steam() {
        return BuiltInRegistries.FLUID.getValue(STEAM_ID);
    }

    public static FluidResource steamResource() {
        return FluidResource.of(steam());
    }
}
