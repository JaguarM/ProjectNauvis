package com.jaguarm.nauvispower.multiblock;

import net.minecraft.core.Direction;

/**
 * Turning a list of boxes a quarter turn at a time.
 *
 * <p>Two things have to rotate together when a machine is placed facing east rather than north:
 * where each cell sits, and the geometry inside it. This does the second; {@link MachineShape}
 * does the first, with the same rotation. **They are one rotation written twice and must stay
 * that way** - a model turned one way and a collision box turned the other is a machine you can
 * see through on one side and walk into on the other, and nothing about that fails a test that
 * does not look for it.
 *
 * <p>The convention is Minecraft's, not a choice: a blockstate {@code y: 90} turns a model a
 * quarter turn <em>clockwise seen from above</em>, which is what puts a furnace's front face on
 * the east when its model draws it on the north. Rotating a point about the centre of its block
 * that way sends {@code (x, z)} to {@code (16 - z, x)}, and rotating an offset between blocks
 * sends {@code (x, z)} to {@code (-z, x)}. The two differ only by the block's own centre.
 *
 * <p>It is also used at authoring time, and that is the more common case: four corner cells of a
 * machine are one corner written once and turned three times, so they cannot drift into three
 * different corners.
 */
public final class Boxes {

    private Boxes() {}

    /** How far clockwise this facing is from north, in quarter turns. */
    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("a machine faces horizontally: " + facing);
        };
    }

    /** Every box turned to suit a machine facing this way. The north-facing list is returned as is. */
    public static float[][] rotate(float[][] boxes, Direction facing) {
        return rotate(boxes, quarterTurns(facing));
    }

    /** Every box turned {@code turns} quarter turns clockwise, seen from above. */
    public static float[][] rotate(float[][] boxes, int turns) {
        int quarters = Math.floorMod(turns, 4);
        if (quarters == 0) {
            return boxes;
        }
        float[][] turned = new float[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            turned[i] = rotate(boxes[i], quarters);
        }
        return turned;
    }

    private static float[] rotate(float[] box, int quarters) {
        float[] turned = box.clone();
        for (int turn = 0; turn < quarters; turn++) {
            // (x, z) -> (16 - z, x), applied to both corners, then put back in min/max order.
            float x1 = 16 - turned[5];
            float z1 = turned[0];
            float x2 = 16 - turned[2];
            float z2 = turned[3];
            turned = new float[] {
                Math.min(x1, x2), turned[1], Math.min(z1, z2),
                Math.max(x1, x2), turned[4], Math.max(z1, z2),
            };
        }
        return turned;
    }
}
