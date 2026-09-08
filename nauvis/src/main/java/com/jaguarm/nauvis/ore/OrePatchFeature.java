package com.jaguarm.nauvis.ore;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Draws this chunk's share of every ore patch that reaches into it. Once per chunk; the patches
 * are {@link OrePatches}' arithmetic on the seed, so neighbours agree without talking.
 */
public class OrePatchFeature extends Feature<NoneFeatureConfiguration> {

    public OrePatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.x() << 4;
        int minZ = chunk.z() << 4;
        int placed = 0;
        for (OrePatch patch : OrePatches.patchesTouching(level.getSeed(), chunk.x(), chunk.z())) {
            placed += place(level, patch, minX, minZ, minX + 15, minZ + 15);
        }
        return placed > 0;
    }

    /** The whole patch, wherever it reaches: for the command and the tests. */
    public static int place(WorldGenLevel level, OrePatch patch) {
        return place(level, patch, patch.minX(), patch.minZ(), patch.maxX(), patch.maxZ());
    }

    /**
     * The part of the patch inside these columns. Only ground becomes ore: a cave, water or air
     * is left as it is, so a patch cut by a ravine has a hole in it.
     */
    public static int place(WorldGenLevel level, OrePatch patch, int minX, int minZ, int maxX, int maxZ) {
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(minX, patch.minX()); x <= Math.min(maxX, patch.maxX()); x++) {
            for (int z = Math.max(minZ, patch.minZ()); z <= Math.min(maxZ, patch.maxZ()); z++) {
                int height = patch.height(x, z);
                int floor = patch.floorAt(x, z);
                for (int y = floor; y < floor + height; y++) {
                    if (y < level.getMinY() || y > level.getMaxY()) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState ore = patch.kind().oreFor(level.getBlockState(pos));
                    if (ore != null) {
                        level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }
}
