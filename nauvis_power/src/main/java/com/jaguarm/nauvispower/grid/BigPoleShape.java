package com.jaguarm.nauvispower.grid;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * Two tiles by two, six blocks tall: a lattice tower rather than a post.
 *
 * <p>The footprint is Factorio's and so is not ours to change - see ARCHITECTURE.md. The height is
 * ours, and six is chosen so the three tiers read as a ladder from any distance: four, five, six,
 * and the last of them twice as wide as the other two.
 *
 * <p>Four legs, one per cell, because a two-by-two footprint has no middle - every cell is a
 * corner. So the whole tower is three models turned four ways each, which is what keeps the four
 * legs from drifting into four slightly different legs. The heads' arms meet across the seams to
 * make one closed ring; see {@link PoleBoxes#LEG_HEAD}.
 *
 * <p>Twenty-four blocks is a lot for one item, and it is the point: this is the pole you put up
 * once and run a bus off, not the one you dot around a field of drills.
 */
public final class BigPoleShape {

    private BigPoleShape() {}

    /** Ties these cells to the {@code size} in Project Nauvis's {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "big-electric-pole";

    /** The front-left foot: the block entity, the loot table, and the one the player clicks. */
    public static final int FOOT = 0;

    /**
     * Bottom to top, clockwise from the front left within each storey.
     *
     * <p>The order fixes the {@code part} values, which are in world saves: <b>do not reorder this
     * list</b>, only append to it.
     */
    public static final MachineShape SHAPE = new MachineShape(List.of(
            new MachineCell(0, 0, 0, PoleBoxes.FOOT, 0, PoleBoxes.LEG_FOOT),
            new MachineCell(1, 0, 0, PoleBoxes.FOOT, 1, PoleBoxes.LEG_FOOT),
            new MachineCell(1, 0, 1, PoleBoxes.FOOT, 2, PoleBoxes.LEG_FOOT),
            new MachineCell(0, 0, 1, PoleBoxes.FOOT, 3, PoleBoxes.LEG_FOOT),
            new MachineCell(0, 1, 0, PoleBoxes.SHAFT, 0, PoleBoxes.LEG),
            new MachineCell(1, 1, 0, PoleBoxes.SHAFT, 1, PoleBoxes.LEG),
            new MachineCell(1, 1, 1, PoleBoxes.SHAFT, 2, PoleBoxes.LEG),
            new MachineCell(0, 1, 1, PoleBoxes.SHAFT, 3, PoleBoxes.LEG),
            new MachineCell(0, 2, 0, PoleBoxes.SHAFT, 0, PoleBoxes.LEG),
            new MachineCell(1, 2, 0, PoleBoxes.SHAFT, 1, PoleBoxes.LEG),
            new MachineCell(1, 2, 1, PoleBoxes.SHAFT, 2, PoleBoxes.LEG),
            new MachineCell(0, 2, 1, PoleBoxes.SHAFT, 3, PoleBoxes.LEG),
            new MachineCell(0, 3, 0, PoleBoxes.SHAFT, 0, PoleBoxes.LEG),
            new MachineCell(1, 3, 0, PoleBoxes.SHAFT, 1, PoleBoxes.LEG),
            new MachineCell(1, 3, 1, PoleBoxes.SHAFT, 2, PoleBoxes.LEG),
            new MachineCell(0, 3, 1, PoleBoxes.SHAFT, 3, PoleBoxes.LEG),
            new MachineCell(0, 4, 0, PoleBoxes.SHAFT, 0, PoleBoxes.LEG),
            new MachineCell(1, 4, 0, PoleBoxes.SHAFT, 1, PoleBoxes.LEG),
            new MachineCell(1, 4, 1, PoleBoxes.SHAFT, 2, PoleBoxes.LEG),
            new MachineCell(0, 4, 1, PoleBoxes.SHAFT, 3, PoleBoxes.LEG),
            new MachineCell(0, 5, 0, PoleBoxes.HEAD, 0, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(1, 5, 0, PoleBoxes.HEAD, 1, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(1, 5, 1, PoleBoxes.HEAD, 2, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(0, 5, 1, PoleBoxes.HEAD, 3, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION)),
            FOOT, FOOT);
}
