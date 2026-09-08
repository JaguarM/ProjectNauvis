package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.transfer.SingleFluidTank;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A tank that holds steam and nothing else. */
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
