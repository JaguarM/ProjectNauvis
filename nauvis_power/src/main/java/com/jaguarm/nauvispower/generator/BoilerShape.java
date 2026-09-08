package com.jaguarm.nauvispower.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What a boiler looks like and how much room it takes: three tiles by two, the size Factorio
 * made it, with a chimney.
 */
public final class BoilerShape {

    private BoilerShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "boiler";

    /** The name of the port the steam comes out of. See {@link MachineShape#hasPort}. */
    public static final String STEAM = "steam";

    /** The name of the ports water comes in at - both ends of the front row. */
    public static final String WATER = "water";

    /** The one model this machine adds to the shared shell. */
    public static final String CHIMNEY = "chimney";

    /** A stack, narrowing as it rises. Two blocks tall in total, standing on the deck below it. */
    private static final float[][] CHIMNEY_BOXES = {
        {3, 0, 3, 13, 4, 13},
        {5, 4, 5, 11, 14, 11},
        {4, 14, 4, 12, 16, 12},
    };

    /**
     * The six ground cells, then the chimney.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The west end of the front row, where water comes in. */
    public static final int WEST_END = 0;

    /** The middle of the front row: the block the player clicks, so the boiler grows away from them. */
    public static final int FRONT_MIDDLE = 1;

    /** The east end of the front row, where water comes in. */
    public static final int EAST_END = 2;

    /** The middle of the back row: the block that holds everything, under the chimney. */
    public static final int BACK_MIDDLE = 5;

    /** The chimney, which is also where the steam leaves. */
    public static final int STACK = 6;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Front row, west to east. The wall runs along the front only, so both ends stand open
        // where the water pipes meet them.
        cells.add(new MachineCell(0, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));

        // Back row. The middle of it is a plain deck rather than a walled edge, which leaves a
        // gap in the outer wall exactly where the steam pipe goes - the port is a thing you can
        // see before you have connected anything to it.
        cells.add(new MachineCell(0, 0, 1, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 1, 1, CHIMNEY, 0, CHIMNEY_BOXES));

        return new MachineShape(cells, BACK_MIDDLE, FRONT_MIDDLE, Map.of(
                STEAM, Set.of(new MachineShape.Port(BACK_MIDDLE, Direction.SOUTH)),
                WATER, Set.of(new MachineShape.Port(WEST_END, Direction.WEST),
                        new MachineShape.Port(EAST_END, Direction.EAST))));
    }
}
