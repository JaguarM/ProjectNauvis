package com.jaguarm.nauvismachines.machine.furnace;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a burner furnace looks like and how much room it takes: two tiles by two, the size
 * Factorio made the stone furnace and the steel furnace both, with a stack on one corner.
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
