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
 * What a steam engine looks like and how much room it takes: five tiles by three, the size
 * Factorio made it.
 */
public final class SteamEngineShape {

    private SteamEngineShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "steam-engine";

    /** The name of the port steam goes in and out of. See {@link MachineShape#hasPort}. */
    public static final String STEAM = "steam";

    /** The one model this machine adds to the shared shell. */
    public static final String FLYWHEEL = "flywheel";

    /**
     * A wheel on a hub, broad across the machine and thin along it, so it reads as turning.
     *
     * <p>Four pixels thick and standing in the spine, which leaves it clear of the walls on either
     * flank - nothing here shares a plane with anything else, because faces that do z-fight.
     */
    private static final float[][] FLYWHEEL_BOXES = {
        {6, 0, 6, 10, 5, 10},
        {2, 5, 6, 14, 16, 10},
    };

    /**
     * Four corners, then the two flanks, then the spine, then the flywheels.
     *
     * <p>Every cell is written out rather than looped. The order fixes the {@code part} values,
     * which are in world saves - <b>do not reorder this list</b>, only append to it - and a loop
     * hides both that order and the machine's real size from
     * {@code tools/check_models.py}, which reads these numbers out of the source.
     */
    public static final MachineShape SHAPE = build();

    /** The middle of the spine: the block that holds everything, and the block you click. */
    public static final int MIDDLE = 12;

    /** The two ends of the spine, where steam goes in and comes out. */
    public static final int NORTH_END = 10;
    public static final int SOUTH_END = 14;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Corners, clockwise from north-west. One model, four turns.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 4, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 4, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        // The west flank, then the east one: wall outward, floor inward.
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 3, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 3, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));

        // The spine, north to south. Indices 10 to 14 - see NORTH_END, MIDDLE and SOUTH_END - and
        // no wall anywhere along it, which is what leaves both ends open for a pipe.
        cells.add(new MachineCell(1, 0, 0, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 0, 4, MachineParts.DECK, 0, MachineParts.DECK_BOXES));

        // The flywheels, at the second and fourth tiles. The gap between them is the way across.
        cells.add(new MachineCell(1, 1, 1, FLYWHEEL, 0, FLYWHEEL_BOXES));
        cells.add(new MachineCell(1, 1, 3, FLYWHEEL, 0, FLYWHEEL_BOXES));

        return new MachineShape(cells, MIDDLE, MIDDLE, Map.of(STEAM, Set.of(
                new MachineShape.Port(NORTH_END, Direction.NORTH),
                new MachineShape.Port(SOUTH_END, Direction.SOUTH))));
    }
}
