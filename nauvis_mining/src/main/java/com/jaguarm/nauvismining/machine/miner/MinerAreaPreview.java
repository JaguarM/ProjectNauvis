package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismining.Config;
import com.jaguarm.nauvismining.NauvisMining;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * Draws the drill's area in the world, on hover and while its screen is open.
 *
 * <p>A burner covers what it stands on and an electric covers that and a ring around it, so the
 * outline says the area is the machine, plus one, rather than a number you have to look up.
 * Factorio shows a drill's coverage the same way, while you are placing it. The box runs from the
 * floor to the drill's own level: the ground it reaches down through for ore.
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
     * Which drill to outline, or null for none.
     *
     * <p>Looking at a machine is the primary trigger, the way Factorio shows a drill's coverage on
     * hover. The open screen still counts, so the outline stays up while modules are being
     * swapped. Any block of the machine counts, not only the one holding the block entity: the
     * area is arithmetic on the state and the anchor, so nothing needs asking.
     */
    private static @Nullable AABB activeArea(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof MinerBlock drill) {
                BlockPos anchor = Multiblock.anchorPos(drill, state, hit.getBlockPos());
                return outline(minecraft.level, MinerBlock.digArea(state, anchor));
            }
        }
        if (minecraft.gui.screen() instanceof MinerScreen screen) {
            BlockPos anchor = screen.getMenu().machinePos();
            BlockState state = minecraft.level.getBlockState(anchor);
            if (state.getBlock() instanceof MinerBlock) {
                return outline(minecraft.level, MinerBlock.digArea(state, anchor));
            }
        }
        return null;
    }

    /**
     * The volume the drill draws from: its {@link DigArea} in plan, running from the block
     * directly beneath it down to the configured floor.
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
