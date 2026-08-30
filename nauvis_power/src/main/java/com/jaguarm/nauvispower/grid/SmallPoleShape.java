package com.jaguarm.nauvispower.grid;

import java.util.List;

import com.jaguarm.nauvispower.multiblock.MachineCell;
import com.jaguarm.nauvispower.multiblock.MachineShape;

/**
 * One tile, four blocks tall.
 *
 * <p>A pole is a multi-block in the way a door is, and since 26.2 it is one in the way every other
 * machine in this pack is: the same {@link MachineShape} the boiler and the engine use, with a
 * footprint of one. It used to have a mechanism of its own - a {@code PolePart} enum, four rules
 * written out by hand - and that mechanism was the thing {@code Multiblock} was generalised from,
 * so having the pole keep its own copy of it was two implementations of one idea. The tiers are
 * what made it untenable: a five-block pole needs a five-value enum and a two-by-two one needs
 * two more axes, both of which {@link MachineShape} already has.
 *
 * <p>Four blocks because a pole has to stand well clear of the machines it feeds - three left it
 * looking like a tall fence. Height is ours rather than Factorio's; see ARCHITECTURE.md.
 */
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
