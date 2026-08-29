package com.jaguarm.nauvismachines.multiblock;

/**
 * The shell every machine in the pack is built out of, so they read as one family.
 *
 * <p>A machine is a housing with a wall round the outside and a recessed floor inside it, and
 * whatever makes it that machine stands in the middle. The wall, the floor and the corner where
 * the wall turns are here; the gearbox, the chimney and the flywheel are not, because those are
 * what tells an assembler from a boiler.
 *
 * <p>This file is duplicated into every mod that has machines, and
 * {@code tools/check_duplicated.py} holds the copies to being identical. That is on purpose and it
 * is the point: a boiler that grew a slightly different wall from an assembler would look like a
 * mistake long before anyone could say which of the two was wrong. Compare
 * {@code ../NeoProgressiveAutomation/texture-workshop/}, where sixteen textures come off three
 * shared maps for the same reason - a family cannot drift apart if there is only one of it.
 *
 * <h2>The heights are the walkability rule</h2>
 *
 * <p>Wall at 1.0, floor at 0.75. A player jumps about 1.25 and steps 0.6 for free, so the wall is
 * one climb and everything after it is walking. That is what lets machines be packed edge to edge
 * without walling the player out of their own base - see {@code docs/ARCHITECTURE.md}. Anything a machine
 * puts in the middle is the part you walk around, so put it where tiled machines leave a lane.
 */
public final class MachineParts {

    private MachineParts() {}

    /** Model names, shared so a blockstate reads the same from machine to machine. */
    public static final String DECK = "deck";
    public static final String EDGE = "edge";
    public static final String CORNER = "corner";

    /** How high you stand when you are on a machine, in pixels. */
    public static final int FLOOR = 12;

    /** How high its outer wall stands. A full block, so the climb is one jump and no more. */
    public static final int WALL = 16;

    /**
     * A full block: what goes under whatever the machine puts in the middle.
     *
     * <p>Full height rather than the recessed floor around it, so the thing standing on it meets
     * something solid. A quarter-block gap under a machine's own mechanism is the sort of thing
     * you only notice once you are next to it.
     */
    public static final float[][] DECK_BOXES = {
        {0, 0, 0, 16, WALL, 16},
    };

    /**
     * A recessed floor with the outer wall along the north edge.
     *
     * <p>Nothing here leaves the block it belongs to, which is worth saying because the first
     * version of this did: the wall stood proud at {@code y 16..20} and reached into the air block
     * above. {@code tools/check_models.py} refused it - an element outside {@code 0..16} takes its
     * texture coordinates from its own position, so the wall would have been drawn with the
     * texture running off the end. Keeping it inside the block is the simpler fix and it reads
     * better anyway.
     */
    public static final float[][] EDGE_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, 16, WALL, 3},
    };

    /**
     * The same floor with the wall turning a corner.
     *
     * <p>The two wall boxes meet rather than overlap - faces sharing a plane and pointing the same
     * way z-fight, which looks like the model flickering.
     */
    public static final float[][] CORNER_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, 16, WALL, 3},
        {0, FLOOR, 3, 3, WALL, 16},
    };
}
