package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.fluid.CrudeOilFluid;
import com.jaguarm.nauvisfluids.fluid.NaturalWaterFluid;
import com.jaguarm.nauvisfluids.fluid.SteamFluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The fluids this mod owns: steam, crude oil, and the water the world is made with.
 *
 * <p>A fluid is two registrations: NeoForge's {@link FluidType}, which is everything about how it
 * behaves as a substance, and Minecraft's {@link Fluid}, which is the thing a
 * {@code FluidResource} names. Steam and oil are never in the world, so their types are left almost
 * entirely default - the properties that matter are all about swimming in it, and nobody ever
 * will. Natural water is nothing but in the world, and its type is vanilla water's with one
 * property turned off.
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
     * Natural water: NeoForge's own water type, line for line, with {@code canConvertToSource}
     * off. That one property is the whole difference between a lake and a spring, and it is
     * Factorio's rule - water is where the map put it - rather than a softening of Minecraft's.
     * Named for the block, as vanilla names its own, so a lake and a bucket both read "Water".
     */
    public static final DeferredHolder<FluidType, FluidType> WATER_TYPE = FLUID_TYPES.register(
            "water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.nauvis_fluids.water")
                    .fallDistanceModifier(0F)
                    .canExtinguish(true)
                    .canConvertToSource(false)
                    .supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
                    .canHydrate(true)
                    .isWaterLike(true)));

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

    /**
     * {@code nauvis_fluids:water} - Factorio's {@code water} tile, the still water of every lake
     * and sea the world generates. See {@link NaturalWaterFluid} for the two rules that make it
     * Factorio's. The water that flows in pipes is {@code minecraft:water}, which is what
     * {@code data/mapping.json} maps Factorio's water fluid to and what a bucket of this becomes.
     */
    public static final DeferredHolder<Fluid, NaturalWaterFluid.Source> WATER =
            FLUIDS.register("water", () -> new NaturalWaterFluid.Source(naturalWater()));

    public static final DeferredHolder<Fluid, NaturalWaterFluid.Flowing> FLOWING_WATER =
            FLUIDS.register("flowing_water", () -> new NaturalWaterFluid.Flowing(naturalWater()));

    /**
     * Water's numbers - a slope search of four, one level lost a block, a tick every five - and
     * the two things that are this fluid's point: vanilla's bucket, and a block of its own.
     */
    private static BaseFlowingFluid.Properties naturalWater() {
        return new BaseFlowingFluid.Properties(WATER_TYPE, WATER, FLOWING_WATER)
                .bucket(() -> Items.WATER_BUCKET)
                .block(ModBlocks.WATER)
                .explosionResistance(100.0F);
    }

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
