package com.jaguarm.nauvismachines.machine.furnace;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What an electric furnace looks like: three tiles by three, the size Factorio made it, with a
 * hood over the middle.
 */
public final class ElectricFurnaceShape {

    private ElectricFurnaceShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "electric-furnace";

    /** The one model this machine adds to the shared shell. */
    public static final String HOOD = "hood";

    /** Three steps, each narrower than the last, and the top one is the one that glows. */
    private static final float[][] HOOD_BOXES = {
        {1, 0, 1, 15, 5, 15},
        {3, 5, 3, 13, 10, 13},
        {5, 10, 5, 11, 13, 11},
    };

    /**
     * The nine ground cells clockwise from north-west, then the hood.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle of the deck: the block that holds everything, and the block the click lands on. */
    public static final int MIDDLE = 8;

    /** The hood, above the middle. */
    public static final int HOOD_CELL = 9;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));

        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 1, 1, HOOD, 0, HOOD_BOXES));

        return new MachineShape(cells, MIDDLE, MIDDLE);
    }
}
