package com.jaguarm.nauvisfluids.chemicalplant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What a chemical plant looks like and how much room it takes: three tiles by three, the size
 * Factorio made it, with the vat standing in the middle.
 */
public final class ChemicalPlantShape {
    private ChemicalPlantShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "chemical-plant";

    /** The ports, named the way {@code ProcessingBlockEntity} publishes them. */
    public static final String IN0 = "in0";
    public static final String IN1 = "in1";
    public static final String OUT0 = "out0";
    public static final String OUT1 = "out1";

    /** The one model this machine adds to the shared shell. */
    public static final String VAT = "vat";

    /** A vessel with a pipe rising out of its lid. */
    private static final float[][] VAT_BOXES = {
        {2, 0, 2, 14, 11, 14},
        {6, 11, 6, 10, 16, 10},
    };

    /**
     * The nine ground cells, north row first and west to east, then the vat.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The middle: the block that holds everything, and the one the player's click lands on. */
    public static final int CENTRE = 4;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        // North row: the two inputs at the corners.
        cells.add(new MachineCell(0, 0, 0, MachineParts.PORT_CORNER_LEFT, 0, MachineParts.PORT_CORNER_LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.PORT_CORNER_RIGHT, 0, MachineParts.PORT_CORNER_RIGHT_BOXES));
        // Middle row.
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        // South row: the two outputs at the corners. A corner port turned twice keeps the other
        // wall, so the right-hand model serves the south-west corner and the left the south-east.
        cells.add(new MachineCell(0, 0, 2, MachineParts.PORT_CORNER_RIGHT, 2, MachineParts.PORT_CORNER_RIGHT_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.PORT_CORNER_LEFT, 2, MachineParts.PORT_CORNER_LEFT_BOXES));
        // The vat.
        cells.add(new MachineCell(1, 1, 1, VAT, 0, VAT_BOXES));
        return new MachineShape(cells, CENTRE, CENTRE, Map.of(
                IN0, Set.of(new MachineShape.Port(0, Direction.NORTH)),
                IN1, Set.of(new MachineShape.Port(2, Direction.NORTH)),
                OUT0, Set.of(new MachineShape.Port(6, Direction.SOUTH)),
                OUT1, Set.of(new MachineShape.Port(8, Direction.SOUTH))));
    }
}
