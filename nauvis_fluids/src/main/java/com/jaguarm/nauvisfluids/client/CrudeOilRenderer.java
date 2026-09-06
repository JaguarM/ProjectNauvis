package com.jaguarm.nauvisfluids.client;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The oil x-ray: while a pumpjack is in hand, every oil well in view is outlined, through
 * whatever is in the way, and the one the machine would snap to shows the footprint it would take.
 *
 * <p>Factorio finds oil for you on the map, where every field is a magenta blot. This pack has no
 * map, and a well is a dark block on the ground that a tree or a rise hides completely, so this is
 * the substitute: pick up a pumpjack and the wells within render distance light up in Factorio's
 * map colour, walls and hills notwithstanding. Put it away and they go dark again.
 *
 * <h2>Why a block entity renderer, and why off-screen</h2>
 *
 * <p>A well has a block entity, so it has a renderer, and the renderer is visited only for wells
 * that are loaded - there is no scan. What it has to fight is culling: a well behind a hill is in a
 * chunk section the visibility graph has already thrown away, and the per-section pass never
 * visits it. {@link #shouldRenderOffScreen} moves wells into the level-wide pass, which is the one
 * pass that visits a block entity whose section was culled, and {@link #getRenderBoundingBox}
 * covers the footprint so the frustum test does not clip the outline when the well itself has
 * left the screen. One frustum test per loaded well per frame; wells are rare.
 *
 * <h2>Why a pipeline of our own</h2>
 *
 * <p>Every vanilla line pipeline tests depth, and lines that fail a depth test are exactly the
 * hidden ones this exists to draw. {@link #XRAY_PIPELINE} is vanilla's lines snippet with the
 * depth test set to always pass, registered through NeoForge's pipeline event so it is compiled
 * with the rest.
 *
 * <h2>The footprint is the snap rule, asked the same question</h2>
 *
 * <p>Where the machine will land is worked out by {@link PumpjackBlock#snapPart}, which is the
 * method placement itself calls, on the block the crosshair would place at and the way the player
 * faces. What is drawn is therefore what will happen, and there is no second copy of the rule.
 */
public class CrudeOilRenderer implements BlockEntityRenderer<CrudeOilBlockEntity, CrudeOilRenderState> {

    /** Vanilla's lines, with the depth test turned off. */
    public static final RenderPipeline XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "pipeline/oil_xray"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    /** Lines through anything. The offshore pump's ghost draws with it too. */
    public static final RenderType XRAY = RenderType.create(
            "nauvis_fluids:oil_xray",
            RenderSetup.builder(XRAY_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());

    /** Factorio's map colour for crude oil, {@code {0.78, 0.2, 0.77}}. */
    private static final int WELL_ARGB = 0xE0C733C4;

    /** The footprint, in the pack's placement blue, brighter because it is the thing being decided. */
    private static final int FOOTPRINT_ARGB = 0xE04AC3E8;

    private static final float WELL_LINE = 2.0F;
    private static final float FOOTPRINT_LINE = 3.0F;

    /** The well itself, drawn a hair outside the block so its edges are not lost in the ground's. */
    private static final AABB WELL_BOX = new AABB(0, 0, 0, 1, 1, 1).inflate(0.01);

    /** How far a footprint outline can reach from the well: a block each way and two up. */
    private static final int REACH = 3;

    @Override
    public CrudeOilRenderState createRenderState() {
        return new CrudeOilRenderState();
    }

    @Override
    public void extractRenderState(CrudeOilBlockEntity well, CrudeOilRenderState state,
            float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(well, state, partialTicks, cameraPosition, breakProgress);

        state.show = false;
        state.footprint = null;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = well.getLevel();
        if (player == null || level == null || !holdingPumpjack(player)) {
            return;
        }
        state.show = true;

        BlockPos placeAt = placementPos(minecraft, level);
        if (placeAt == null) {
            return;
        }
        Direction facing = player.getDirection();
        int part = PumpjackBlock.snapPart(level, placeAt, facing);
        if (part < 0) {
            return;
        }
        MachineShape shape = PumpjackShape.SHAPE;
        BlockPos anchor = shape.anchorPos(placeAt, part, facing);
        if (!anchor.below().equals(well.getBlockPos())) {
            return;
        }

        // The machine's box, relative to this well: the anchor is one up from the well and the
        // footprint is a block each way from it, two blocks tall in the middle.
        BlockPos origin = well.getBlockPos();
        state.footprint = new AABB(
                anchor.getX() - 1 - origin.getX(), anchor.getY() - origin.getY(), anchor.getZ() - 1 - origin.getZ(),
                anchor.getX() + 2 - origin.getX(), anchor.getY() + 2 - origin.getY(), anchor.getZ() + 2 - origin.getZ());
    }

    private static boolean holdingPumpjack(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PUMPJACK.get())
                || player.getOffhandItem().is(ModItems.PUMPJACK.get());
    }

    /**
     * The block a right-click would place into right now: the one looked at if it gives way, else
     * the one beyond the face looked at. Vanilla's own rule for where a block goes.
     */
    private static @Nullable BlockPos placementPos(Minecraft minecraft, Level level) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos looked = hit.getBlockPos();
        return level.getBlockState(looked).canBeReplaced() ? looked : looked.relative(hit.getDirection());
    }

    @Override
    public void submit(CrudeOilRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.show) {
            return;
        }
        collector.submitShapeOutline(poseStack, Shapes.create(WELL_BOX), XRAY, WELL_ARGB, WELL_LINE, false);
        if (state.footprint != null) {
            collector.submitShapeOutline(poseStack, Shapes.create(state.footprint), XRAY,
                    FOOTPRINT_ARGB, FOOTPRINT_LINE, false);
        }
    }

    /** The footprint reaches past the well, and the frustum test has to know. See {@code API-26.2.md}. */
    @Override
    public AABB getRenderBoundingBox(CrudeOilBlockEntity well) {
        return new AABB(well.getBlockPos()).inflate(REACH);
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
