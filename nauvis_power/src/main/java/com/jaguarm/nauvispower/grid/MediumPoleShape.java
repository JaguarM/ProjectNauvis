package com.jaguarm.nauvispower.grid;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * One tile, five blocks tall: the small pole's post with one more shaft in it.
 *
 * <p>The extra block is the tier, seen. Reach is a number a player has to be told; standing a head
 * higher than everything around it is a thing they can see from across the base, and it is what
 * makes a line of medium poles read as a bus rather than as more of the same. Height is ours - see
 * ARCHITECTURE.md - which is exactly why it is free to spend on telling two tiers apart.
 */
public final class MediumPoleShape {

    private MediumPoleShape() {}

    /** Ties these cells to the {@code size} in {@code data/mapping.json}. One tile, so no entry. */
    public static final String FACTORIO_ID = "medium-electric-pole";

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
            new MachineCell(0, 3, 0, PoleBoxes.SHAFT, 0, PoleBoxes.POST),
            new MachineCell(0, 4, 0, PoleBoxes.HEAD, 0, PoleBoxes.HEAD_BOXES, PoleBoxes.POST)),
            FOOT, FOOT);
}
