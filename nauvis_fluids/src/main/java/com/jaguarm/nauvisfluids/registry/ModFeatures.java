package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.oil.CrudeOilFieldFeature;
import com.jaguarm.nauvisfluids.water.NaturalWaterFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen.
 *
 * <p>The features are code; where and how often each runs is data. {@code data/nauvis_fluids/worldgen/}
 * holds the configured and placed features and {@code data/nauvis_fluids/neoforge/biome_modifier/}
 * adds them to every overworld biome, so the density of oil is a number in a JSON file rather
 * than a constant here, and the water swap is a line a pack can leave out.
 */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, NauvisFluids.MODID);

    public static final DeferredHolder<Feature<?>, CrudeOilFieldFeature> CRUDE_OIL_FIELD =
            FEATURES.register("crude_oil_field",
                    () -> new CrudeOilFieldFeature(NoneFeatureConfiguration.CODEC));

    /** Every {@code minecraft:water} a chunk generated with, made natural. Once per chunk, last of all. */
    public static final DeferredHolder<Feature<?>, NaturalWaterFeature> NATURAL_WATER =
            FEATURES.register("natural_water",
                    () -> new NaturalWaterFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
