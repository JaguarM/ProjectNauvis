package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/**
 * The client half: what steam looks like.
 *
 * <p>Every registered fluid needs a {@link FluidModel} in 26.2 - this is not optional and not
 * skippable for a fluid that is never placed in the world. Without one you get the
 * missing-texture checkerboard anywhere the fluid is drawn, which for steam means every tank
 * readout and every Jade tooltip that shows a boiler's contents. NeoForge logs
 * {@code Missing FluidModel for fluid} when it happens, and nothing else complains.
 *
 * <p>Vanilla textures, like every other model in this pack: water, which is what steam is. The
 * tint is what makes it read as steam rather than as water in a pipe - pale and washed out
 * against water's blue.
 */
@Mod(value = NauvisFluids.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisFluids.MODID, value = Dist.CLIENT)
public class NauvisFluidsClient {

    /** Pale, faintly warm white. Steam is water that has stopped looking like water. */
    private static final int STEAM_TINT = 0xFFD8E8F0;

    public NauvisFluidsClient() {}

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(
                new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        null,
                        FluidTintSources.constant(STEAM_TINT)),
                ModFluids.STEAM.get());
    }
}
