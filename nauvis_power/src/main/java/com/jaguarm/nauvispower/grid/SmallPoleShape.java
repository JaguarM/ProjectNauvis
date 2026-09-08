package com.jaguarm.nauvispower.grid;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/** One tile, four blocks tall. */
public final class SmallPoleShape {

    private SmallPoleShape() {}

    /** Ties these cells to the {@code size} in {@code data/mapping.json}. One tile, so no entry. */
    public static final String FACTORIO_ID = "small-electric-pole";

    /** The foot: the block entity, the loot table, and the one the player clicks. */
    public static final int FOOT = 0;

    /**
     * Bottom to top.
     *
     * <p>The order fixes the {@code part} values, which are in world saves: <b>do not reorder this
     * list</b>, only append to it.
     */
    public static final MachineShape SHAPE = new MachineShape(List.of(
            new MachineCell(0, 0, 0, PoleBoxes.FOOT, 0, PoleBoxes.FOOT_BOXES),
            new MachineCell(0, 1, 0, PoleBoxes.SHAFT, 0, PoleBoxes.POST),
            new MachineCell(0, 2, 0, PoleBoxes.SHAFT, 0, PoleBoxes.POST),
            new MachineCell(0, 3, 0, PoleBoxes.HEAD, 0, PoleBoxes.HEAD_BOXES, PoleBoxes.POST)),
            FOOT, FOOT);
}
