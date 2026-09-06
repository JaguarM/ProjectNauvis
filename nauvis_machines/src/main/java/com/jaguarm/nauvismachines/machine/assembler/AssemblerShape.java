package com.jaguarm.nauvismachines.machine.assembler;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * What an assembling machine looks like and how much room it takes: three tiles by three, the
 * size Factorio made it.
 *
 * <p>Footprint is identity - see {@code docs/ARCHITECTURE.md}. Three by three is why an assembler column
 * spaces the way it does and why a player's layout transfers from one game to the other, so it is
 * stated here once and read by the block, the models and the loot table.
 *
 * <h2>Ten blocks, not eighteen, and why you can walk on it</h2>
 *
 * <p>The machine is two blocks tall but occupies ten blocks rather than eighteen: nine at ground
 * level and one more for the gearbox in the middle. The rest of the upper storey is air.
 *
 * <p>That is the whole design, and it is deliberate. <b>Assemblers placed edge to edge have to
 * stay crossable.</b> A field of 3x3 machines two solid blocks tall is a maze with no way over it
 * - you cannot jump two blocks - and a Factorio player packs assemblers together without leaving
 * corridors, because in Factorio you can always walk round the far end. Here the answer is up:
 * you climb onto a machine and walk over it.
 *
 * <p>The heights, in the terms {@code docs/ARCHITECTURE.md} sets out:
 *
 * <ul>
 *   <li><b>1.0</b> - the wall around the outside. A jump gets you onto it, since the jump is about
 *       1.25 blocks, and it is the only climb in a whole field of machines however large;
 *   <li><b>0.75</b> - the floor inside the wall. A quarter-block dip, well under the 0.6 step the
 *       player takes without noticing, so crossing a machine is walking rather than hopping;
 *   <li><b>1.0</b> - the plinth in the middle, and <b>2.0</b> for the gearbox standing on it. The
 *       gearbox is the one thing you walk around, and tiled machines put them three apart, which
 *       leaves lanes two blocks wide in both directions.
 * </ul>
 *
 * <p>Nothing here is scenery you pass through: what you see is what you stand on. The machine is
 * allowed a taller silhouette than its collision - {@code PolePart}'s crossarm does exactly that -
 * but for something you are meant to walk across, the two agreeing is the point.
 */
public final class AssemblerShape {

    private AssemblerShape() {}

    /**
     * Which Factorio entity this is the shape of.
     *
     * <p>Not used at runtime. It is here so {@code tools/check_models.py} can tie the cells below
     * to the {@code size} recorded against this id in {@code data/mapping.json}, and fail the
     * build if the two ever disagree. A footprint is identity; identity is checked rather than
     * remembered, the same way recipes are.
     */
    public static final String FACTORIO_ID = "assembling-machine-1";

    /**
     * The one model name this machine adds. The shell it shares with every other machine in the
     * pack is {@link MachineParts} - see there for why that is one file and not three.
     */
    public static final String GEARBOX = "gearbox";

    /** The mechanism on top, and the only part of an assembler you have to walk around. */
    private static final float[][] GEARBOX_BOXES = {
        {2, 0, 2, 14, 12, 14},
        {4, 12, 4, 12, 16, 12},
    };

    /**
     * The nine ground cells clockwise from north-west, then the gearbox.
     *
     * <p>Built rather than written out, so the four corners are the one corner turned four ways
     * and cannot become four subtly different corners. The order fixes the {@code part} values,
     * which are in world saves - <b>do not reorder this list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** Index of the middle of the deck: the block that holds everything, and the block you click. */
    public static final int MIDDLE = 8;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        // Corners, clockwise from north-west. One model, four turns.
        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        // Edge middles, clockwise from north.
        cells.add(new MachineCell(1, 0, 0, MachineParts.EDGE, 0, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.EDGE, 2, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));

        // The middle of the deck, which is index 8 - see MIDDLE - and the gearbox above it.
        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 1, 1, GEARBOX, 0, GEARBOX_BOXES));

        // Anchor and placement are both the middle: the machine keeps its block entity where you
        // would point at it, and a 3x3 centres on the block you clicked the way Factorio's ghost
        // does rather than growing away from you.
        return new MachineShape(cells, MIDDLE, MIDDLE);
    }
}
