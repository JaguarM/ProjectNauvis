package com.jaguarm.nauvisfluids.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** How rich an oil well is, given where it is. */
public final class CrudeOilField {

    private CrudeOilField() {}

    /** Factorio's {@code additional_richness} for crude oil: added to every well. */
    public static final long ADDITIONAL_RICHNESS = 220_000;

    /**
     * The spread the density term adds on top, before the distance factor. Ninety to two hundred
     * percent of a normal well when added to {@link #ADDITIONAL_RICHNESS}.
     */
    public static final long SPREAD_MIN = 50_000;
    public static final long SPREAD_MAX = 380_000;

    /** Factorio's {@code double_density_distance}: where the distance factor begins to climb. */
    public static final double DOUBLE_DENSITY_DISTANCE = 1300.0;

    /**
     * Factorio's richness-by-distance factor, {@code max((D + d) / 2D, 1)}.
     *
     * @param distance blocks from the world origin, which is where a Factorio map starts
     */
    public static double distanceFactor(double distance) {
        return Math.max((DOUBLE_DENSITY_DISTANCE + distance) / (2 * DOUBLE_DENSITY_DISTANCE), 1.0);
    }

    /** How far this position is from the world origin, horizontally. */
    public static double distance(BlockPos pos) {
        return Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
    }

    /**
     * The amount a well at {@code pos} starts with, in a world with this seed.
     *
     * <p>A multiple of ten, because a pumpjack cycle takes ten and Factorio's own amounts are
     * multiples of the depletion step.
     */
    public static long initialAmount(long seed, BlockPos pos) {
        RandomSource random = RandomSource.create(seed ^ Mth.getSeed(pos));
        long spread = SPREAD_MIN + (long) (random.nextDouble() * (SPREAD_MAX - SPREAD_MIN));
        double amount = (ADDITIONAL_RICHNESS + spread) * distanceFactor(distance(pos));
        return Math.round(amount / CrudeOilBlockEntity.DEPLETION) * CrudeOilBlockEntity.DEPLETION;
    }
}
