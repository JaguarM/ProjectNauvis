package com.jaguarm.nauvispower.client;

import com.jaguarm.nauvispower.grid.ElectricPoleBlock;
import com.jaguarm.nauvispower.grid.ElectricPoleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The wires between poles.
 *
 * <p>There is no wire coil and there are no connectors to place: <b>poles that can see each other
 * are wired.</b> The network already decided which those are - it has to, to know what is
 * connected to what - so drawing them is a matter of showing what is already true rather than of
 * asking the player to say it twice. Immersive Engineering's manual coils are a whole subsystem
 * this pack does not want, and Factorio does not have one either.
 *
 * <p>The client is told the set of links per pole rather than working it out, because it has no
 * copy of the graph and rediscovering it would mean every pole scanning a fifteen-block cube for
 * others and then somehow noticing when one moved. See
 * {@link ElectricPoleBlockEntity#links()}.
 *
 * <h2>Each wire is drawn once</h2>
 *
 * <p>Both ends know about a link, so without a rule both would draw it and the wire would be
 * double-rendered - which shows up as z-fighting rather than as anything obviously wrong. The end
 * with the lower packed position draws; the other does nothing. Links only ever name loaded poles,
 * so there is always exactly one of them present to do it.
 */
public class PoleWireRenderer implements BlockEntityRenderer<ElectricPoleBlockEntity, PoleRenderState> {

    /**
     * A solid white vanilla texture, tinted per vertex.
     *
     * <p>The pack ships no textures of its own yet, and a wire wants a flat colour rather than a
     * picture. {@code entityCutout} is the render type rather than {@code entitySolid} because it
     * does not cull: a wire is a flat ribbon and would otherwise vanish when seen from its back.
     */
    private static final RenderType WIRE = RenderTypes.entityCutout(
            Identifier.withDefaultNamespace("textures/block/white_concrete.png"));

    /** Dark copper. A Factorio wire reads as a dark line against the sky, not as a bright one. */
    private static final int COLOR = 0xFF4A2E1E;

    /**
     * How far up the head block a wire attaches: the middle of the crossarm.
     *
     * <p>The arms are at {@code y 10..12} in every tier's head cell, so this is one number and the
     * <em>block</em> it applies to is the tier's own. See {@code PoleBoxes}.
     */
    private static final float ARM_Y = 11.0F / 16.0F;

    /** Half-thickness of the ribbon, in blocks. */
    private static final float HALF_WIDTH = 0.02F;

    /** How far a wire dips at its middle, as a fraction of how far it spans. */
    private static final float SAG = 0.09F;

    /** Segments per wire. Eight is enough for a curve this shallow over seven blocks. */
    private static final int SEGMENTS = 8;

    @Override
    public PoleRenderState createRenderState() {
        return new PoleRenderState();
    }

    @Override
    public void extractRenderState(ElectricPoleBlockEntity pole, PoleRenderState state,
            float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pole, state, partialTicks, cameraPosition, breakProgress);

        state.wires.clear();
        Level level = pole.getLevel();
        if (level == null) {
            return;
        }

        BlockPos self = pole.getBlockPos();
        Vec3 here = attachment(level, self);
        // The foot of a pole stands in whatever shadow the machines cast; the wires do not.
        state.headLight = LightCoordsUtil.getLightCoords(level,
                BlockPos.containing(here.x, here.y, here.z));

        long key = self.asLong();
        for (long link : pole.links()) {
            if (key >= link) {
                continue;
            }
            // Each end attaches to its own pole, which is the whole reason this is worked out
            // here rather than in the drawing: the two ends can be different tiers, so they can be
            // different heights and - for a two-by-two pole - different distances in from the foot.
            Vec3 there = attachment(level, BlockPos.of(link));
            state.wires.add(new PoleRenderState.Wire(
                    (float) (here.x - self.getX()), (float) (here.y - self.getY()),
                    (float) (here.z - self.getZ()),
                    (float) (there.x - self.getX()), (float) (there.y - self.getY()),
                    (float) (there.z - self.getZ())));
        }
    }

    /**
     * Where a wire meets the pole whose foot is at {@code foot}, in world coordinates.
     *
     * <p>The middle of the footprint horizontally, not the middle of the foot block: a big pole is
     * two tiles across and its wires come off the tower rather than off one of its legs. For a
     * one-tile pole the two are the same point.
     *
     * <p>Falls back to a small pole's head if the block is not one of ours, which can only happen
     * in the tick between a pole being broken and the client hearing about it.
     */
    private static Vec3 attachment(Level level, BlockPos foot) {
        float width = 1;
        float depth = 1;
        int height = 4;
        if (level.getBlockState(foot).getBlock() instanceof ElectricPoleBlock pole) {
            width = pole.shape().width();
            depth = pole.shape().depth();
            height = pole.height();
        }
        return new Vec3(foot.getX() + width / 2.0,
                foot.getY() + height - 1 + ARM_Y,
                foot.getZ() + depth / 2.0);
    }

    @Override
    public void submit(PoleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        for (PoleRenderState.Wire wire : state.wires) {
            collector.submitCustomGeometry(poseStack, WIRE,
                    (pose, buffer) -> draw(pose, buffer, wire, state.headLight));
        }
    }

    /**
     * One wire, as a cross of two ribbons that sags in the middle.
     *
     * <p>Two ribbons at right angles rather than one, because a single flat one disappears when
     * you look along its edge - which for a horizontal wire is exactly what happens when you fly
     * over your own base.
     */
    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, PoleRenderState.Wire wire, int light) {
        float x0 = wire.x0();
        float y0 = wire.y0();
        float z0 = wire.z0();
        float x1 = wire.x1();
        float y1 = wire.y1();
        float z1 = wire.z1();

        float span = Mth.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
        float dip = span * SAG;

        // Perpendicular to the wire, horizontally, so the upright ribbon faces sideways.
        float length = Math.max(span, 1.0E-4F);
        float px = -(z1 - z0) / length * HALF_WIDTH;
        float pz = (x1 - x0) / length * HALF_WIDTH;

        for (int segment = 0; segment < SEGMENTS; segment++) {
            float a = (float) segment / SEGMENTS;
            float b = (float) (segment + 1) / SEGMENTS;

            float ax = Mth.lerp(a, x0, x1);
            float az = Mth.lerp(a, z0, z1);
            float ay = Mth.lerp(a, y0, y1) - dip * 4.0F * a * (1.0F - a);
            float bx = Mth.lerp(b, x0, x1);
            float bz = Mth.lerp(b, z0, z1);
            float by = Mth.lerp(b, y0, y1) - dip * 4.0F * b * (1.0F - b);

            // Upright: width in y.
            quad(pose, buffer, light,
                    ax, ay - HALF_WIDTH, az, ax, ay + HALF_WIDTH, az,
                    bx, by + HALF_WIDTH, bz, bx, by - HALF_WIDTH, bz);
            // Flat: width across the wire.
            quad(pose, buffer, light,
                    ax - px, ay, az - pz, ax + px, ay, az + pz,
                    bx + px, by, bz + pz, bx - px, by, bz - pz);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, buffer, light, ax, ay, az, 0.0F, 0.0F);
        vertex(pose, buffer, light, bx, by, bz, 0.0F, 1.0F);
        vertex(pose, buffer, light, cx, cy, cz, 1.0F, 1.0F);
        vertex(pose, buffer, light, dx, dy, dz, 1.0F, 0.0F);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light,
            float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z)
                .setColor(COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * <b>The box the frustum test uses, and the reason wires stay drawn.</b>
     *
     * <p>Its default is the unit cube at the block entity, so a pole whose foot had gone off the
     * edge of the screen stopped drawing wires that were still in plain sight. Vanilla leashes
     * never look wrong this way because an entity is culled against a box that already contains
     * what it draws; a block entity is culled against one block, and has to say otherwise.
     *
     * <p>Computed on the block entity so that a headless test can assert it - see
     * {@link ElectricPoleBlockEntity#wireBounds()}.
     */
    @Override
    public AABB getRenderBoundingBox(ElectricPoleBlockEntity pole) {
        return pole.wireBounds();
    }

    /**
     * Off-screen, because a wire leaves its own block entirely.
     *
     * <p>This takes poles out of the per-section pass and into the level-wide one, which is the
     * only one that visits a block entity whose own chunk section was culled - and a pole two
     * sections behind you can still have a wire in front of you. The cost is one frustum test per
     * loaded pole per frame rather than per visible pole; a frustum test against a box is a few
     * dot products, and there is no other way to get this right.
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
