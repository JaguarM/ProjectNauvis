package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.Config;
import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws the miner's dig area in the world, on hover and while its screen is open.
 *
 * <p>A drill covers what it stands on - two by two under a burner, three by three under an
 * electric - so most of the time this outline sits exactly on the machine, which is the point: it
 * says the area is the machine rather than a number you have to look up. Anything past that comes
 * from range modules or the config, and those were the invisible part. Factorio shows a drill's
 * coverage the same way, while you are placing it.
 */
@EventBusSubscriber(modid = NauvisMining.MODID, value = Dist.CLIENT)
public final class MinerAreaPreview {

    private static final int OUTLINE_ARGB = 0xCC4AC3E8;
    private static final float LINE_WIDTH = 2.0F;

    private MinerAreaPreview() {}

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        AABB area = activeArea(minecraft);
        if (area == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;

        // 26.2 has a purpose-built submit for this. It takes the shape in world space, so
        // the camera offset goes into the pose rather than into the shape's coordinates.
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        event.getSubmitNodeCollector().submitShapeOutline(
                poseStack,
                Shapes.create(area),
                RenderTypes.lines(),
                OUTLINE_ARGB,
                LINE_WIDTH,
                false);
        poseStack.popPose();
    }

    /**
     * Which miner to outline, or null for none.
     *
     * <p>Looking at a machine is the primary trigger, the way Factorio shows a drill's
     * coverage on hover: opening a screen to find out what a machine covers is exactly the
     * friction the preview exists to remove. The open screen still counts, so the outline
     * stays up while modules are being swapped.
     */
    private static @Nullable AABB activeArea(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            // Any block of the machine, not only the one holding the block entity. A drill is
            // four or nine blocks and only one of them answers getBlockEntity, so looking one up
            // directly meant the outline appeared on a corner of the machine and nowhere else.
            BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof MinerBlock drill) {
                BlockPos anchor = Multiblock.anchorPos(drill, state, hit.getBlockPos());
                if (minecraft.level.getBlockEntity(anchor) instanceof MinerBlockEntity miner) {
                    return outline(minecraft.level, miner.digArea());
                }
            }
        }
        if (minecraft.gui.screen() instanceof MinerScreen screen) {
            MinerMenu menu = screen.getMenu();
            BlockPos anchor = menu.machinePos();
            BlockState state = minecraft.level.getBlockState(anchor);
            if (state.getBlock() instanceof MinerBlock drill) {
                return outline(minecraft.level, DigArea.of(drill.shape(), anchor,
                        state.getValue(MinerBlock.FACING), menu.extraRings()));
            }
        }
        return null;
    }

    /**
     * The volume the miner will clear: its {@link DigArea} in plan, running from the block
     * directly beneath it down to the configured floor.
     *
     * <p>Both the outline and the machine ask {@link DigArea} the same question, so the box a
     * player is shown cannot drift from the columns the drill actually walks. It used to be
     * described twice - a radius here and a spiral there - which is exactly the kind of pair that
     * agrees until somebody changes one.
     */
    private static AABB outline(Level level, DigArea area) {
        int floor = Math.max(Config.MINE_FLOOR.get(), level.getMinY());
        return new AABB(
                area.outerMinX(),
                floor,
                area.outerMinZ(),
                area.outerMaxX() + 1,
                area.y(),
                area.outerMaxZ() + 1);
    }
}
