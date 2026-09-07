package com.jaguarm.nauvisfluids.pumpjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What a pumpjack looks like and how much room it takes: three tiles by three, the size Factorio
 * made it, with the pump standing in the middle.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py}
 * holds this to the {@code size} recorded for {@code pumpjack} in {@code data/mapping.json}.
 * Everything else here is ours.
 *
 * <h2>Ten blocks, walkable but for the middle</h2>
 *
 * <p>Nine at ground level in the shared {@link MachineParts} shell - a wall at 1.0, a floor at
 * 0.75 - and the nodding donkey on the tenth, above the centre. A field of pumpjacks is walked
 * across, and the pumps are what you walk round.
 *
 * <h2>The oil leaves one corner</h2>
 *
 * <p>Factorio's pumpjack has a single fluid output at a corner of its footprint, pointing out along
 * the machine's facing, and rotating the machine is how you choose which corner. Here that is the
 * north-east cell's north face in the machine's own frame, and it turns with the facing exactly as
 * the boiler's steam port does. The corner is drawn without its north wall and with a wellhead on
 * it, so where the pipe goes is visible before a pipe is there.
 *
 * <h2>The well is under the middle</h2>
 *
 * <p>The block below the centre cell must be {@code nauvis_fluids:crude_oil}, and that is the only
 * place a pumpjack may stand. {@link PumpjackBlock#snapPart} is what lets a player click any of
 * the nine blocks over a well and get the machine centred on it.
 */
public final class PumpjackShape {

    private PumpjackShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "pumpjack";

    /** The name of the port the crude oil comes out of. See {@link MachineShape#hasPort}. */
    public static final String OUTPUT = "output";

    /** The three models this machine adds to the shared shell. */
    public static final String OUTLET = "outlet";
    public static final String HEAD = "head";
    public static final String TOP = "top";

    /**
     * The corner the oil leaves: the shell's floor with the east wall kept and the north wall left
     * open, and a wellhead standing on the floor where a pipe meets the machine.
     */
    private static final float[][] OUTLET_BOXES = {
        {0, 0, 0, 16, MachineParts.FLOOR, 16},
        {13, MachineParts.FLOOR, 3, 16, MachineParts.WALL, 16},
        {3, MachineParts.FLOOR, 3, 9, MachineParts.WALL, 9},
    };

    /**
     * A nodding donkey over two blocks. The lower block is the post on its base plate, the
     * upper is the beam rocking on it: the walking beam along the block, the horse head at the
     * front and the counterweight at the back. It was one block once and read as a small box
     * from the item slot; a pumpjack is the tallest thing on an oil field and should look it.
     *
     * <p>The beam's ends sit inside the head and the weight, and the post meets the pivot face to
     * face rather than sharing a plane the same way, so nothing z-fights - see
     * {@code docs/PITFALLS.md}.
     */
    private static final float[][] HEAD_BOXES = {
        {4, 0, 4, 12, 2, 12},
        {6, 2, 6, 10, 16, 10},
    };

    private static final float[][] TOP_BOXES = {
        {5, 0, 5, 11, 1, 11},
        {5, 1, 1, 11, 4, 15},
        {3, 0, 0, 13, 9, 3},
        {4, 0, 13, 12, 6, 16},
    };

    /**
     * The nine ground cells, north row first and west to east, then the head.
     *
     * <p>The order fixes the {@code part} values, which are in world saves - <b>do not reorder
     * this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The north-east corner, where the oil leaves. */
    public static final int OUTLET_CELL = 2;

    /** The middle: the block that holds everything, the one the player's click lands on, and the one over the well. */
    public static final int CENTRE = 4;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // North row. The east corner is the outlet.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 0, OUTLET, 0, OUTLET_BOXES));

        // Middle row. A full deck in the centre for the pump to stand on.
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));

        // South row.
        cells.add(new MachineCell(0, 0, 2, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));

        // The pump: the post, and the beam above it. Appended in that order; see above.
        cells.add(new MachineCell(1, 1, 1, HEAD, 0, HEAD_BOXES));
        cells.add(new MachineCell(1, 2, 1, TOP, 0, TOP_BOXES));

        return new MachineShape(cells, CENTRE, CENTRE,
                Map.of(OUTPUT, Set.of(new MachineShape.Port(OUTLET_CELL, Direction.NORTH))));
    }
}
