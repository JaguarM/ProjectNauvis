package com.jaguarm.nauvis;

import com.jaguarm.nauvis.ore.OrePatchFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen. The features are code; where they run is {@code data/nauvis/worldgen/} and the biome modifiers. */
public final class ModFeatures {

    private ModFeatures() {}

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Nauvis.MODID);

    /** Every ore patch's share of a chunk. Once per chunk, in the ore step, after vanilla's veins are removed. */
    public static final DeferredHolder<Feature<?>, OrePatchFeature> ORE_PATCH =
            FEATURES.register("ore_patch", () -> new OrePatchFeature(NoneFeatureConfiguration.CODEC));

    static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
