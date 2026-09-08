package com.jaguarm.nauvismilitary.turret;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import java.util.ArrayList;
import java.util.List;

/** Two tiles by two, the size Factorio made the gun turret. */
public final class GunTurretShape {

    private GunTurretShape() {}

    /** Ties these cells to the {@code size} in Project Nauvis's {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "gun-turret";

    /** The two front cells: a barrel each, one with the wall on its left and one on its right. */
    public static final String GUN_LEFT = "gun_left";
    public static final String GUN_RIGHT = "gun_right";

    private static final float[][] GUN_LEFT_BOXES = {
        {0, 0, 0, 16, MachineParts.FLOOR, 16},
        {0, MachineParts.FLOOR, 0, 3, MachineParts.WALL, 16},
        {6, MachineParts.FLOOR, 0, 10, 15, 12},
        {4, MachineParts.FLOOR, 10, 12, MachineParts.WALL, 16},
    };

    private static final float[][] GUN_RIGHT_BOXES = {
        {0, 0, 0, 16, MachineParts.FLOOR, 16},
        {13, MachineParts.FLOOR, 0, 16, MachineParts.WALL, 16},
        {6, MachineParts.FLOOR, 0, 10, 15, 12},
        {4, MachineParts.FLOOR, 10, 12, MachineParts.WALL, 16},
    };

    /**
     * Front left, front right, back right, back left.
     *
     * <p>The order fixes the {@code part} values, which are in world saves: <b>do not reorder this
     * list</b>, only append to it.
     */
    public static final MachineShape SHAPE = build();

    /** The front-left block: the block entity, and the one the player clicks. */
    public static final int ANCHOR = 0;

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();
        cells.add(new MachineCell(0, 0, 0, GUN_LEFT, 0, GUN_LEFT_BOXES));
        cells.add(new MachineCell(1, 0, 0, GUN_RIGHT, 0, GUN_RIGHT_BOXES));
        cells.add(new MachineCell(1, 0, 1, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));
        return new MachineShape(cells, ANCHOR, ANCHOR);
    }
}
