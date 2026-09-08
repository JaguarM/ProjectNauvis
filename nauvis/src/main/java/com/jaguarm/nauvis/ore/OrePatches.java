package com.jaguarm.nauvis.ore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.RandomSource;

/**
 * Where the patches are, worked out from the seed: three starting patches, one of each ore, and a
 * random patch in some of the cells of a grid beyond the starting area. Every chunk asks the same
 * arithmetic, so a patch that spans chunks is drawn the same by each of them.
 *
 * <p>Factorio's starting patches are thirty to forty tiles across and a patch's footprint stays
 * that size with distance while its richness grows; the pack's are a little smaller, and the
 * richness is Crumbling Ore's harvests per block, which the pack's config scales by the same
 * distance rule. Factorio places about 2.5 patches of each ore per square kilometre; with no map
 * to find them on, the pack places about five times that.
 */
public final class OrePatches {

    private OrePatches() {}

    /** Factorio's starting patches are thirty to forty across; a first look found that too big here. */
    public static final int LONG_AXIS_MIN = 20;
    public static final int LONG_AXIS_MAX = 30;
    /** The most the floor tilts, in blocks per block along an axis: three or so across a patch. */
    public static final double MAX_SLOPE = 0.2;
    public static final int WAVELENGTH_MIN = 10;
    public static final int WAVELENGTH_MAX = 20;
    /** The short axis as a fraction of the long one: kinda rectangular, never a square or a stripe. */
    public static final double ASPECT_MIN = 0.6;
    public static final double ASPECT_MAX = 0.85;
    public static final int LAYERS_MIN = 2;
    public static final int LAYERS_MAX = 3;
    /** The bottom layer of a random patch: anywhere from the deepslate to just under the plains. */
    public static final int DEPTH_MIN = -40;
    public static final int DEPTH_MAX = 48;
    /** The starting patches sit higher, so the first hour is spent building rather than digging. */
    public static final int STARTING_DEPTH_MIN = 16;
    public static final int STARTING_DEPTH_MAX = 48;
    /** Factorio's internal starting area: the starting patches are inside it and nothing else is. */
    public static final int STARTING_AREA = 160;
    public static final int STARTING_NEAR = 48;
    public static final int STARTING_FAR = 120;
    /** The grid of random patches, in blocks: a cell holds one patch or none. */
    public static final int CELL = 128;
    public static final double PATCH_CHANCE = 0.6;
    /** The furthest a patch reaches from its centre, whichever way. */
    public static final int REACH = (int) Math.ceil(LONG_AXIS_MAX / 2.0 * OrePatch.REACH);

    private static final long STARTING_SALT = 0x5741525449_4E47L;
    private static final long CELL_SALT = 0x43454C4CL;

    /** A well-mixed 64 bits from three: the seed for a cell, a patch, a column. */
    public static long hash(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    /** A patch of the usual proportions, drawn from {@code random}, centred and bottomed where told. */
    public static OrePatch patch(RandomSource random, OreKind kind, int centreX, int centreZ, int bottomY, int layers) {
        double longAxis = LONG_AXIS_MIN + random.nextDouble() * (LONG_AXIS_MAX - LONG_AXIS_MIN);
        double shortAxis = longAxis * (ASPECT_MIN + random.nextDouble() * (ASPECT_MAX - ASPECT_MIN));
        boolean alongX = random.nextBoolean();
        OrePatch.Relief relief = new OrePatch.Relief(
                (random.nextDouble() * 2 - 1) * MAX_SLOPE, (random.nextDouble() * 2 - 1) * MAX_SLOPE,
                WAVELENGTH_MIN + random.nextDouble() * (WAVELENGTH_MAX - WAVELENGTH_MIN),
                WAVELENGTH_MIN + random.nextDouble() * (WAVELENGTH_MAX - WAVELENGTH_MIN),
                random.nextDouble() * 2 * Math.PI, random.nextDouble() * 2 * Math.PI);
        return new OrePatch(kind, centreX, centreZ,
                (alongX ? longAxis : shortAxis) / 2, (alongX ? shortAxis : longAxis) / 2,
                bottomY, layers, random.nextDouble() * 2 * Math.PI, random.nextDouble() * 2 * Math.PI, relief);
    }

    /**
     * The three starting patches: one of each ore, a third of a turn apart, between
     * {@link #STARTING_NEAR} and {@link #STARTING_FAR} blocks from the origin, three layers thick.
     */
    public static List<OrePatch> startingPatches(long seed) {
        RandomSource random = RandomSource.create(hash(seed, STARTING_SALT, 0));
        double base = random.nextDouble() * 2 * Math.PI;
        OreKind[] kinds = OreKind.values();
        List<OrePatch> patches = new ArrayList<>(kinds.length);
        for (int i = 0; i < kinds.length; i++) {
            double angle = base + i * 2 * Math.PI / kinds.length + (random.nextDouble() - 0.5) * Math.PI / 6;
            double distance = STARTING_NEAR + random.nextDouble() * (STARTING_FAR - STARTING_NEAR);
            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);
            int bottom = STARTING_DEPTH_MIN + random.nextInt(STARTING_DEPTH_MAX - STARTING_DEPTH_MIN + 1);
            patches.add(patch(random, kinds[i], x, z, bottom, LAYERS_MAX));
        }
        return patches;
    }

    /** The random patch in this cell of the grid, if it has one and it is outside the starting area. */
    public static Optional<OrePatch> patchInCell(long seed, int cellX, int cellZ) {
        RandomSource random = RandomSource.create(hash(seed ^ CELL_SALT, cellX, cellZ));
        if (random.nextDouble() >= PATCH_CHANCE) {
            return Optional.empty();
        }
        // The middle half of the cell, so two neighbours' patches cannot meet.
        int x = cellX * CELL + CELL / 4 + random.nextInt(CELL / 2);
        int z = cellZ * CELL + CELL / 4 + random.nextInt(CELL / 2);
        if (Math.hypot(x, z) < STARTING_AREA) {
            return Optional.empty();
        }
        OreKind kind = OreKind.values()[random.nextInt(OreKind.values().length)];
        int bottom = DEPTH_MIN + random.nextInt(DEPTH_MAX - DEPTH_MIN + 1);
        int layers = LAYERS_MIN + random.nextInt(LAYERS_MAX - LAYERS_MIN + 1);
        return Optional.of(patch(random, kind, x, z, bottom, layers));
    }

    /** Every patch with a column in this chunk. */
    public static List<OrePatch> patchesTouching(long seed, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        List<OrePatch> patches = new ArrayList<>();
        if (Math.min(Math.abs(minX), Math.abs(maxX)) <= STARTING_FAR + REACH
                && Math.min(Math.abs(minZ), Math.abs(maxZ)) <= STARTING_FAR + REACH) {
            for (OrePatch patch : startingPatches(seed)) {
                if (patch.touches(minX, minZ, maxX, maxZ)) {
                    patches.add(patch);
                }
            }
        }
        for (int cellX = Math.floorDiv(minX - REACH, CELL); cellX <= Math.floorDiv(maxX + REACH, CELL); cellX++) {
            for (int cellZ = Math.floorDiv(minZ - REACH, CELL); cellZ <= Math.floorDiv(maxZ + REACH, CELL); cellZ++) {
                patchInCell(seed, cellX, cellZ)
                        .filter(patch -> patch.touches(minX, minZ, maxX, maxZ))
                        .ifPresent(patches::add);
            }
        }
        return patches;
    }
}
