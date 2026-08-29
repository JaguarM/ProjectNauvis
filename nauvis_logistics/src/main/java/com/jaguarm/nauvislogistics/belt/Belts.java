package com.jaguarm.nauvislogistics.belt;

import net.minecraft.core.Direction;

/**
 * The numbers a belt is made of, in one place.
 *
 * <h2>Why sixty-four</h2>
 *
 * <p>Distances along a run are whole numbers of a sixty-fourth of a block, never floats. A float
 * would drift: an item's place on a belt is arrived at by adding a step twenty times a second for
 * as long as the world exists, and it is saved to disk and sent to clients in between. Integers
 * add exactly, compare exactly and serialise exactly, and the belt's whole compression trick
 * depends on {@code slack == 0} meaning what it says. See {@link BeltLane}.
 *
 * <p>Sixty-four is chosen so that every number Factorio publishes lands on a whole one:
 *
 * <ul>
 *   <li>items sit a quarter of a tile apart, so {@link #SPACING} is 16 and a tile holds four
 *       items a lane, eight in all - Factorio's figure;</li>
 *   <li>a transport belt moves 1.875 tiles a second, which over twenty ticks is exactly 6 units a
 *       tick. The fast and express belts are exactly 12 and 18;</li>
 *   <li>and the throughput follows rather than being a second number to keep straight: four items
 *       a tile, two lanes, 1.875 tiles a second is the 15 items a second on the wiki.</li>
 * </ul>
 *
 * <p>The speeds themselves are identity and live in {@code data/mapping.json} beside the ids;
 * {@code tools/check_models.py} holds the constant in each belt block to the number recorded
 * there, so the figure is written down once and checked rather than remembered.
 */
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
