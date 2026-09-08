package com.jaguarm.nauvismining.client;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.NauvisLibClient;
import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.registry.ModTags;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * The ore x-ray: while a drill is in hand, the iron, copper and coal in the loaded chunks around
 * the player are outlined through the ground, one box per vein in each ore's colour.
 * There is no map, so this is how a patch is found; a pumpjack does the same for oil wells.
 */
@EventBusSubscriber(modid = NauvisMining.MODID, value = Dist.CLIENT)
public final class OreXray {

    private OreXray() {}

    /** Chunks each way from the player's own. */
    private static final int RADIUS = 8;
    private static final int RESCAN_TICKS = 20;
    private static final float LINE = 2.0F;
    /** Factorio's map colours for iron, copper and coal, the coal lifted off black so it shows; white for any other tagged ore. */
    private static final int[] ARGB = {0xE06A8694, 0xE0CD6337, 0xE0404040, 0xE0F0F0F0};
    private static final int KINDS = ARGB.length;

    private record Box(AABB box, int argb) {}

    private static List<Box> boxes = List.of();
    private static long scannedAt = Long.MIN_VALUE;
    private static ChunkPos scannedFrom = ChunkPos.ZERO;
    private static ClientLevel scannedLevel;

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !(holdsDrill(player.getMainHandItem()) || holdsDrill(player.getOffhandItem()))) {
            boxes = List.of();
            scannedAt = Long.MIN_VALUE;
            return;
        }
        ChunkPos from = player.chunkPosition();
        if (level != scannedLevel || !from.equals(scannedFrom) || level.getGameTime() - scannedAt >= RESCAN_TICKS) {
            boxes = scan(level, from);
            scannedAt = level.getGameTime();
            scannedFrom = from;
            scannedLevel = level;
        }
    }

    private static boolean holdsDrill(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof MinerBlock;
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        List<Box> drawn = boxes;
        if (drawn.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Box box : drawn) {
            event.getSubmitNodeCollector().submitShapeOutline(pose, Shapes.create(box.box()),
                    NauvisLibClient.XRAY, box.argb(), LINE, false);
        }
        pose.popPose();
    }

    /** One box per ore per section that holds any: a palette check first, so empty ground costs nothing. */
    private static List<Box> scan(ClientLevel level, ChunkPos from) {
        List<Box> found = new ArrayList<>();
        int[] min = new int[KINDS * 3];
        int[] max = new int[KINDS * 3];
        for (int cx = from.x() - RADIUS; cx <= from.x() + RADIUS; cx++) {
            for (int cz = from.z() - RADIUS; cz <= from.z() + RADIUS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(state -> kindOf(state) >= 0)) {
                        continue;
                    }
                    java.util.Arrays.fill(min, 16);
                    java.util.Arrays.fill(max, -1);
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int kind = kindOf(section.getBlockState(x, y, z));
                                if (kind < 0) {
                                    continue;
                                }
                                min[kind * 3] = Math.min(min[kind * 3], x);
                                min[kind * 3 + 1] = Math.min(min[kind * 3 + 1], y);
                                min[kind * 3 + 2] = Math.min(min[kind * 3 + 2], z);
                                max[kind * 3] = Math.max(max[kind * 3], x);
                                max[kind * 3 + 1] = Math.max(max[kind * 3 + 1], y);
                                max[kind * 3 + 2] = Math.max(max[kind * 3 + 2], z);
                            }
                        }
                    }
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    for (int kind = 0; kind < KINDS; kind++) {
                        if (max[kind * 3] < 0) {
                            continue;
                        }
                        found.add(new Box(new AABB(
                                (cx << 4) + min[kind * 3], baseY + min[kind * 3 + 1], (cz << 4) + min[kind * 3 + 2],
                                (cx << 4) + max[kind * 3] + 1, baseY + max[kind * 3 + 1] + 1, (cz << 4) + max[kind * 3 + 2] + 1),
                                ARGB[kind]));
                    }
                }
            }
        }
        return merged(found);
    }

    /** Boxes of one ore that touch are one vein, so they are drawn as one box. */
    private static List<Box> merged(List<Box> boxes) {
        List<Box> out = new ArrayList<>(boxes);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < out.size() && !changed; i++) {
                for (int j = i + 1; j < out.size(); j++) {
                    Box a = out.get(i);
                    Box b = out.get(j);
                    if (a.argb() == b.argb() && a.box().inflate(0.5).intersects(b.box())) {
                        out.set(i, new Box(a.box().minmax(b.box()), a.argb()));
                        out.remove(j);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Which colour an ore takes, or -1 for a block that is not one of the drill's ores. */
    private static int kindOf(BlockState state) {
        if (!state.is(ModTags.FACTORIO_ORES)) {
            return -1;
        }
        if (state.is(Tags.Blocks.ORES_IRON)) {
            return 0;
        }
        if (state.is(Tags.Blocks.ORES_COPPER)) {
            return 1;
        }
        if (state.is(Tags.Blocks.ORES_COAL)) {
            return 2;
        }
        return 3;
    }
}
