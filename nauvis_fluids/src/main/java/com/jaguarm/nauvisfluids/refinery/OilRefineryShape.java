package com.jaguarm.nauvisfluids.refinery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What an oil refinery looks like and how much room it takes: five tiles by five, the size
 * Factorio made it, with the column standing in the middle.
 */
public final class OilRefineryShape {
    private OilRefineryShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "oil-refinery";

    /** The ports, named the way {@code ProcessingBlockEntity} publishes them. */
    public static final String IN_WATER = "in0";
    public static final String IN_CRUDE = "in1";
    public static final String OUT_HEAVY = "out0";
    public static final String OUT_LIGHT = "out1";
    public static final String OUT_PETROLEUM = "out2";

    /** The two models this machine adds to the shared shell. */
    public static final String TOWER = "tower";
    public static final String DRUM = "drum";

    /** A section of the column: a fat pipe with a collar, stacked three high. */
    private static final float[][] TOWER_BOXES = {
        {4, 0, 4, 12, 13, 12},
        {3, 13, 3, 13, 16, 13},
    };

    /** A drum on the deck: a squat cylinder with a cap. */
    private static final float[][] DRUM_BOXES = {
        {2, 0, 2, 14, 12, 14},
        {4, 12, 4, 12, 14, 12},
    };

    /**
     * The twenty-five ground cells, north row first and west to east, then the tower from the
     * bottom up, then the drums.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle of the deck: the block that holds everything, and the one the player's click lands on. */
    public static final int CENTRE = 12;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        // North row: the three outputs, at the corners and the middle.
        cells.add(new MachineCell(0, 0, 0, MachineParts.PORT_CORNER_LEFT, 0, MachineParts.PORT_CORNER_LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.PORT_EDGE, 0, MachineParts.PORT_EDGE_BOXES));
        cells.add(new MachineCell(3, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(4, 0, 0, MachineParts.PORT_CORNER_RIGHT, 0, MachineParts.PORT_CORNER_RIGHT_BOXES));
        // Three middle rows: an edge either side of a deck.
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(3, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(3, 0, 2, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 2, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 3, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(3, 0, 3, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(4, 0, 3, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        // South row: the two inputs, second and fourth.
        cells.add(new MachineCell(0, 0, 4, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 4, MachineParts.PORT_EDGE, 2, MachineParts.PORT_EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 4, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(3, 0, 4, MachineParts.PORT_EDGE, 2, MachineParts.PORT_EDGE_BOXES));
        cells.add(new MachineCell(4, 0, 4, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        // The column, and the drums.
        cells.add(new MachineCell(2, 1, 2, TOWER, 0, TOWER_BOXES));
        cells.add(new MachineCell(2, 2, 2, TOWER, 0, TOWER_BOXES));
        cells.add(new MachineCell(2, 3, 2, TOWER, 0, TOWER_BOXES));
        cells.add(new MachineCell(1, 1, 1, DRUM, 0, DRUM_BOXES));
        cells.add(new MachineCell(3, 1, 1, DRUM, 0, DRUM_BOXES));
        return new MachineShape(cells, CENTRE, CENTRE, Map.of(
                OUT_HEAVY, Set.of(new MachineShape.Port(0, Direction.NORTH)),
                OUT_LIGHT, Set.of(new MachineShape.Port(2, Direction.NORTH)),
                OUT_PETROLEUM, Set.of(new MachineShape.Port(4, Direction.NORTH)),
                IN_WATER, Set.of(new MachineShape.Port(21, Direction.SOUTH)),
                IN_CRUDE, Set.of(new MachineShape.Port(23, Direction.SOUTH))));
    }
}
