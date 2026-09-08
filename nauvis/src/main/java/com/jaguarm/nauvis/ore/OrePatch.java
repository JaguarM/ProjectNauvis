package com.jaguarm.nauvis.ore;

/**
 * One ore patch: a rounded rectangle of ore a few layers thick, flat, at one height.
 *
 * <p>The footprint is a superellipse with a slow ripple round its edge, so it reads as a
 * rectangle with soft corners rather than a blob; {@code halfWidth} and {@code halfDepth} are
 * its half-axes along x and z. The layers run from {@code bottomY} up; the rim and a few columns
 * of the top layer are one short, which is what keeps it from looking machined.
 */
public record OrePatch(OreKind kind, int centreX, int centreZ, double halfWidth, double halfDepth,
        int bottomY, int layers, double phase1, double phase2, long salt) {

    /** The superellipse exponent: 2 is an ellipse, higher is squarer. */
    public static final double EXPONENT = 4;
    /** How far the ripple pushes the edge in and out, as a fraction of the half-axis. */
    public static final double RIPPLE = 0.12;
    /** The furthest the ripple reaches past the half-axes. */
    public static final double REACH = 1 + 1.5 * RIPPLE;

    /** Under one inside the patch, one at its edge, more outside. */
    public double edge(int x, int z) {
        double u = (x + 0.5 - centreX) / halfWidth;
        double v = (z + 0.5 - centreZ) / halfDepth;
        double angle = Math.atan2(v, u);
        double ripple = 1 + RIPPLE * (Math.sin(3 * angle + phase1) + 0.5 * Math.sin(5 * angle + phase2));
        return (Math.pow(Math.abs(u), EXPONENT) + Math.pow(Math.abs(v), EXPONENT)) / Math.pow(ripple, EXPONENT);
    }

    public boolean contains(int x, int z) {
        return edge(x, z) <= 1;
    }

    /** How many layers this column holds, from the bottom: none outside, never fewer than one inside. */
    public int height(int x, int z) {
        double edge = edge(x, z);
        if (edge > 1) {
            return 0;
        }
        double roll = ((OrePatches.hash(salt, x, z) >>> 11) & 0xFFFF) / 65536.0;
        int height = layers;
        if (edge > 0.7 ? roll < (edge - 0.7) / 0.3 : roll < 0.2) {
            height--;
        }
        return Math.max(1, height);
    }

    public int topY() {
        return bottomY + layers - 1;
    }

    public int minX() {
        return (int) Math.floor(centreX - halfWidth * REACH);
    }

    public int maxX() {
        return (int) Math.ceil(centreX + halfWidth * REACH);
    }

    public int minZ() {
        return (int) Math.floor(centreZ - halfDepth * REACH);
    }

    public int maxZ() {
        return (int) Math.ceil(centreZ + halfDepth * REACH);
    }

    public boolean touches(int minX, int minZ, int maxX, int maxZ) {
        return maxX() >= minX && minX() <= maxX && maxZ() >= minZ && minZ() <= maxZ;
    }

    /** How many columns hold ore. */
    public int columns() {
        int count = 0;
        for (int x = minX(); x <= maxX(); x++) {
            for (int z = minZ(); z <= maxZ(); z++) {
                if (contains(x, z)) {
                    count++;
                }
            }
        }
        return count;
    }
}
