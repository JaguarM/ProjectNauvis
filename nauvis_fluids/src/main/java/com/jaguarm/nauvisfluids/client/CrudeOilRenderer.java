package com.jaguarm.nauvisfluids.client;

import com.jaguarm.nauvislib.NauvisLibClient;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The oil x-ray: while a pumpjack is in hand, every oil well in view is outlined, through
 * whatever is in the way.
 *
 * <p>Factorio finds oil for you on the map, where every field is a magenta blot. This pack has no
 * map, and a well is a dark block on the ground that a tree or a rise hides completely, so this is
 * the substitute: pick up a pumpjack and the wells within render distance light up in Factorio's
 * map colour, walls and hills notwithstanding. Put it away and they go dark again. Where the
 * machine itself would land is {@code nauvis_lib}'s ghost, which draws every machine and marks
 * the well a pumpjack would centre on.
 *
 * <h2>Why a block entity renderer, and why off-screen</h2>
 *
 * <p>A well has a block entity, so it has a renderer, and the renderer is visited only for wells
 * that are loaded - there is no scan. What it has to fight is culling: a well behind a hill is in a
 * chunk section the visibility graph has already thrown away, and the per-section pass never
 * visits it. {@link #shouldRenderOffScreen} moves wells into the level-wide pass, which is the one
 * pass that visits a block entity whose section was culled. One frustum test per loaded well per
 * frame; wells are rare.
 *
 * <p>The lines are {@link NauvisLibClient#XRAY}: every vanilla line pipeline tests depth, and
 * lines that fail a depth test are exactly the hidden ones this exists to draw.
 */
public class CrudeOilRenderer implements BlockEntityRenderer<CrudeOilBlockEntity, CrudeOilRenderState> {

    /** Factorio's map colour for crude oil, {@code {0.78, 0.2, 0.77}}. */
    private static final int WELL_ARGB = 0xE0C733C4;

    private static final float WELL_LINE = 2.0F;

    /** The well itself, drawn a hair outside the block so its edges are not lost in the ground's. */
    private static final AABB WELL_BOX = new AABB(0, 0, 0, 1, 1, 1).inflate(0.01);

    @Override
    public CrudeOilRenderState createRenderState() {
        return new CrudeOilRenderState();
    }

    @Override
    public void extractRenderState(CrudeOilBlockEntity well, CrudeOilRenderState state,
            float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(well, state, partialTicks, cameraPosition, breakProgress);
        LocalPlayer player = Minecraft.getInstance().player;
        state.show = player != null && holdingPumpjack(player);
    }

    private static boolean holdingPumpjack(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PUMPJACK.get())
                || player.getOffhandItem().is(ModItems.PUMPJACK.get());
    }

    @Override
    public void submit(CrudeOilRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.show) {
            return;
        }
        collector.submitShapeOutline(poseStack, Shapes.create(WELL_BOX), NauvisLibClient.XRAY,
                WELL_ARGB, WELL_LINE, false);
    }

    /** A hair past the block, as the outline is. See {@code API-26.2.md} on the frustum test. */
    @Override
    public AABB getRenderBoundingBox(CrudeOilBlockEntity well) {
        return new AABB(well.getBlockPos()).inflate(1);
    }

    /** Off-screen, so a well behind a hill is still visited. That is the x-ray. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** As far as chunks load. A well you cannot see from here is one you want to know about. */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
