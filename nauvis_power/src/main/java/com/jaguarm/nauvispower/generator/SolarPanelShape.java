package com.jaguarm.nauvispower.generator;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a solar panel looks like and how much room it takes: three tiles by three, the size
 * Factorio made it, and half a block high.
 */
public final class SolarPanelShape {

    private SolarPanelShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "solar-panel";

    /** The one model. */
    public static final String PANEL = "panel";

    /** A frame to seven pixels, and the glass a pixel in from every edge on top of it. */
    private static final float[][] PANEL_BOXES = {
        {0, 0, 0, 16, 7, 16},
        {1, 7, 1, 15, 8, 15},
    };

    /**
     * The nine cells, north row first and west to east.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle: the block that holds everything, and the block the player's click lands on. */
    public static final int CENTRE = 4;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(0, 0, 0, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(1, 0, 0, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(2, 0, 0, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(0, 0, 1, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(1, 0, 1, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(2, 0, 1, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(0, 0, 2, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(1, 0, 2, PANEL, 0, PANEL_BOXES));
        cells.add(new MachineCell(2, 0, 2, PANEL, 0, PANEL_BOXES));
        return new MachineShape(cells, CENTRE, CENTRE);
    }
}
