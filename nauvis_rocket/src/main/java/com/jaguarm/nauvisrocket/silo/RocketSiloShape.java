package com.jaguarm.nauvisrocket.silo;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a rocket silo looks like and how much room it takes: nine tiles by nine, the size Factorio
 * made it, and the biggest thing in the pack by a distance.
 *
 * <h2>A pad with a rocket standing on it</h2>
 *
 * <p>Factorio's silo is a hole in the ground with doors that open for the launch. That is a
 * rendered animation on a machine nine tiles across, and this pack's machines are block models;
 * so the silo here is a pad, the shared shell nine wide with the family's wall and recessed floor,
 * and the rocket stands on a solid three-by-three deck in the middle of it. The rocket is drawn
 * whether or not a rocket has been built - the parts a silo holds are a number in its screen and
 * its readout - which is the shortcut {@code GAPS.md} names. Five blocks of body with the corners
 * chamfered, so it reads as round at a distance, and a stepped nose over that: seven tall, and
 * the tallest thing a factory has.
 *
 * <p>The floor inside the wall is walked over, which is the walkability rule of every machine
 * here; the deck and the rocket are the one thing to go round, and they leave three blocks of
 * floor on every side.
 *
 * <p>Every cell is written out rather than looped, all {@value #CELL_COUNT} of them, because a
 * cell's place in the list is its {@code part} value and {@code part} values are in world saves;
 * {@code tools/check_models.py} reads them back and holds the footprint to the {@code size} for
 * {@code rocket-silo} in {@code data/mapping.json}. <b>Do not reorder this list</b>, only append.
 */
public final class RocketSiloShape {

    private RocketSiloShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "rocket-silo";

    public static final int CELL_COUNT = 135;

    /** The rocket's body: a chamfered corner, a flat side, and the core nobody sees. */
    public static final String BODY_CORNER = "body_corner";
    public static final String BODY_SIDE = "body_side";
    public static final String BODY_CORE = "body_core";

    /** The nose: the same three places, stepping in to the tip. */
    public static final String NOSE_CORNER = "nose_corner";
    public static final String NOSE_EDGE = "nose_edge";
    public static final String NOSE_TIP = "nose_tip";

    /** The north-west corner of the body, with its outer corner cut off: a full block less a notch. */
    private static final float[][] BODY_CORNER_BOXES = {
        {4, 0, 4, 16, 16, 16},
        {0, 0, 8, 4, 16, 16},
        {8, 0, 0, 16, 16, 4},
    };

    private static final float[][] BODY_SIDE_BOXES = {
        {0, 0, 0, 16, 16, 16},
    };

    private static final float[][] BODY_CORE_BOXES = {
        {0, 0, 0, 16, 16, 16},
    };

    /** The north-west of the nose: low, and stepping in from the outer corner. */
    private static final float[][] NOSE_CORNER_BOXES = {
        {4, 0, 4, 16, 3, 16},
        {8, 3, 8, 16, 6, 16},
    };

    /** The north edge of the nose: a step up towards the tip behind it. */
    private static final float[][] NOSE_EDGE_BOXES = {
        {0, 0, 0, 16, 3, 16},
        {0, 3, 6, 16, 6, 16},
        {2, 6, 11, 14, 8, 16},
    };

    /** The tip, over the core: four steps to a point. */
    private static final float[][] NOSE_TIP_BOXES = {
        {0, 0, 0, 16, 4, 16},
        {2, 4, 2, 14, 9, 14},
        {5, 9, 5, 11, 13, 11},
        {7, 13, 7, 9, 16, 9},
    };

    /** The middle of the pad: the block that holds everything, and the block the click lands on. */
    public static final int MIDDLE = 40;

    public static final MachineShape SHAPE = build();

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // The pad, row by row from the north-west corner. The rocket stands on the deck in the
        // middle; everything else inside the wall is floor.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(3, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(4, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(5, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(6, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(7, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(8, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(4, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(5, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(6, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 1, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(4, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(5, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(6, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 2, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 2, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 3, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 3, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 3, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(5, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(6, 0, 3, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 3, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 3, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 4, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 4, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 4, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 4, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 4, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(5, 0, 4, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(6, 0, 4, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 4, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 4, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 5, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 5, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 5, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 5, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 5, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(5, 0, 5, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(6, 0, 5, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 5, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 5, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 6, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(4, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(5, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(6, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 6, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 6, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 7, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(2, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(3, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(4, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(5, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(6, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(7, 0, 7, MachineParts.FLOOR_CELL, 0, MachineParts.FLOOR_BOXES));
        cells.add(new MachineCell(8, 0, 7, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 8, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(3, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(4, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(5, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(6, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(7, 0, 8, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(8, 0, 8, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(3, 1, 3, BODY_CORNER, 0, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 1, 3, BODY_CORNER, 1, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 1, 5, BODY_CORNER, 2, BODY_CORNER_BOXES));
        cells.add(new MachineCell(3, 1, 5, BODY_CORNER, 3, BODY_CORNER_BOXES));
        cells.add(new MachineCell(4, 1, 3, BODY_SIDE, 0, BODY_SIDE_BOXES));
        cells.add(new MachineCell(5, 1, 4, BODY_SIDE, 1, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 1, 5, BODY_SIDE, 2, BODY_SIDE_BOXES));
        cells.add(new MachineCell(3, 1, 4, BODY_SIDE, 3, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 1, 4, BODY_CORE, 0, BODY_CORE_BOXES));
        cells.add(new MachineCell(3, 2, 3, BODY_CORNER, 0, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 2, 3, BODY_CORNER, 1, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 2, 5, BODY_CORNER, 2, BODY_CORNER_BOXES));
        cells.add(new MachineCell(3, 2, 5, BODY_CORNER, 3, BODY_CORNER_BOXES));
        cells.add(new MachineCell(4, 2, 3, BODY_SIDE, 0, BODY_SIDE_BOXES));
        cells.add(new MachineCell(5, 2, 4, BODY_SIDE, 1, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 2, 5, BODY_SIDE, 2, BODY_SIDE_BOXES));
        cells.add(new MachineCell(3, 2, 4, BODY_SIDE, 3, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 2, 4, BODY_CORE, 0, BODY_CORE_BOXES));
        cells.add(new MachineCell(3, 3, 3, BODY_CORNER, 0, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 3, 3, BODY_CORNER, 1, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 3, 5, BODY_CORNER, 2, BODY_CORNER_BOXES));
        cells.add(new MachineCell(3, 3, 5, BODY_CORNER, 3, BODY_CORNER_BOXES));
        cells.add(new MachineCell(4, 3, 3, BODY_SIDE, 0, BODY_SIDE_BOXES));
        cells.add(new MachineCell(5, 3, 4, BODY_SIDE, 1, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 3, 5, BODY_SIDE, 2, BODY_SIDE_BOXES));
        cells.add(new MachineCell(3, 3, 4, BODY_SIDE, 3, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 3, 4, BODY_CORE, 0, BODY_CORE_BOXES));
        cells.add(new MachineCell(3, 4, 3, BODY_CORNER, 0, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 4, 3, BODY_CORNER, 1, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 4, 5, BODY_CORNER, 2, BODY_CORNER_BOXES));
        cells.add(new MachineCell(3, 4, 5, BODY_CORNER, 3, BODY_CORNER_BOXES));
        cells.add(new MachineCell(4, 4, 3, BODY_SIDE, 0, BODY_SIDE_BOXES));
        cells.add(new MachineCell(5, 4, 4, BODY_SIDE, 1, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 4, 5, BODY_SIDE, 2, BODY_SIDE_BOXES));
        cells.add(new MachineCell(3, 4, 4, BODY_SIDE, 3, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 4, 4, BODY_CORE, 0, BODY_CORE_BOXES));
        cells.add(new MachineCell(3, 5, 3, BODY_CORNER, 0, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 5, 3, BODY_CORNER, 1, BODY_CORNER_BOXES));
        cells.add(new MachineCell(5, 5, 5, BODY_CORNER, 2, BODY_CORNER_BOXES));
        cells.add(new MachineCell(3, 5, 5, BODY_CORNER, 3, BODY_CORNER_BOXES));
        cells.add(new MachineCell(4, 5, 3, BODY_SIDE, 0, BODY_SIDE_BOXES));
        cells.add(new MachineCell(5, 5, 4, BODY_SIDE, 1, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 5, 5, BODY_SIDE, 2, BODY_SIDE_BOXES));
        cells.add(new MachineCell(3, 5, 4, BODY_SIDE, 3, BODY_SIDE_BOXES));
        cells.add(new MachineCell(4, 5, 4, BODY_CORE, 0, BODY_CORE_BOXES));
        cells.add(new MachineCell(3, 6, 3, NOSE_CORNER, 0, NOSE_CORNER_BOXES));
        cells.add(new MachineCell(5, 6, 3, NOSE_CORNER, 1, NOSE_CORNER_BOXES));
        cells.add(new MachineCell(5, 6, 5, NOSE_CORNER, 2, NOSE_CORNER_BOXES));
        cells.add(new MachineCell(3, 6, 5, NOSE_CORNER, 3, NOSE_CORNER_BOXES));
        cells.add(new MachineCell(4, 6, 3, NOSE_EDGE, 0, NOSE_EDGE_BOXES));
        cells.add(new MachineCell(5, 6, 4, NOSE_EDGE, 1, NOSE_EDGE_BOXES));
        cells.add(new MachineCell(4, 6, 5, NOSE_EDGE, 2, NOSE_EDGE_BOXES));
        cells.add(new MachineCell(3, 6, 4, NOSE_EDGE, 3, NOSE_EDGE_BOXES));
        cells.add(new MachineCell(4, 6, 4, NOSE_TIP, 0, NOSE_TIP_BOXES));

        return new MachineShape(cells, MIDDLE, MIDDLE);
    }
}
