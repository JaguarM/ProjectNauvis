package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.fluid.SteamFluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The fluids this mod owns, which so far is steam.
 *
 * <p>A fluid is two registrations: NeoForge's {@link FluidType}, which is everything about how it
 * behaves as a substance, and Minecraft's {@link Fluid}, which is the thing a
 * {@code FluidResource} names. Ours is never in the world, so the type is left almost entirely
 * default - the properties that matter are all about swimming in it.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, NauvisFluids.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, NauvisFluids.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = FLUID_TYPES.register(
            "steam",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.nauvis_fluids.steam")
                    .canDrown(false)
                    .canSwim(false)
                    .canPushEntity(false)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .supportsBoating(false)));

    /**
     * The id {@code data/mapping.json} has always named, so it is identity and not ours to change.
     */
    public static final DeferredHolder<Fluid, SteamFluid> STEAM =
            FLUIDS.register("steam", SteamFluid::new);

    private ModFluids() {}
}
