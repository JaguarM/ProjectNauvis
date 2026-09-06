package com.jaguarm.nauvispower.grid;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * Two tiles by two, five blocks tall: the same tower as the big pole, one storey shorter.
 *
 * <p>The footprint is Factorio's. The height is ours, and being <em>under</em> the big pole rather
 * than over it is the point: a substation is a squat thing that covers ground, and a big pole is a
 * tall thing that spans it. Standing them side by side should say which is which before the player
 * has read either tooltip.
 *
 * <p>It shares every box with {@link BigPoleShape} - same legs, same ring - so the two read as the
 * same family of tower, which they are. What tells them apart is one storey and the metal.
 */
public final class SubstationShape {

    private SubstationShape() {}

    /** Ties these cells to the {@code size} in Project Nauvis's {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "substation";

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
            new MachineCell(0, 4, 0, PoleBoxes.HEAD, 0, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(1, 4, 0, PoleBoxes.HEAD, 1, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(1, 4, 1, PoleBoxes.HEAD, 2, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION),
            new MachineCell(0, 4, 1, PoleBoxes.HEAD, 3, PoleBoxes.LEG_HEAD, PoleBoxes.LEG_COLLISION)),
            FOOT, FOOT);
}
