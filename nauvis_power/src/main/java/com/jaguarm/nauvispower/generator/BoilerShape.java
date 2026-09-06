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
 * What a boiler looks like and how much room it takes: three tiles by two, the size Factorio made
 * it, with a chimney.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py} holds
 * this to the {@code size} recorded for {@code boiler} in {@code data/mapping.json}. Everything
 * else here is ours: Factorio is two-dimensional and has no opinion about chimneys.
 *
 * <h2>Seven blocks, and a lane between them</h2>
 *
 * <p>Six at ground level and the chimney above the middle of the back. The shell is
 * {@link MachineParts} - a wall at 1.0, a floor at 0.75 - so a boiler is climbed onto once and
 * walked over after that, and the chimney is the only thing to go around.
 *
 * <p><b>Where the chimney sits is a walkability decision, not a drawing one.</b> Factorio players
 * chain boilers in a row, and a row of them here has to stay crossable. The chimney is on the
 * middle of the three, so chained boilers stand their chimneys three blocks apart and leave a
 * two-wide lane between each pair. Put it on a corner instead and two chained boilers would leave
 * their chimneys touching, with the wall running the length of the row.
 *
 * <h2>Steam leaves one block, and water comes in at two</h2>
 *
 * <p>The steam port is the back face of the cell under the chimney, and nowhere else. Water comes
 * in at the two ends of the front row - Factorio's boiler has its water connections at either end
 * and its steam output at the back, which is what lets a row of boilers pass water along
 * themselves with the steam pipe running behind. A one-block boiler had to offer steam on all six
 * sides because it had only one block to offer it from; with a footprint there is somewhere
 * specific to put each pipe, and getting it wrong is a thing the player can see and fix. That is
 * what a footprint buys.
 *
 * <p>Each port is a gap in the wall: the back middle is a plain deck where the steam pipe goes,
 * and the front row has its wall along the front only, so both ends stand open where the water
 * pipes meet them.
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
