package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.Config;
import com.jaguarm.nauvismining.NauvisMining;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
 * Draws the miner's dig area in the world while its screen is open.
 *
 * <p>Range is otherwise an invisible number: nothing tells you what a Range module actually
 * bought until you watch the machine dig for a while. Outlining the footprint makes the
 * effect legible immediately, the way Factorio shows a drill's coverage while placing it.
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
        if (minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof MinerBlockEntity miner) {
            return digArea(minecraft.level, hit.getBlockPos(), miner.range());
        }
        if (minecraft.gui.screen() instanceof MinerScreen screen) {
            MinerMenu menu = screen.getMenu();
            return digArea(minecraft.level, menu.machinePos(), menu.range());
        }
        return null;
    }

    /**
     * The volume the miner will clear: a square of side {@code 2 * range - 1} centred on the
     * machine, running from the block directly beneath it down to the configured floor.
     * Mirrors the bounds {@code MinerBlockEntity.findNextTarget} walks.
     */
    private static AABB digArea(Level level, BlockPos pos, int range) {
        int reach = range - 1;
        int floor = Math.max(Config.MINE_FLOOR.get(), level.getMinY());
        return new AABB(
                pos.getX() - reach,
                floor,
                pos.getZ() - reach,
                pos.getX() + reach + 1,
                pos.getY(),
                pos.getZ() + reach + 1);
    }
}
