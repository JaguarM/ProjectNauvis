package com.jaguarm.nauvismachines.machine.furnace;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a burner furnace looks like and how much room it takes: two tiles by two, the size
 * Factorio made the stone furnace and the steel furnace both, with a stack on one corner.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py}
 * holds this to the {@code size} recorded for {@code stone-furnace} in {@code data/mapping.json}.
 * The steel furnace is the same two by two and shares this shape; the two differ in what they are
 * made of and how fast they run, which is the block's business.
 *
 * <h2>Five blocks: a hearth you can walk over, and a stack you walk round</h2>
 *
 * <p>Four corner cells of the shared shell make a walled square with a recessed floor - the
 * hearth - and the stack stands on the north-west corner. A column of furnaces with belts down
 * either side is the most-built thing in Factorio, and here it has to stay crossable: the hearths
 * are the shell's wall-and-floor, so a column is climbed onto once and walked along, and the stacks
 * stand two apart with a gap between each pair.
 *
 * <p>The stack is where the fire shows. Its top is drawn in lava while the furnace is lit, which
 * is the one thing a furnace column needs to say from across a base: which ones are working.
 */
public final class FurnaceShape {

    private FurnaceShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "stone-furnace";

    /** The one model this machine adds to the shared shell. */
    public static final String STACK = "stack";

    /** A short chimney with a wide mouth, standing on the corner cell under it. */
    private static final float[][] STACK_BOXES = {
        {3, 0, 3, 13, 10, 13},
        {2, 10, 2, 14, 13, 14},
    };

    /**
     * The four ground cells clockwise from north-west, then the stack.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The north-west corner: the block that holds everything, and the block the click lands on. */
    public static final int HEARTH = 0;

    /** The stack, above the hearth. */
    public static final int STACK_CELL = 4;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Corners, clockwise from north-west. One model, four turns.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        cells.add(new MachineCell(0, 1, 0, STACK, 0, STACK_BOXES));

        // A two by two has no middle to centre on, so the clicked block is the north-west corner
        // and the furnace grows east and south from it - which is where Factorio's own ghost
        // puts a two-tile machine relative to the cursor.
        return new MachineShape(cells, HEARTH, HEARTH);
    }
}
