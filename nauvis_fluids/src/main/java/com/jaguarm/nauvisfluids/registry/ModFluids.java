package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.fluid.ContainedFluid;
import com.jaguarm.nauvisfluids.fluid.NaturalWaterFluid;

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
 * The fluids this mod owns: steam, the oil chain, and the water the world is made with.
 *
 * <p>A fluid is two registrations: NeoForge's {@link FluidType}, which is everything about how it
 * behaves as a substance, and Minecraft's {@link Fluid}, which is the thing a
 * {@code FluidResource} names. Seven of them are never in the world, so their types are left
 * almost entirely default - the properties that matter are all about swimming in it, and nobody
 * ever will. Natural water is nothing but in the world, and its type is vanilla water's with one
 * property turned off.
 *
 * <p>Every id here is the one {@code data/mapping.json} gives the Factorio fluid, so it is
 * identity and not ours to change: {@code heavy_oil}, {@code light_oil}, {@code petroleum_gas},
 * {@code lubricant}, {@code sulfuric_acid}. Water in a pipe is {@code minecraft:water}, which
 * the mapping stands in for Factorio's water.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, NauvisFluids.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, NauvisFluids.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = contained("steam");
    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_TYPE = contained("crude_oil");
    public static final DeferredHolder<FluidType, FluidType> HEAVY_OIL_TYPE = contained("heavy_oil");
    public static final DeferredHolder<FluidType, FluidType> LIGHT_OIL_TYPE = contained("light_oil");
    public static final DeferredHolder<FluidType, FluidType> PETROLEUM_GAS_TYPE = contained("petroleum_gas");
    public static final DeferredHolder<FluidType, FluidType> LUBRICANT_TYPE = contained("lubricant");
    public static final DeferredHolder<FluidType, FluidType> SULFURIC_ACID_TYPE = contained("sulfuric_acid");

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

    /** Steam, the one substance a boiler makes and a steam engine drinks. */
    public static final DeferredHolder<Fluid, ContainedFluid> STEAM =
            FLUIDS.register("steam", () -> new ContainedFluid(STEAM_TYPE));

    /**
     * {@code nauvis_fluids:crude_oil} - the same id as the resource block, exactly as Factorio
     * names both its {@code crude-oil} fluid and its {@code crude-oil} resource entity. Different
     * registries, so no clash, and one name for the player to learn.
     */
    public static final DeferredHolder<Fluid, ContainedFluid> CRUDE_OIL =
            FLUIDS.register("crude_oil", () -> new ContainedFluid(CRUDE_OIL_TYPE));

    /** The refinery's three, in the order its outputs lie: heavy, light, petroleum gas. */
    public static final DeferredHolder<Fluid, ContainedFluid> HEAVY_OIL =
            FLUIDS.register("heavy_oil", () -> new ContainedFluid(HEAVY_OIL_TYPE));
    public static final DeferredHolder<Fluid, ContainedFluid> LIGHT_OIL =
            FLUIDS.register("light_oil", () -> new ContainedFluid(LIGHT_OIL_TYPE));
    public static final DeferredHolder<Fluid, ContainedFluid> PETROLEUM_GAS =
            FLUIDS.register("petroleum_gas", () -> new ContainedFluid(PETROLEUM_GAS_TYPE));

    /** The chemical plant's two fluids: ten heavy oil make ten lubricant, and acid is sulfur, iron and water. */
    public static final DeferredHolder<Fluid, ContainedFluid> LUBRICANT =
            FLUIDS.register("lubricant", () -> new ContainedFluid(LUBRICANT_TYPE));
    public static final DeferredHolder<Fluid, ContainedFluid> SULFURIC_ACID =
            FLUIDS.register("sulfuric_acid", () -> new ContainedFluid(SULFURIC_ACID_TYPE));

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

    /**
     * A fluid that lives only in machines, named {@code fluid.nauvis_fluids.<name>}: nothing can
     * happen to you in it, because you can never be in it.
     */
    private static DeferredHolder<FluidType, FluidType> contained(String name) {
        return FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid.nauvis_fluids." + name)
                .canDrown(false)
                .canSwim(false)
                .canPushEntity(false)
                .canExtinguish(false)
                .canConvertToSource(false)
                .supportsBoating(false)));
    }

    private ModFluids() {}
}
