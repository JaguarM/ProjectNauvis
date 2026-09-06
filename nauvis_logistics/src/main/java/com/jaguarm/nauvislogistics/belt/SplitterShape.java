package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What a splitter looks like and how much room it takes: two tiles wide by one tile deep.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py} holds
 * this to the {@code size} recorded for {@code splitter} in {@code data/mapping.json}.
 *
 * <p>Two cells at ground level with a low hood across the middle spanning the two tracks.
 */
public final class SplitterShape {

    private SplitterShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "splitter";

    public static final String CELL_LEFT = "left";
    public static final String CELL_RIGHT = "right";

    /** Left cell: half-block belt deck with hood on the right half. */
    private static final float[][] LEFT_BOXES = {
        {0, 0, 0, 16, 8, 16},
        {2, 8, 3, 16, 12, 13},
    };

    /** Right cell: half-block belt deck with hood on the left half. */
    private static final float[][] RIGHT_BOXES = {
        {0, 0, 0, 16, 8, 16},
        {0, 8, 3, 14, 12, 13},
    };

    public static final int LEFT_TRACK = 0;
    public static final int RIGHT_TRACK = 1;

    public static final MachineShape SHAPE = build();

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(0, 0, 0, CELL_LEFT, 0, LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 0, CELL_RIGHT, 0, RIGHT_BOXES));

        return new MachineShape(cells, LEFT_TRACK, LEFT_TRACK);
    }
}
