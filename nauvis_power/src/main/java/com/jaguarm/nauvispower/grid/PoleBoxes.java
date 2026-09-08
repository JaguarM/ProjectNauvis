package com.jaguarm.nauvispower.grid;

/** The geometry every pole is built from, so the three tiers read as one family. */
public final class PoleBoxes {

    private PoleBoxes() {}

    /** Model names. Shared across the tiers, and the file each names is per block. */
    public static final String FOOT = "foot";
    public static final String SHAFT = "shaft";
    public static final String HEAD = "head";

    // --- the one-by-one pole: small and medium ------------------------------------------------

    /** Plain post. Height, and nothing else. */
    public static final float[][] POST = {
        {6, 0, 6, 10, 16, 10},
    };

    /** A flared base, so the pole looks planted rather than dropped. */
    public static final float[][] FOOT_BOXES = {
        {5, 0, 5, 11, 3, 11},
        {6, 3, 6, 10, 16, 10},
    };

    /**
     * Post, crossarm and cap, which is what makes it read as a power pole rather than a fence.
     *
     * <p>One arm each way at one height, so a pole needs no facing property and looks the same
     * from any angle - a distribution pole with a square crossarm, which is what Factorio's small
     * pole is. The wires attach here; see {@code PoleWireRenderer}.
     */
    public static final float[][] HEAD_BOXES = {
        {6, 0, 6, 10, 13, 10},
        {1, 10, 7, 15, 12, 9},
        {7, 10, 1, 9, 12, 7},
        {7, 10, 9, 9, 12, 15},
        {5, 13, 5, 11, 16, 11},
    };

    // --- the two-by-two pole: big -------------------------------------------------------------
    //
    // A lattice tower: four legs at the corners of the footprint, joined at the top by a square
    // ring. Every cell here is a corner - a two-by-two has no middle - so all four are one model
    // turned four ways, which is what stops them drifting into four slightly different legs.
    //
    // The boxes are written for the front-left cell, with the leg towards that cell's *outer*
    // corner. Turned three times, the four legs stand at the four corners of the footprint and the
    // tower is as wide as it is, rather than four posts huddled around the seam.

    /** One leg. Six pixels square, two in from the outside corner. */
    public static final float[][] LEG = {
        {2, 0, 2, 8, 16, 8},
    };

    /** The same leg on a footing. */
    public static final float[][] LEG_FOOT = {
        {1, 0, 1, 9, 3, 9},
        {2, 3, 2, 8, 16, 8},
    };

    /**
     * The leg, its cap, and two arms reaching to the cell edges the tower's other legs are past.
     *
     * <p>Each arm stops exactly at the seam and the neighbouring cell's arm carries on from there,
     * so the four cells make one closed square ring joining the four legs with nothing overlapping
     * anything. That is what a lattice tower's top actually looks like, and it is also the only
     * version that stays inside its own blocks.
     */
    public static final float[][] LEG_HEAD = {
        {2, 0, 2, 8, 13, 8},
        {8, 10, 3, 16, 12, 7},
        {3, 10, 8, 7, 12, 16},
        {1, 13, 1, 9, 16, 9},
    };

    /** What you bump into on a big pole: the leg, full height, and never the ring. */
    public static final float[][] LEG_COLLISION = {
        {2, 0, 2, 8, 16, 8},
    };
}
