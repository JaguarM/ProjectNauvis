package com.jaguarm.nauvismining.machine.miner;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;

/**
 * Three tiles by three, the size Factorio made the electric mining drill.
 *
 * <p>It was a single block until version 2.0, and the change is not cosmetic. A drill is the thing
 * a player builds most of, in rows across an ore patch, so its footprint decides what a mining
 * field looks like more than any other number in this mod.
 *
 * <h2>Half a block high, so you walk over a drill field rather than through it</h2>
 *
 * <p>A field of drills is the arrangement where crossing a machine matters most: there are dozens
 * of them, they cover the ground you want to walk on, and there is nowhere else to put them. At
 * half a block the player's own step carries them across without a jump, so a hundred drills read
 * as one flat floor.
 *
 * <p>The exception is the output head at the front, which stands a full block and is the one thing
 * you walk around. It earns that by being the part that hands you the ore, and it stands where the
 * drill faces - so a row of drills all pointed at the same belt puts every head on the same side.
 *
 * <p>Nothing here is scenery you pass through: what you can see, you can stand on.
 */
public final class ElectricDrillShape {

    private ElectricDrillShape() {}

    /**
     * Which Factorio entity this is the shape of.
     *
     * <p>Not used at runtime, and this mod does not need it. It is here so that
     * {@code tools/check_models.py} over in Project Nauvis can tie these cells to the {@code size}
     * recorded against the entity in {@code data/mapping.json}, and fail if the two disagree.
     */
    public static final String FACTORIO_ID = "electric-mining-drill";

    /** The two models this drill draws with. */
    public static final String DECK = "deck";
    public static final String HEAD = "head";

    /** Half a block, and the whole reason a drill field is walkable. */
    private static final int LOW = 8;

    /** Flat, and low enough that the player's step clears it without a jump. */
    private static final float[][] DECK_BOXES = {
        {0, 0, 0, 16, LOW, 16},
    };

    /** The same deck with the output chute standing on it. */
    private static final float[][] HEAD_BOXES = {
        {0, 0, 0, 16, LOW, 16},
        {2, LOW, 2, 14, 16, 14},
    };

    /**
     * The head, then the middle, then the rest of the deck.
     *
     * <p>Every cell is written out rather than looped, which is worth the extra lines twice over.
     * The order fixes the {@code part} values, and those are in world saves - <b>do not reorder
     * this list</b>, only append to it. And a loop hides the machine's real size from Project
     * Nauvis's {@code tools/check_models.py}, which reads these numbers straight out of the
     * source: written as a nested loop, this drill read as one tile by two and said nothing.
     */
    public static final MachineShape SHAPE = build();

    /** The head, at the middle of the front row: where the player's click lands. */
    public static final int OUTPUT = 0;

    /**
     * The middle of the nine, which holds the block entity.
     *
     * <p>Not the head, though the head would have been the obvious choice. The block entity's
     * own position is what the dig area is measured from, and a drill that mined a square centred
     * on its front edge rather than on itself would be a different machine in an existing world.
     * The one thing a released mod may not quietly change is where it digs.
     */
    public static final int MIDDLE = 1;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(1, 0, 0, HEAD, 0, HEAD_BOXES));
        cells.add(new MachineCell(1, 0, 1, DECK, 0, DECK_BOXES));

        cells.add(new MachineCell(0, 0, 0, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(0, 0, 1, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(0, 0, 2, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(1, 0, 2, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(2, 0, 0, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(2, 0, 1, DECK, 0, DECK_BOXES));
        cells.add(new MachineCell(2, 0, 2, DECK, 0, DECK_BOXES));
        // Placed on the head so the drill lands with its output where the player was pointing,
        // and anchored on the middle so it digs where it always did.
        return new MachineShape(cells, MIDDLE, OUTPUT);
    }
}
