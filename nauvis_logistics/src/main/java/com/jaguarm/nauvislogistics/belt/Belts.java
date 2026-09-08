package com.jaguarm.nauvislogistics.belt;

import net.minecraft.core.Direction;

/** The numbers a belt is made of, in one place. */
public final class Belts {

    private Belts() {}

    /** Units of distance in one block. See the class note - this is why the numbers come out whole. */
    public static final int UNITS_PER_BLOCK = 64;

    /** How much room one item takes: a quarter of a tile, so four items a tile a lane. */
    public static final int SPACING = UNITS_PER_BLOCK / 4;

    /** The two lanes, left and right of the direction of travel. They never mix. */
    public static final int LANES = 2;
    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    /**
     * How high a belt stands.
     *
     * <p>Half a block, which is under vanilla's 0.6 step height, so a player walks across a belt
     * rather than jumping onto it. A belt you have to jump is not a belt - see the walkability
     * table in {@code docs/ARCHITECTURE.md}.
     */
    public static final double HEIGHT = 0.5;

    /** How far a lane sits from the middle of the belt: a quarter of a tile, as in Factorio. */
    public static final double LANE_OFFSET = 0.25;

    /** Which lane an item arriving from {@code side} belongs on, given the belt's direction. */
    public static int laneFor(Direction travel, Direction side) {
        if (side == travel.getCounterClockWise()) {
            return LEFT;
        }
        if (side == travel.getClockWise()) {
            return RIGHT;
        }
        return -1;
    }

    /** The side of the belt a given lane runs along. */
    public static Direction sideOf(Direction travel, int lane) {
        return lane == LEFT ? travel.getCounterClockWise() : travel.getClockWise();
    }
}
