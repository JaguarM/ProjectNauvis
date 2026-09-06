package com.jaguarm.nauvislib.client;

import java.util.List;

import com.jaguarm.nauvislib.NauvisLib;
import com.jaguarm.nauvislib.NauvisLibClient;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * Every machine's ghost: while a multi-block machine is in hand, it is drawn where a click would
 * put it, turned the way it would turn, blue if it will go and red if it will not.
 *
 * <p>Factorio's placement ghost is how a player learns where a machine goes, and a machine three
 * tiles across with a facing and a port is a thing worth seeing before it is placed. This draws
 * it for any block that is a {@link Multiblock.MachineBlock}, with no code of the machine's own:
 * the click is built exactly as a right-click would build it - the same hit, the same hand, the
 * block item's own {@code updatePlacementContext} - and {@link Multiblock#ghost} asks the block
 * the questions placement asks, in the order placement asks them. What is drawn is what will
 * happen, and there is no second copy of any machine's rule.
 *
 * <p>The outline is the machine's own, cell by cell from its shape, so a boiler's chimney and a
 * pole's height are in it. A machine that uses something in particular marks it too - the well
 * a pumpjack centres on, the water an offshore pump draws from - in water's blue. All of it
 * through terrain, so a pump aimed at a lake behind a bank is still shown.
 *
 * <p>Two events rather than a renderer, because a ghost belongs to no block entity: the
 * placement is worked out once a frame while the world is being extracted, when the world may be
 * read, and drawn when custom geometry is collected, when it may not.
 */
@EventBusSubscriber(modid = NauvisLib.MODID, value = Dist.CLIENT)
public final class MachineGhost {

    private MachineGhost() {}

    /** The pack's placement blue. */
    private static final int ALLOWED_ARGB = 0xE04AC3E8;

    /** Refused. */
    private static final int REFUSED_ARGB = 0xE0E85A4A;

    /** What the machine will use, in water's colour. */
    private static final int MARK_ARGB = 0xE03F76E4;

    private static final float LINE = 3.0F;

    /** A marked block, drawn a hair outside itself so its edges are not lost in its neighbours'. */
    private static final VoxelShape MARK_BOX = Shapes.create(new AABB(0, 0, 0, 1, 1, 1).inflate(0.01));

    /** What this frame worked out. A null ghost means nothing to draw. */
    private static Multiblock.@Nullable Ghost ghost;
    private static @Nullable MachineShape shape;
    private static List<BlockPos> marks = List.of();

    /** A machine in a hand: the stack, the item that places it and the block it places. */
    private record Held(ItemStack stack, BlockItem item, Multiblock.MachineBlock block) {}

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        ghost = null;
        marks = List.of();

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        Held held = held(player.getMainHandItem());
        if (held == null) {
            hand = InteractionHand.OFF_HAND;
            held = held(player.getOffhandItem());
        }
        if (held == null) {
            return;
        }

        // Exactly the click a right-click would make, through the same item hook - which is how
        // an offshore pump's click on a lake is lifted to the surface for the ghost too.
        BlockPlaceContext context = held.item().updatePlacementContext(
                new BlockPlaceContext(player, hand, held.stack(), hit));
        if (context == null) {
            return;
        }

        Multiblock.Ghost found = Multiblock.ghost(held.block(), context);
        shape = held.block().shape();
        marks = found.allowed()
                ? held.block().placementMarks(player.level(), found.anchor(), found.facing())
                : List.of();
        ghost = found;
    }

    private static @Nullable Held held(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof Multiblock.MachineBlock block
                ? new Held(stack, item, block)
                : null;
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        Multiblock.Ghost drawn = ghost;
        MachineShape drawnShape = shape;
        if (drawn == null || drawnShape == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        int colour = drawn.allowed() ? ALLOWED_ARGB : REFUSED_ARGB;
        for (int part = 0; part < drawnShape.cellCount(); part++) {
            outline(pose, collector, camera, drawnShape.cellPos(drawn.anchor(), part, drawn.facing()),
                    drawnShape.cell(part).shape(drawn.facing()), colour);
        }
        for (BlockPos mark : marks) {
            outline(pose, collector, camera, mark, MARK_BOX, MARK_ARGB);
        }
    }

    private static void outline(PoseStack pose, SubmitNodeCollector collector, Vec3 camera,
            BlockPos at, VoxelShape outline, int argb) {
        pose.pushPose();
        pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
        collector.submitShapeOutline(pose, outline, NauvisLibClient.XRAY, argb, LINE, false);
        pose.popPose();
    }
}
