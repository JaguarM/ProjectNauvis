package com.jaguarm.nauvislogistics.client;

import com.jaguarm.nauvislogistics.belt.BeltBlockEntity;
import com.jaguarm.nauvislogistics.belt.BeltLane;
import com.jaguarm.nauvislogistics.belt.BeltRun;
import com.jaguarm.nauvislogistics.belt.Belts;
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

/**
 * The items on a belt: the thing the whole subsystem exists to show.
 *
 * <p>Each belt block draws what is standing on its own block, and no more. That keeps a belt line
 * culled the way everything else is - a block off the edge of the screen draws nothing - and it is
 * why this renderer, unlike the pole's, needs neither {@code shouldRenderOffScreen} nor a render
 * bounding box that reaches into other blocks.
 *
 * <h2>Where the positions come from</h2>
 *
 * <p>Nowhere on the wire. The client has its own copy of every belt run, built from block states
 * it already had and advanced by the same code the server runs - see {@code BeltRun}. So drawing
 * an item is a question the client answers locally, and a busy belt costs no network traffic at
 * all.
 *
 * <h2>Between two ticks</h2>
 *
 * <p>Items move six sixty-fourths of a block a tick, which at sixty frames a second is three
 * frames of stillness and one jump. So each item is drawn part of the way back along the step it
 * last took: {@link BeltLane#lastMove(int)} says how far that was, per item, because at a jam the
 * item at the front did not move and the one behind it did.
 */
public class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity, BeltRenderState> {

    /** How big an item rides. Small enough that four to a tile read as four things, not a smear. */
    private static final float SCALE = 0.4F;

    /** Clear of the belt surface, so an item is not z-fighting the thing carrying it. */
    private static final double LIFT = 0.02;

    private final ItemModelResolver items;

    public BeltRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public BeltRenderState createRenderState() {
        return new BeltRenderState();
    }

    @Override
    public void extractRenderState(BeltBlockEntity belt, BeltRenderState state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(belt, state, partialTicks, cameraPosition, breakProgress);

        state.used = 0;
        BeltRun run = belt.run();
        if (run == null || belt.getLevel() == null) {
            return;
        }
        BlockPos pos = belt.getBlockPos();
        int block = run.indexOf(pos);
        if (block < 0) {
            return;
        }

        int seed = (int) pos.asLong();
        for (int lane = 0; lane < Belts.LANES; lane++) {
            BeltLane track = run.lane(lane);
            var positions = track.positions();
            for (int i = 0; i < positions.size(); i++) {
                int at = positions.getInt(i);
                // Which block an item belongs to is decided by where it is on the tick, not by
                // where it is being drawn - otherwise an item on a boundary is drawn by both
                // blocks on one frame and by neither on the next.
                if (run.blockAt(at) != block) {
                    continue;
                }

                double drawn = at + (1.0F - partialTicks) * track.lastMove(i);
                Vec3 point = run.pointAt(drawn, lane);
                Direction travel = run.travelAt(run.blockAt((int) drawn));

                ItemStackRenderState model = state.model(state.used);
                this.items.updateForTopItem(model, track.item(i).toStack(1),
                        ItemDisplayContext.GROUND, belt.getLevel(), null, seed + state.used);
                state.offsets.set(state.used, point.subtract(pos.getX(), pos.getY(), pos.getZ())
                        .add(0, LIFT, 0));
                state.yaws.set(state.used, -travel.toYRot());
                state.used++;
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
            // Flat on its back, the way a dropped item lies, rather than standing up like a sign.
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(SCALE, SCALE, SCALE);
            state.models.get(i).submit(poseStack, collector, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * A little wider than the block, because an item is drawn part-way through the step it is
     * taking and that can carry it a fraction over the edge.
     *
     * <p>Nothing like the pole's problem - a belt never draws into the next block but one - but
     * the default box is exactly one block and an item straddling a boundary would pop.
     */
    @Override
    public AABB getRenderBoundingBox(BeltBlockEntity belt) {
        return new AABB(belt.getBlockPos()).inflate(0.5);
    }
}
