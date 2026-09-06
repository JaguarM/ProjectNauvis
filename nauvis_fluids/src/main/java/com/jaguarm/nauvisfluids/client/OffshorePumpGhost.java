package com.jaguarm.nauvisfluids.client;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The offshore pump's ghost: while one is in hand, the pump is drawn where a click would put it,
 * turned the way it would turn, and the water it would draw from is marked.
 *
 * <p>Factorio's placement ghost is how a player learns where a pump goes, and the pumpjack has
 * its own here - a footprint drawn on the well it would snap to. Water has no block entity to
 * hang a renderer on, so this one is two events instead: the placement is worked out once a
 * frame while the world is being extracted, and drawn when custom geometry is collected. Both
 * answers come from the block's own methods - {@link OffshorePumpBlock#afloat} for where the
 * click lands and {@link OffshorePumpBlock#aim} for which way - so what is drawn is what will
 * happen, and there is no second copy of the rule.
 *
 * <p>Blue when the pump will go, drawn as the machine's own outline; red when it will not, drawn
 * the way the player faces, so they can see both where it would have gone and that it will not.
 * The water block the intake would draw from is outlined in water's blue, which is the "why here"
 * a shoreline needs. All of it through terrain, with the oil x-ray's lines, so a pump aimed at a
 * lake behind a bank is still shown.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID, value = Dist.CLIENT)
public final class OffshorePumpGhost {

    private OffshorePumpGhost() {}

    /** The pack's placement blue, as the pumpjack's footprint. */
    private static final int ALLOWED_ARGB = 0xE04AC3E8;

    /** Refused. */
    private static final int REFUSED_ARGB = 0xE0E85A4A;

    /** The water it would draw from, in water's own colour. */
    private static final int WATER_ARGB = 0xE03F76E4;

    private static final float LINE = 3.0F;

    /** The water block, drawn a hair outside itself so its edges are not lost in the surface's. */
    private static final VoxelShape WATER_BOX = Shapes.create(new AABB(0, 0, 0, 1, 1, 1).inflate(0.01));

    /** What this frame worked out. Null anchor means nothing to draw. */
    private static @Nullable BlockPos anchor;
    private static Direction facing = Direction.NORTH;
    private static boolean allowed;
    private static @Nullable BlockPos water;

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        anchor = null;
        water = null;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = event.getLevel();
        if (player == null || !holdingPump(player)) {
            return;
        }
        BlockPos placeAt = placementPos(minecraft, level);
        if (placeAt == null) {
            return;
        }

        BlockPos at = OffshorePumpBlock.afloat(level, placeAt);
        Direction aimed = OffshorePumpBlock.aim(level, at, player.getDirection());
        allowed = aimed != null;
        facing = allowed ? aimed : player.getDirection();
        anchor = at;
        water = allowed ? OffshorePumpBlock.waterAt(level, at, facing) : null;
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        BlockPos at = anchor;
        if (at == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        MachineShape shape = OffshorePumpShape.SHAPE;
        int colour = allowed ? ALLOWED_ARGB : REFUSED_ARGB;
        for (int part = 0; part < shape.cellCount(); part++) {
            outline(pose, collector, camera, shape.cellPos(at, part, facing),
                    shape.cell(part).shape(facing), colour);
        }
        if (water != null) {
            outline(pose, collector, camera, water, WATER_BOX, WATER_ARGB);
        }
    }

    private static void outline(PoseStack pose, SubmitNodeCollector collector, Vec3 camera,
            BlockPos at, VoxelShape shape, int argb) {
        pose.pushPose();
        pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
        collector.submitShapeOutline(pose, shape, CrudeOilRenderer.XRAY, argb, LINE, false);
        pose.popPose();
    }

    private static boolean holdingPump(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.OFFSHORE_PUMP.get())
                || player.getOffhandItem().is(ModItems.OFFSHORE_PUMP.get());
    }

    /**
     * The block a right-click would place into right now: the one looked at if it gives way, else
     * the one beyond the face looked at. Vanilla's own rule for where a block goes, before the
     * pump's item lifts it out of the water.
     */
    private static @Nullable BlockPos placementPos(Minecraft minecraft, ClientLevel level) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos looked = hit.getBlockPos();
        return level.getBlockState(looked).canBeReplaced() ? looked : looked.relative(hit.getDirection());
    }
}
