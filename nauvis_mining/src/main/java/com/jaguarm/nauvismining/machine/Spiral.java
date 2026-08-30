package com.jaguarm.nauvismining.machine;

/**
 * Maps a 1-based index onto a square spiral in the XZ plane.
 *
 * <p>Index 1 is the centre, and each subsequent index walks one step around an
 * ever-widening square. The miner uses this to pick which column to dig next, so it
 * works outward from the machine rather than sweeping a rectangle. This preserves the
 * original mod's behaviour, where a partially-fuelled miner still clears a tidy area
 * centred on itself.
 *
 * <p>Ported from the 1.12.2 implementation's {@code BaseTileEntity.spiral}, minus the
 * facing-dependent rotation: the original rotated the spiral to match the block's
 * facing, which is invisible for a symmetric dig pattern.
 */
public final class Spiral {

    private Spiral() {}

    /** A spiral offset from the origin. */
    public record Offset(int x, int z) {}

    /**
     * @param n 1-based spiral index; {@code n = 1} is the centre
     * @return the offset from the spiral's centre at that index
     */
    public static Offset offset(int n) {
        int k = (int) Math.ceil((Math.sqrt(n) - 1) / 2);
        int t = 2 * k + 1;
        int m = t * t;
        t = t - 1;

        if (n >= m - t) {
            return new Offset(k - (m - n), -k);
        }
        m = m - t;

        if (n >= m - t) {
            return new Offset(-k, -k + (m - n));
        }
        m = m - t;

        if (n >= m - t) {
            return new Offset(-k + (m - n), k);
        }
        return new Offset(k, k - (m - n - t));
    }

    /**
     * Number of spiral indices needed to fill a square of the given radius.
     * A radius of 1 is the single centre column, radius 2 is 3x3, and so on.
     */
    public static int columnsForRadius(int radius) {
        int side = 2 * radius - 1;
        return side * side;
    }
}
