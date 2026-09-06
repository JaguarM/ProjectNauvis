package com.jaguarm.nauvislib;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The client half of the library: lines that show through anything.
 *
 * <p>Every vanilla line pipeline tests depth, and the lines the pack draws are exactly the ones
 * that must not - a machine's ghost behind a bank, an oil well under a hill. {@link #XRAY} is
 * vanilla's lines with the depth test set to always pass, registered here once so that every mod
 * draws with the same one. {@code client/MachineGhost} is what draws with it for every machine;
 * the oil x-ray in {@code nauvis_fluids} is the other.
 */
@Mod(value = NauvisLib.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisLib.MODID, value = Dist.CLIENT)
public class NauvisLibClient {

    /** Vanilla's lines, with the depth test turned off. */
    public static final RenderPipeline XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NauvisLib.MODID, "pipeline/xray"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    /** The same setup {@code RenderTypes.LINES} uses, on the pipeline above. */
    public static final RenderType XRAY = RenderType.create(
            "nauvis_lib:xray",
            RenderSetup.builder(XRAY_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());

    public NauvisLibClient() {}

    /** Registered through the event so it is compiled with vanilla's. */
    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(XRAY_PIPELINE);
    }
}
