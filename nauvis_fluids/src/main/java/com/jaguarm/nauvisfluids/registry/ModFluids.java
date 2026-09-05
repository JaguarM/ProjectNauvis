package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.fluid.CrudeOilFluid;
import com.jaguarm.nauvisfluids.fluid.SteamFluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The fluids this mod owns: steam, and crude oil.
 *
 * <p>A fluid is two registrations: NeoForge's {@link FluidType}, which is everything about how it
 * behaves as a substance, and Minecraft's {@link Fluid}, which is the thing a
 * {@code FluidResource} names. Ours are never in the world, so the types are left almost entirely
 * default - the properties that matter are all about swimming in it, and nobody ever will.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, NauvisFluids.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, NauvisFluids.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = FLUID_TYPES.register(
            "steam",
            () -> new FluidType(contained().descriptionId("fluid.nauvis_fluids.steam")));

    /**
     * Crude oil. Factorio gives it no temperature worth modelling and a viscosity that only
     * matters to its 2.0 flow model, which this pack's {@code FluidNetwork} does not have.
     */
    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_TYPE = FLUID_TYPES.register(
            "crude_oil",
            () -> new FluidType(contained().descriptionId("fluid.nauvis_fluids.crude_oil")));

    /**
     * The id {@code data/mapping.json} has always named, so it is identity and not ours to change.
     */
    public static final DeferredHolder<Fluid, SteamFluid> STEAM =
            FLUIDS.register("steam", SteamFluid::new);

    /**
     * {@code nauvis_fluids:crude_oil} - the same id as the resource block, exactly as Factorio
     * names both its {@code crude-oil} fluid and its {@code crude-oil} resource entity. Different
     * registries, so no clash, and one name for the player to learn.
     */
    public static final DeferredHolder<Fluid, CrudeOilFluid> CRUDE_OIL =
            FLUIDS.register("crude_oil", CrudeOilFluid::new);

    /** What every fluid that lives only in machines has in common: nothing can happen to you in it. */
    private static FluidType.Properties contained() {
        return FluidType.Properties.create()
                .canDrown(false)
                .canSwim(false)
                .canPushEntity(false)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false);
    }

    private ModFluids() {}
}
