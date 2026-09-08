package com.jaguarm.nauvismining.machine.miner;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/** Two tiles by two, the size Factorio made the burner mining drill. */
public final class BurnerDrillShape {

    private BurnerDrillShape() {}

    /** Ties these cells to the {@code size} in Project Nauvis's {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "burner-mining-drill";

    /** The models this drill adds to the shared shell. */
    public static final String CHIMNEY = "chimney";

    /**
     * The firebox corner, which is the same shell as the other three and a different file.
     *
     * <p>It has to be a different file because it is the one that wears the drill's face -
     * the front texture, and the lit one while it is burning. A model is a model plus its
     * textures, so a corner showing a furnace door is not the corner the other three are.
     */
    public static final String FACE = "face";

    /** A stack over the firebox, narrowing as it rises. */
    private static final float[][] CHIMNEY_BOXES = {
        {4, 0, 4, 12, 3, 12},
        {5, 3, 5, 11, 13, 11},
        {4, 13, 4, 12, 16, 12},
    };

    /**
     * Four blocks on the ground and the chimney above the first.
     *
     * <p>The order fixes the {@code part} values, which are in world saves: <b>do not reorder this
     * list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The front-left block: the firebox, the block entity, and the one you click. */
    public static final int FIREBOX = 0;

    /** The ore comes out under the chimney: the block in front of the firebox is the output tile. */
    public static final int OUTPUT = FIREBOX;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Clockwise from the front left. Two deep by two across means every cell is a corner, so
        // the wall turns at each of them - one corner model from MachineParts, four turns. The
        // burner wears the same shell as a boiler or an assembler, which is the point of that
        // file: a wall at 1.0 and a floor at 0.75, so the family reads as one thing.
        cells.add(new MachineCell(0, 0, 0, FACE, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        cells.add(new MachineCell(0, 1, 0, CHIMNEY, 0, CHIMNEY_BOXES));

        return new MachineShape(cells, FIREBOX, FIREBOX);
    }
}
