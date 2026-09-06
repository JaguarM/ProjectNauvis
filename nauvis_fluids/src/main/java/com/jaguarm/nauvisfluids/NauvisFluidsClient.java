package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvisfluids.client.CrudeOilRenderer;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/**
 * The client half: what steam, crude oil and natural water look like, and the oil x-ray.
 *
 * <p>Every registered fluid needs a {@link FluidModel} in 26.2 - this is not optional and not
 * skippable for a fluid that is never placed in the world. Without one you get the
 * missing-texture checkerboard anywhere the fluid is drawn, which means every tank readout and
 * every Jade tooltip that shows a machine's contents. NeoForge logs
 * {@code Missing FluidModel for fluid} when it happens, and nothing else complains.
 *
 * <p>Vanilla textures, like every other model in this pack: water, tinted. Pale for steam, near
 * black for oil - Factorio's crude oil has a base colour of pure black and a flow colour of grey -
 * and for natural water, water's own three sprites and the biome's colour, which is what makes
 * a swamp's water brown and a warm ocean's turquoise exactly as before.
 */
@Mod(value = NauvisFluids.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisFluids.MODID, value = Dist.CLIENT)
public class NauvisFluidsClient {

    /** Pale, faintly warm white. Steam is water that has stopped looking like water. */
    private static final int STEAM_TINT = 0xFFD8E8F0;

    /** Not quite black, so it still reads as a liquid with a surface rather than as a hole. */
    private static final int CRUDE_OIL_TINT = 0xFF1A1418;

    public NauvisFluidsClient() {}

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(water(STEAM_TINT), ModFluids.STEAM.get());
        event.register(water(CRUDE_OIL_TINT), ModFluids.CRUDE_OIL.get());
        // Vanilla's own water model, sprite for sprite: still, flowing, the overlay drawn against
        // glass and leaves, and the biome tint. One model for both halves of the fluid, as
        // vanilla registers its own.
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                FluidTintSources.water()),
                ModFluids.WATER.get(), ModFluids.FLOWING_WATER.get());
    }

    private static FluidModel.Unbaked water(int tint) {
        return new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                null,
                FluidTintSources.constant(tint));
    }

    /** The x-ray: an outline drawn on every oil well while a pumpjack is in hand. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRUDE_OIL.get(), context -> new CrudeOilRenderer());
    }

    /** The lines the x-ray draws with have no depth test, which no vanilla pipeline offers. */
    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CrudeOilRenderer.XRAY_PIPELINE);
    }
}
