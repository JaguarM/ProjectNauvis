package com.jaguarm.nauvismilitary.pollution;

import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/**
 * What the ground under a chunk takes back each minute, by what the ground is.
 *
 * <p>Factorio's absorption is per tile, and trees take far more than grass, grass more than
 * sand, and water nothing much. Reading every block of every polluted chunk once a minute is
 * more than the model is worth; the biome at the chunk's middle is one lookup and says the same
 * thing at chunk scale - a forest chunk is trees, a desert chunk is sand. So a forest, a jungle or
 * a taiga takes three times the flat figure, a desert or badlands a fifth of it, water a little
 * more than the flat, and everything else the flat five that the model began with. A chunk that
 * is not loaded takes the flat figure, since nobody can say what is under it.
 *
 * <p>Numbers, not identity: they are the knobs {@code PLAN.md}'s military note says a playtest
 * moves. What is identity is that planting trees now does something for the air.
 */
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
