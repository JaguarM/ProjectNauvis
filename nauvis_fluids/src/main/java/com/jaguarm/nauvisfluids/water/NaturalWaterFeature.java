package com.jaguarm.nauvisfluids.water;

import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Makes a chunk's water natural: every {@code minecraft:water} the generator put down becomes
 * {@code nauvis_fluids:water}, level for level.
 *
 * <p>Minecraft's water is placed by a dozen things - the sea and the rivers by the noise, caves
 * by the aquifers, springs and pools and clay patches by features, an ocean monument by its
 * pieces - and every one of them writes {@code Blocks.WATER} by name. Replacing the noise's
 * default fluid would catch the sea and miss the rest. So this runs once per chunk, in the last
 * decoration step after everything else has had its say, and reads the chunk back rather than
 * asking anybody: whatever is water is made ours.
 *
 * <h2>Straight into the sections</h2>
 *
 * <p>An ocean chunk is tens of thousands of water blocks, and putting each through
 * {@code setBlock} would cost more than the rest of the chunk's generation together - heightmaps,
 * light and neighbour bookkeeping, per block, for a swap that changes none of them. Both blocks
 * are liquids with the same light and the same motion, so every heightmap answers the same
 * before and after, and the swap is done where the blocks are kept: a palette check per section,
 * which is a handful of comparisons, and a walk of the sections that have any. The chunk's own
 * post-processing then ticks every fluid it generated, ours included, so a spring still starts
 * to flow.
 *
 * <p>Waterlogged blocks are not touched, because they cannot be: the water inside a kelp stalk or
 * a shipwreck's stairs is hard-coded to vanilla's fluid, block by block. Breaking one leaves a
 * block of vanilla water standing in the sea - which a bucket lifts exactly as before, and which
 * no pump draws from, so the rule this exists for holds either way. See {@code GAPS.md}.
 *
 * <p>Placed with no modifiers, so it lands once at each chunk's origin, and added to every
 * overworld biome by {@code data/nauvis_fluids/neoforge/biome_modifier/natural_water.json}.
 */
public class NaturalWaterFeature extends Feature<NoneFeatureConfiguration> {

    /** A section is sixteen blocks on a side. */
    private static final int SECTION_SIZE = 16;

    public NaturalWaterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos origin = context.origin();
        ChunkAccess chunk = context.level().getChunk(
                SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()));
        return replace(chunk) > 0;
    }

    /**
     * Swaps every vanilla water block in the chunk for natural water, and says how many.
     *
     * <p>Separated from {@link #place} so that a test can hand it a chunk of a live level: a
     * loaded chunk's sections are the same objects a generating one's are.
     */
    public static int replace(ChunkAccess chunk) {
        int replaced = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(NaturalWaterFeature::isVanillaWater)) {
                continue;
            }
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    for (int x = 0; x < SECTION_SIZE; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isVanillaWater(state)) {
                            section.setBlockState(x, y, z, natural(state));
                            replaced++;
                        }
                    }
                }
            }
        }
        return replaced;
    }

    private static boolean isVanillaWater(BlockState state) {
        return state.is(Blocks.WATER);
    }

    /**
     * The same water, ours. The level comes across, so a source stays a source and a waterfall
     * stays a waterfall.
     */
    public static BlockState natural(BlockState vanillaWater) {
        return ModBlocks.WATER.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, vanillaWater.getValue(LiquidBlock.LEVEL));
    }
}
