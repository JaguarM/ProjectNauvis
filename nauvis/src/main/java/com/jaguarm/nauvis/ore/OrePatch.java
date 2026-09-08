package com.jaguarm.nauvis.ore;

/**
 * One ore patch: a rounded rectangle of solid ore a few layers thick, lying on a floor that tilts
 * and rolls a little across the patch.
 *
 * <p>The footprint is a superellipse with a slow ripple round its edge, so it reads as a
 * rectangle with soft corners rather than a blob; {@code halfWidth} and {@code halfDepth} are
 * its half-axes along x and z. The floor is {@code bottomY} at the centre plus the {@link Relief}:
 * a tilt along each axis and a long wave along each, so a patch is not a slab cut by a plane.
 * The rim is one layer thinner, which softens the edge; there are no holes.
 */
public record OrePatch(OreKind kind, int centreX, int centreZ, double halfWidth, double halfDepth,
        int bottomY, int layers, double phase1, double phase2, Relief relief) {

    /** How the floor moves across the patch: a slope in blocks per block along each axis, and a wave along each. */
    public record Relief(double slopeX, double slopeZ, double wavelengthX, double wavelengthZ, double phaseX, double phaseZ) {
        public static final Relief FLAT = new Relief(0, 0, 16, 16, 0, 0);
    }

    /** The superellipse exponent: 2 is an ellipse, higher is squarer. */
    public static final double EXPONENT = 4;
    /** How far the ripple pushes the edge in and out, as a fraction of the half-axis. */
    public static final double RIPPLE = 0.12;
    /** The furthest the ripple reaches past the half-axes. */
    public static final double REACH = 1 + 1.5 * RIPPLE;
    /** Each of the two waves lifts or drops the floor this much at most. */
    public static final double WAVE = 1.0;

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

    /** How many layers this column holds: none outside, one fewer on the rim, never fewer than one inside. */
    public int height(int x, int z) {
        double edge = edge(x, z);
        if (edge > 1) {
            return 0;
        }
        return edge > 0.8 ? Math.max(1, layers - 1) : layers;
    }

    /** The lowest ore block of this column. */
    public int floorAt(int x, int z) {
        double dx = x + 0.5 - centreX;
        double dz = z + 0.5 - centreZ;
        double lift = relief.slopeX() * dx + relief.slopeZ() * dz
                + WAVE * Math.sin(2 * Math.PI * dx / relief.wavelengthX() + relief.phaseX())
                + WAVE * Math.sin(2 * Math.PI * dz / relief.wavelengthZ() + relief.phaseZ());
        return bottomY + (int) Math.round(lift);
    }

    /** The most the floor can leave {@code bottomY} by, either way. */
    public int maxRelief() {
        return (int) Math.ceil(Math.abs(relief.slopeX()) * halfWidth * REACH
                + Math.abs(relief.slopeZ()) * halfDepth * REACH + 2 * WAVE);
    }

    public int minY() {
        return bottomY - maxRelief();
    }

    public int maxY() {
        return bottomY + layers - 1 + maxRelief();
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
