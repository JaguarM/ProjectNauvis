package com.jaguarm.nauvismachines.machine.radar;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a radar looks like: three tiles by three, the size Factorio made it, with a dish on a
 * mast over the middle.
 *
 * <p>The ground is the assembler's - nine cells of the shared shell - and the one thing standing
 * on it is a mast with a dish tilted off it, three thin slabs stepping up towards the north,
 * which is as near to a tilted dish as a block model gets. {@code tools/check_models.py} holds
 * these cells to the {@code size} for {@code radar} in {@code data/mapping.json}.
 */
public final class RadarShape {

    private RadarShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "radar";

    /** The one model this machine adds to the shared shell. */
    public static final String DISH = "dish";

    /** A post, three slabs stepping up to the north for the dish, and the feed standing off its middle. */
    private static final float[][] DISH_BOXES = {
        {6, 0, 6, 10, 8, 10},
        {1, 8, 10, 15, 10, 16},
        {1, 10, 5, 15, 12, 11},
        {1, 12, 0, 15, 14, 6},
        {7, 14, 7, 9, 16, 9},
    };

    /**
     * The nine ground cells clockwise from north-west, then the dish.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle of the deck: the block that holds everything, and the block the click lands on. */
    public static final int MIDDLE = 8;

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
        cells.add(new MachineCell(1, 1, 1, DISH, 0, DISH_BOXES));

        return new MachineShape(cells, MIDDLE, MIDDLE);
    }
}
