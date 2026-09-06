package com.jaguarm.nauvisfluids.oil;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Puts an oil field into the world: a handful of wells, each on its own patch of level ground.
 *
 * <p>Factorio's oil comes as fields of several wells with a few tiles between them, never in the
 * starting area, at about 1.8 fields per square kilometre - and it is easy to find, because the
 * map view shows every field as a magenta blot. This pack has no map, only a pumpjack in hand that
 * outlines wells within render distance, so the density has to do the map's job: <b>about four
 * times Factorio's</b>, one field per three hundred chunks, which puts the nearest one typically
 * inside a hundred and fifty blocks of wherever you stand. The chance per chunk is in the placed
 * feature JSON; the starting area is {@link #STARTING_AREA} here, because it is a rule about where
 * a field may begin rather than about how often one is rolled. Placement is behaviour, not
 * identity - see {@code GAPS.md}.
 *
 * <p><b>A superflat world runs none of this.</b> The default "Classic Flat" preset has
 * {@code features: false} - no trees, no ores, no oil - and only the "Overworld" flat preset turns
 * them on. {@code /oil field} is for that world, and for a playtest that does not want to walk.
 *
 * <h2>Wells are four blocks apart</h2>
 *
 * <p>A pumpjack is 3x3, so two wells three apart could each take one with the machines touching,
 * and four apart leaves a one-block lane for the pipe. Factorio's wells are typically five to
 * eight tiles apart; four is the tightest a field can be here and still be pumpable without a
 * pipe going over or under a machine. Every well in a field sits on that grid, which is what makes
 * a field a thing you lay pumpjacks out on rather than squeeze them into.
 *
 * <h2>Each well levels its own three by three</h2>
 *
 * <p>Factorio's terrain is flat and Minecraft's is not, so a well flattens the ground a pumpjack
 * will need: the eight blocks round it are filled up to its level with dirt where they dip, and
 * cleared down to it where they rise or hold a plant or a tree. That is a small pit or a small
 * pad on a hillside, and it is what makes a generated field pumpable without a shovel.
 *
 * <p>Runs in {@code top_layer_modification}, after trees and grass, so nothing grows on the pad
 * afterwards.
 */
public class CrudeOilFieldFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * No oil this close to the origin. Factorio's {@code has_starting_area_placement = false}
     * keeps oil out of the starting area, which is a few hundred tiles across on default
     * settings; the world origin stands in for Factorio's starting position. A hundred and fifty
     * rather than Factorio's radius, for the same reason the density is higher: there is no map,
     * and the first field should be reachable on foot from the first furnace.
     */
    public static final int STARTING_AREA = 150;

    /** Wells per field. */
    public static final int MIN_WELLS = 3;
    public static final int MAX_WELLS = 8;

    /** The grid wells sit on, in blocks. A 3x3 pumpjack and a one-block lane. */
    public static final int WELL_SPACING = 4;

    /** How many grid steps from the field's origin a well may be. Two gives a 5x5 grid of slots. */
    public static final int FIELD_REACH = 2;

    /** How much room over a well is cleared: a pumpjack is two blocks tall in the middle. */
    public static final int HEADROOM = 3;

    public CrudeOilFieldFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos origin = context.origin();
        if (CrudeOilField.distance(origin) < STARTING_AREA) {
            return false;
        }
        return !placeField(context.level(), context.random(), origin).isEmpty();
    }

    /**
     * Places one field around {@code origin} and says where its wells went.
     *
     * <p>Separated from {@link #place} so that a test can put a field down on ground it built,
     * anywhere, without the starting-area rule getting in the way.
     */
    public static List<BlockPos> placeField(WorldGenLevel level, RandomSource random, BlockPos origin) {
        int wanted = MIN_WELLS + random.nextInt(MAX_WELLS - MIN_WELLS + 1);
        List<BlockPos> wells = new ArrayList<>(wanted);
        LongOpenHashSet slotsTried = new LongOpenHashSet();

        int slots = (2 * FIELD_REACH + 1) * (2 * FIELD_REACH + 1);
        for (int attempt = 0; attempt < slots * 2 && wells.size() < wanted; attempt++) {
            int i = random.nextInt(2 * FIELD_REACH + 1) - FIELD_REACH;
            int j = random.nextInt(2 * FIELD_REACH + 1) - FIELD_REACH;
            if (!slotsTried.add(BlockPos.asLong(i, 0, j))) {
                continue;
            }
            int x = origin.getX() + i * WELL_SPACING;
            int z = origin.getZ() + j * WELL_SPACING;
            BlockPos ground = ground(level, x, z);
            if (ground == null) {
                continue;
            }
            levelAround(level, ground);
            level.setBlock(ground, ModBlocks.CRUDE_OIL.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            wells.add(ground);
        }
        return wells;
    }

    /**
     * The block a well at this column would replace: the highest solid ground, or null if the
     * column is water or has nothing to stand a machine on.
     */
    public static @Nullable BlockPos ground(WorldGenLevel level, int x, int z) {
        // MOTION_BLOCKING rather than a worldgen-only heightmap, because this also runs on a live
        // level - a test builds ground and asks for a field on it - and a live chunk keeps this
        // one up to date where it would have to prime a _WG one on demand. It stops at the first
        // thing that blocks motion, which for a tree is its canopy and for a lake is the water.
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        // Walk down through leaves and trunk to the earth they stand on; give up on water.
        while (pos.getY() > level.getMinY() && !isGround(level.getBlockState(pos))) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return null;
            }
            pos.move(0, -1, 0);
        }
        if (pos.getY() <= level.getMinY()) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return null;
        }
        // Not on top of another well, and not on a well's pad either.
        if (state.getBlock() instanceof CrudeOilBlock) {
            return null;
        }
        return pos.immutable();
    }

    /** Earth, stone, sand: something a pumpjack can stand on. Not a plant, not a tree, not a fluid. */
    private static boolean isGround(BlockState state) {
        return state.isSolid()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.LOGS)
                && !state.canBeReplaced();
    }

    /**
     * Flattens the eight blocks round a well to its level and clears the air a pumpjack needs.
     *
     * <p>Below the well's level nothing is touched. At its level a dip is filled with dirt. Above
     * it, up to {@link #HEADROOM}, anything is cleared - plants, leaves, a trunk, or the hillside
     * itself - so the machine's nine ground cells and its head have room.
     */
    public static void levelAround(WorldGenLevel level, BlockPos well) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean centre = dx == 0 && dz == 0;
                pos.set(well.getX() + dx, well.getY(), well.getZ() + dz);
                if (!centre && !isGround(level.getBlockState(pos))) {
                    level.setBlock(pos, dirt, Block.UPDATE_CLIENTS);
                }
                for (int dy = 1; dy <= HEADROOM; dy++) {
                    pos.set(well.getX() + dx, well.getY() + dy, well.getZ() + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
