package com.jaguarm.nauvislogistics.client;

import com.jaguarm.nauvislogistics.belt.BeltLane;
import com.jaguarm.nauvislogistics.belt.Belts;
import com.jaguarm.nauvislogistics.belt.SplitterBlock;
import com.jaguarm.nauvislogistics.belt.SplitterBlockEntity;
import com.jaguarm.nauvislogistics.belt.SplitterShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The items crossing a splitter. */
public class SplitterRenderer implements BlockEntityRenderer<SplitterBlockEntity, BeltRenderState> {

    /** The same size an item rides at on a belt - it is the same item, still travelling. */
    private static final float SCALE = 0.4F;

    /** Clear of the deck, so an item is not z-fighting the thing carrying it. */
    private static final double LIFT = 0.02;

    private final ItemModelResolver items;

    public SplitterRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public BeltRenderState createRenderState() {
        return new BeltRenderState();
    }

    @Override
    public void extractRenderState(SplitterBlockEntity splitter, BeltRenderState state,
            float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                splitter, state, partialTicks, cameraPosition, breakProgress);

        state.used = 0;
        if (splitter.getLevel() == null) {
            return;
        }
        Direction facing = splitter.getBlockState().getValue(SplitterBlock.FACING);
        BlockPos pos = splitter.getBlockPos();
        int seed = (int) pos.asLong();

        for (int track = 0; track < SplitterShape.SHAPE.cellCount(); track++) {
            for (int lane = 0; lane < Belts.LANES; lane++) {
                BeltLane held = splitter.lane(track, lane);
                var positions = held.positions();
                for (int i = 0; i < positions.size(); i++) {
                    // Part of the way back along the step it last took, so six units a tick reads
                    // as movement rather than as three still frames and a jump. As on a belt.
                    double drawn = positions.getInt(i) + (1.0F - partialTicks) * held.lastMove(i);
                    Vec3 point = splitter.pointAt(
                            track, splitter.exitOf(track, lane, i), lane, drawn, facing);

                    ItemStackRenderState model = state.model(state.used);
                    this.items.updateForTopItem(model, held.item(i).toStack(1),
                            ItemDisplayContext.GROUND, splitter.getLevel(), null, seed + state.used);
                    state.offsets.set(state.used,
                            point.subtract(pos.getX(), pos.getY(), pos.getZ()).add(0, LIFT, 0));
                    state.yaws.set(state.used, -facing.toYRot());
                    state.used++;
                }
            }
        }
    }

    @Override
    public void submit(BeltRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        for (int i = 0; i < state.used; i++) {
            Vec3 offset = state.offsets.get(i);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yaws.getFloat(i)));
            // Flat on its back, the way a dropped item lies. As on a belt.
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(SCALE, SCALE, SCALE);
            state.models.get(i).submit(poseStack, collector, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * Both cells, and a little over.
     *
     * <p>The anchor is one of the two blocks, so the default box is half the machine. Every cell
     * is taken in rather than the second one being assumed to lie east, because which way the
     * other track is depends on which way the splitter is turned.
     */
    @Override
    public AABB getRenderBoundingBox(SplitterBlockEntity splitter) {
        Direction facing = splitter.getBlockState().getValue(SplitterBlock.FACING);
        AABB box = new AABB(splitter.getBlockPos());
        for (BlockPos cell : SplitterShape.SHAPE.positions(splitter.getBlockPos(), facing)) {
            box = box.minmax(new AABB(cell));
        }
        return box.inflate(0.5);
    }
}
