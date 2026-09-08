package com.jaguarm.nauvisresearch.lab;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a lab looks like and how much room it takes: three tiles by three, the size Factorio
 * made it.
 */
public final class LabShape {

    private LabShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "lab";

    /** The one model this machine adds to the shared shell. */
    public static final String DOME = "dome";

    /**
     * A stepped dome. Three courses, each narrower than the last.
     *
     * <p>Steps rather than a taper, because a block model has no curves and a stack of squares
     * reading as a dome is the oldest trick in Minecraft's own art.
     */
    private static final float[][] DOME_BOXES = {
        {1, 0, 1, 15, 5, 15},
        {3, 5, 3, 13, 11, 13},
        {5, 11, 5, 11, 16, 11},
    };

    /**
     * The nine ground cells, then the dome.
     *
     * <p>Every cell is written out rather than looped: a cell's place in this list is its
     * {@code part} value, and {@code part} values are in world saves. <b>Do not reorder this
     * list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle of the floor: the block that holds everything, and the block you click. */
    public static final int MIDDLE = 8;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Corners, clockwise from north-west. One model, four turns.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        // Edge middles, clockwise from north.
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));

        // The middle of the floor - index 8, see MIDDLE - and the dome standing on it.
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 1, 1, DOME, 0, DOME_BOXES));

        return new MachineShape(cells, MIDDLE, MIDDLE);
    }
}
