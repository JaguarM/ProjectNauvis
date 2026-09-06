package com.jaguarm.nauvisfluids.offshorepump;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * What an offshore pump looks like and how much room it takes: one tile by two, the size Factorio
 * made it, with the intake at the water and the pump on the shore.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md} - and {@code tools/check_models.py}
 * holds this to the {@code size} recorded for {@code offshore-pump} in {@code data/mapping.json}.
 * Everything else here is ours.
 *
 * <h2>Two blocks, and the intake is the front</h2>
 *
 * <p>Factorio's offshore pump stands at the shoreline with its intake over the water and its
 * outlet on the land side, and turning it is choosing which way the pipe runs. Here the same:
 * the cell the player's click lands on is the body, the intake goes down one block ahead of it -
 * out over the water when the pump stands at the edge - and the water comes out of the body's
 * back, the face away from the sea. A player standing on the beach facing the water and placing
 * one gets exactly that.
 *
 * <p>The body is a housing with the pump on top and a stub of pipe out of the back; the intake
 * is the pipe carried forward, turned down, and ended in a strainer that sits on the water's
 * surface. All of it inside its two blocks, so the textures are the shell's and nothing is drawn
 * with a derived uv off the end of the sprite.
 */
public final class OffshorePumpShape {

    private OffshorePumpShape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "offshore-pump";

    /** The name of the port the water comes out of. See {@link MachineShape#hasPort}. */
    public static final String OUTPUT = "output";

    /** The two models. */
    public static final String INLET = "inlet";
    public static final String BODY = "body";

    /**
     * The intake: the feed pipe arriving from the body, the drop with the pipe's end inside it,
     * and a strainer foot on the water. The drop contains the feed's end, and the foot's top meets
     * the drop's bottom edge to edge, so no two faces share a plane and point the same way.
     */
    private static final float[][] INLET_BOXES = {
        {5, 5, 8, 11, 11, 16},
        {4, 3, 7, 12, 12, 15},
        {2, 0, 5, 14, 3, 16},
    };

    /**
     * The pump: a housing, the motor on top of it, the feed pipe leaving the front towards the
     * intake, and the outlet stub out of the back where the pipe goes.
     */
    private static final float[][] BODY_BOXES = {
        {2, 0, 2, 14, 12, 14},
        {4, 12, 4, 12, 16, 12},
        {5, 5, 0, 11, 11, 2},
        {5, 5, 14, 11, 11, 16},
    };

    /** The intake, then the body. Part values are in world saves - append, never reorder. */
    public static final MachineShape SHAPE = build();

    /** The front: over the water. */
    public static final int INLET_CELL = 0;

    /** The back: on the shore, holding everything, and the cell the player's click lands on. */
    public static final int BODY_CELL = 1;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(0, 0, 0, INLET, 0, INLET_BOXES));
        cells.add(new MachineCell(0, 0, 1, BODY, 0, BODY_BOXES));
        return new MachineShape(cells, BODY_CELL, BODY_CELL,
                Map.of(OUTPUT, Set.of(new MachineShape.Port(BODY_CELL, Direction.SOUTH))));
    }
}
