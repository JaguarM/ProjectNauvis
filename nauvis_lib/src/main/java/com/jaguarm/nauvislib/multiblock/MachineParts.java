package com.jaguarm.nauvislib.multiblock;

/** The shell every machine in the pack is built out of, so they read as one family. */
public final class MachineParts {

    private MachineParts() {}

    /** Model names, shared so a blockstate reads the same from machine to machine. */
    public static final String DECK = "deck";
    public static final String EDGE = "edge";
    public static final String CORNER = "corner";
    /** The floor with no wall at all: the inside of a machine wider than three. */
    public static final String FLOOR_CELL = "floor";

    /** How high you stand when you are on a machine, in pixels. */
    public static final int FLOOR = 12;

    /** How high its outer wall stands. A full block, so the climb is one jump and no more. */
    public static final int WALL = 16;

    /**
     * A full block: what goes under whatever the machine puts in the middle.
     *
     * <p>Full height rather than the recessed floor around it, so the thing standing on it meets
     * something solid. A quarter-block gap under a machine's own mechanism is the sort of thing
     * you only notice once you are next to it.
     */
    public static final float[][] DECK_BOXES = {
        {0, 0, 0, 16, WALL, 16},
    };

    /**
     * The recessed floor on its own, for a cell that is neither on the wall nor under anything.
     * A three-by-three machine has none - its one inner cell is the deck - and a nine-by-nine
     * silo is mostly this.
     */
    public static final float[][] FLOOR_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
    };

    /** A recessed floor with the outer wall along the north edge. */
    public static final float[][] EDGE_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, 16, WALL, 3},
    };

    /**
     * The same floor with the wall turning a corner.
     *
     * <p>The two wall boxes meet rather than overlap - faces sharing a plane and pointing the same
     * way z-fight, which looks like the model flickering.
     */
    public static final float[][] CORNER_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, 16, WALL, 3},
        {0, FLOOR, 3, 3, WALL, 16},
    };

    /**
     * The three cells a fluid enters or leaves through, one for each place on the shell a port
     * can be: an edge, and a corner with the port on one side or the other of it.
     *
     * <p>The wall is opened where the pipe meets the machine and a short length of pipe lies on
     * the floor in the gap, so where a pipe goes is visible before a pipe is there - the same
     * idea as the pumpjack's wellhead, made general. The port is on the model's north face; a
     * cell whose port faces another way is this model turned, exactly as the corners are.
     */
    public static final String PORT_EDGE = "port_edge";
    /** A corner with the port on its north face and the wall kept on its west. */
    public static final String PORT_CORNER_LEFT = "port_corner_left";
    /** The mirror image: port north, wall kept on the east. A mirror is not a turn, so it is a model of its own. */
    public static final String PORT_CORNER_RIGHT = "port_corner_right";

    /** Where the wall opens: the pipe's own width, 5..11, centred on the face. */
    private static final int PORT_FROM = 5;
    private static final int PORT_TO = 11;
    /** How far the stub reaches in from the edge. Short, so the floor stays a floor. */
    private static final int PORT_DEPTH = 7;

    public static final float[][] PORT_EDGE_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, PORT_FROM, WALL, 3},
        {PORT_TO, FLOOR, 0, 16, WALL, 3},
        {PORT_FROM, FLOOR, 0, PORT_TO, WALL, PORT_DEPTH},
    };

    public static final float[][] PORT_CORNER_LEFT_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {0, FLOOR, 0, 3, WALL, 16},
        {PORT_TO, FLOOR, 0, 16, WALL, 3},
        {PORT_FROM, FLOOR, 0, PORT_TO, WALL, PORT_DEPTH},
    };

    public static final float[][] PORT_CORNER_RIGHT_BOXES = {
        {0, 0, 0, 16, FLOOR, 16},
        {13, FLOOR, 0, 16, WALL, 16},
        {0, FLOOR, 0, PORT_FROM, WALL, 3},
        {PORT_FROM, FLOOR, 0, PORT_TO, WALL, PORT_DEPTH},
    };
}
