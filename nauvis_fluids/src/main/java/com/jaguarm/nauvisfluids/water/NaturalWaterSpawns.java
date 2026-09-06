package com.jaguarm.nauvisfluids.water;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Fish in the sea, after the sea stopped being {@code minecraft:water}.
 *
 * <p>Almost everything vanilla does with water asks the {@code #minecraft:water} tag, and natural
 * water is in it. Spawning is the exception that matters: cod, salmon, pufferfish, tropical fish,
 * squid, dolphins, glow squid and the nautilus each check for the water <em>block</em> by name
 * above the spot they would appear, and an ocean of ours would be empty. So each of those gets a
 * second spawn rule alongside vanilla's - the same rule, reading our block where vanilla reads
 * its own - through NeoForge's event, which ORs it with the original. Nothing is replaced: a
 * lake of poured water spawns exactly what it always did.
 *
 * <p>The rules are copied rather than called because the block name is inside each of them.
 * Guardians, drowned and axolotls ask the tag and need nothing.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class NaturalWaterSpawns {

    private NaturalWaterSpawns() {}

    @SubscribeEvent
    static void register(RegisterSpawnPlacementsEvent event) {
        event.register(EntityTypes.COD, NaturalWaterSpawns::nearTheSurface);
        event.register(EntityTypes.SALMON, NaturalWaterSpawns::nearTheSurface);
        event.register(EntityTypes.PUFFERFISH, NaturalWaterSpawns::nearTheSurface);
        event.register(EntityTypes.SQUID, NaturalWaterSpawns::nearTheSurface);
        event.register(EntityTypes.DOLPHIN, NaturalWaterSpawns::nearTheSurface);
        event.register(EntityTypes.TROPICAL_FISH, NaturalWaterSpawns::tropical);
        event.register(EntityTypes.GLOW_SQUID, NaturalWaterSpawns::deepAndDark);
        event.register(EntityTypes.NAUTILUS, NaturalWaterSpawns::belowTheSurface);
    }

    /** {@code WaterAnimal.checkSurfaceWaterAnimalSpawnRules}, in natural water. */
    static <T extends Entity> boolean nearTheSurface(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        int seaLevel = level.getSeaLevel();
        return pos.getY() >= seaLevel - 13
                && pos.getY() <= seaLevel
                && level.getFluidState(pos.below()).is(FluidTags.WATER)
                && natural(level, pos.above());
    }

    /** {@code TropicalFish.checkTropicalFishSpawnRules}, in natural water. */
    static <T extends Entity> boolean tropical(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER)
                && natural(level, pos.above())
                && (level.getBiome(pos).is(BiomeTags.ALLOWS_TROPICAL_FISH_SPAWNS_AT_ANY_HEIGHT)
                        || nearTheSurface(type, level, reason, pos, random));
    }

    /** {@code GlowSquid.checkGlowSquidSpawnRules}, in natural water. */
    static <T extends Entity> boolean deepAndDark(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return pos.getY() <= level.getSeaLevel() - 33
                && level.getRawBrightness(pos, 0) == 0
                && natural(level, pos);
    }

    /** {@code AbstractNautilus.checkNautilusSpawnRules}, in natural water. */
    static <T extends Entity> boolean belowTheSurface(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        int seaLevel = level.getSeaLevel();
        return pos.getY() >= seaLevel - 25
                && pos.getY() <= seaLevel - 5
                && level.getFluidState(pos.below()).is(FluidTags.WATER)
                && natural(level, pos.above());
    }

    private static boolean natural(ServerLevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.WATER.get());
    }
}
