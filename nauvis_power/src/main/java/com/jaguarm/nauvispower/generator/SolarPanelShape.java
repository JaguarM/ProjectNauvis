package com.jaguarm.nauvispower.generator;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvispower.multiblock.MachineCell;
import com.jaguarm.nauvispower.multiblock.MachineShape;

/**
 * What a solar panel looks like and how much room it takes: three tiles by three, the size
 * Factorio made it, and half a block high.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py}
 * holds this to the {@code size} recorded for {@code solar-panel} in {@code data/mapping.json}.
 * The height is ours, and it is the walkability table's lowest row: <b>0.5, crossed without a
 * jump</b>. A solar field in Factorio is something you walk straight across, and a field of these
 * is too - nine panels tile into a floor you would not notice you were on.
 *
 * <h2>Nine cells, one model</h2>
 *
 * <p>Every cell is the same framed panel: a slab to seven pixels with the glass inset a pixel on
 * top, so a field reads as panels in frames rather than as a sheet of blue. No walls, no deck, no
 * mechanism, because there is nothing to walk round - which is why this is not on the
 * {@code MachineParts} shell that every other machine wears.
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
