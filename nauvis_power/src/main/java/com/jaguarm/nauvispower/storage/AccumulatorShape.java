package com.jaguarm.nauvispower.storage;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What an accumulator looks like and how much room it takes: two tiles by two, the size
 * Factorio made it, and one block high.
 */
public final class AccumulatorShape {

    private AccumulatorShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "accumulator";

    /** The one model, in the north-west corner's frame. */
    public static final String CELL = "cell";

    /** Body, lid and a quarter of the terminal, in the north-west corner's own frame. */
    private static final float[][] CELL_BOXES = {
        {0, 0, 0, 16, 10, 16},
        {1, 10, 1, 16, 14, 16},
        {6, 14, 6, 16, 16, 16},
    };

    /**
     * The four cells clockwise from north-west.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The north-west corner: the block that holds everything, and the block the click lands on. */
    public static final int ANCHOR = 0;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(0, 0, 0, CELL, 0, CELL_BOXES));
        cells.add(new MachineCell(1, 0, 0, CELL, 1, CELL_BOXES));
        cells.add(new MachineCell(1, 0, 1, CELL, 2, CELL_BOXES));
        cells.add(new MachineCell(0, 0, 1, CELL, 3, CELL_BOXES));
        // A two by two has no middle to centre on, so the clicked block is the north-west corner
        // and the machine grows east and south from it, as the furnaces do.
        return new MachineShape(cells, ANCHOR, ANCHOR);
    }
}
