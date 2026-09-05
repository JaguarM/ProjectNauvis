package com.jaguarm.nauvisfluids.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How rich an oil well is, given where it is.
 *
 * <p>Factorio's placement puts oil in <em>fields</em> of a few wells each, none of them in the
 * starting area, and richer the further out they are. The exact curve lives in its resource
 * autoplace noise program, which this pack does not have; what it does have is the two numbers
 * Wube's prototype states in the open, and the shape of the rule:
 *
 * <ul>
 *   <li>{@code additional_richness = 220000} is added to every oil spot regardless of distance,
 *       which is why a fresh field near the start is a little under 100% and not a trickle;
 *   <li>and richness is multiplied by {@code max((D + d) / 2D, 1)} where {@code d} is the
 *       distance from the start and {@code D} is 1300 tiles: flat out to 1300, then linear, so a
 *       well at 3900 is twice as rich as the same well at 1300.
 * </ul>
 *
 * <p>The spread on top of the 220000 is this pack's approximation of the density term, chosen so
 * that a well at the edge of the starting area reads between about 90% and 200%, which is what a
 * Factorio player finds there. It is placement behaviour, not identity - see {@code GAPS.md} -
 * and the two Factorio constants above are the part that must not drift.
 *
 * <p>Everything here is a pure function of the world seed and the well's position, so a well
 * needs nothing written into it by worldgen and a creative-placed well is exactly as rich as a
 * generated one at that spot would be.
 */
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
