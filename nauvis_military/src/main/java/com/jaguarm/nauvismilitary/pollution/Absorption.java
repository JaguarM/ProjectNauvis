package com.jaguarm.nauvismilitary.pollution;

import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/** What the ground under a chunk takes back each minute, by what the ground is. */
public final class Absorption {

    private Absorption() {}

    public static final double FOREST = PollutionState.ABSORB_PER_MINUTE * 3;
    public static final double WATER = PollutionState.ABSORB_PER_MINUTE * 1.5;
    public static final double BARE = PollutionState.ABSORB_PER_MINUTE * 0.2;

    /** The absorption of every chunk of this level, for {@link PollutionState#drift}. */
    public static ToDoubleFunction<ChunkPos> of(ServerLevel level) {
        return chunk -> at(level, chunk);
    }

    public static double at(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x(), chunk.z())) {
            return PollutionState.ABSORB_PER_MINUTE;
        }
        return of(level.getBiome(chunk.getMiddleBlockPosition(level.getSeaLevel())));
    }

    /** What a chunk of this biome takes a minute. */
    public static double of(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA)) {
            return FOREST;
        }
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_BEACH)) {
            return BARE;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            return WATER;
        }
        return PollutionState.ABSORB_PER_MINUTE;
    }
}
