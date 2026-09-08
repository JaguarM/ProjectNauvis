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
