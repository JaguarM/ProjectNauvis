package com.jaguarm.nauvisfluids.tank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What a storage tank looks like and how much room it takes: three tiles by three, the size
 * Factorio made it, with the drum standing two blocks high in the middle.
 */
public final class StorageTankShape {
    private StorageTankShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "storage-tank";

    /** The one port, on four faces. See {@link MachineShape#hasPort}. */
    public static final String PORT = "port";

    /** The one model this machine adds to the shared shell. */
    public static final String DRUM = "drum";

    /** A section of the drum: a fat cylinder, stacked two high, with a rim where the sections meet. */
    private static final float[][] DRUM_BOXES = {
        {2, 0, 2, 14, 14, 14},
        {1, 14, 1, 15, 16, 15},
    };

    /**
     * The nine ground cells, north row first and west to east, then the drum from the bottom up.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle: the block that holds everything, and the one the player's click lands on. */
    public static final int CENTRE = 4;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        // The four corners are the one port model, turned: its wall stays on the side the pinwheel
        // keeps and its stub points the way the connection faces.
        cells.add(new MachineCell(0, 0, 0, MachineParts.PORT_CORNER_LEFT, 0, MachineParts.PORT_CORNER_LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.PORT_CORNER_LEFT, 1, MachineParts.PORT_CORNER_LEFT_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.PORT_CORNER_LEFT, 3, MachineParts.PORT_CORNER_LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.PORT_CORNER_LEFT, 2, MachineParts.PORT_CORNER_LEFT_BOXES));
        // The drum.
        cells.add(new MachineCell(1, 1, 1, DRUM, 0, DRUM_BOXES));
        cells.add(new MachineCell(1, 2, 1, DRUM, 0, DRUM_BOXES));
        return new MachineShape(cells, CENTRE, CENTRE, Map.of(PORT, Set.of(
                new MachineShape.Port(0, Direction.NORTH),
                new MachineShape.Port(2, Direction.EAST),
                new MachineShape.Port(8, Direction.SOUTH),
                new MachineShape.Port(6, Direction.WEST))));
    }
}
