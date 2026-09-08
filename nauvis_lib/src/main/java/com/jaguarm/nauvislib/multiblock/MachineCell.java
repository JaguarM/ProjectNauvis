package com.jaguarm.nauvislib.multiblock;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a multi-block machine: where it sits in the machine's own frame, what it draws,
 * and what you bump into.
 */
public final class MachineCell {

    private final int x;
    private final int y;
    private final int z;
    private final String model;
    private final int turns;
    private final float[][] boxes;
    private final float[][] collisionBoxes;

    /** Both shapes for all four facings, built once. Ten cells times four is forty small objects. */
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> collisionShapes = new EnumMap<>(Direction.class);

    /** A cell whose outline and collision are the same boxes. */
    public MachineCell(int x, int y, int z, String model, int turns, float[][] boxes) {
        this(x, y, z, model, turns, boxes, boxes);
    }

    /**
     * @param model the model file this cell draws, in that model's own frame
     * @param turns quarter turns clockwise from that frame to this cell's place in the machine.
     *              Four corners of a machine are one corner model and four turns, so they cannot
     *              drift into four slightly different corners. The blockstate applies the turn to
     *              the model as a {@code y} rotation and the shapes below apply it to the boxes,
     *              which is the same rotation twice and has to stay that way
     */
    public MachineCell(int x, int y, int z, String model, int turns,
            float[][] boxes, float[][] collisionBoxes) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.model = model;
        this.turns = turns;
        this.boxes = boxes;
        this.collisionBoxes = collisionBoxes;

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int total = turns + Boxes.quarterTurns(facing);
            VoxelShape outline = build(Boxes.rotate(boxes, total));
            shapes.put(facing, outline);
            collisionShapes.put(facing, boxes == collisionBoxes
                    ? outline
                    : build(Boxes.rotate(collisionBoxes, total)));
        }
    }

    private static VoxelShape build(float[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (float[] box : boxes) {
            // Clamped, because Block.box rejects anything outside one block and an overhanging
            // box is a drawing instruction rather than a collision one. A box entirely outside
            // this cell contributes nothing, which is what it should do.
            shape = Shapes.or(shape, Block.box(
                    Math.max(0, box[0]), Math.max(0, box[1]), Math.max(0, box[2]),
                    Math.min(16, box[3]), Math.min(16, box[4]), Math.min(16, box[5])));
        }
        return shape;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** The name of the model file this cell draws with. Cells that differ only by a turn share one. */
    public String model() {
        return model;
    }

    /** Quarter turns clockwise from the model's own frame to this cell's place in the machine. */
    public int turns() {
        return turns;
    }

    /**
     * Model pixels, {@code {x1, y1, z1, x2, y2, z2}} per box, in the model's own frame. Read by
     * the model provider, which writes them out once and lets the blockstate do {@link #turns()}.
     */
    public float[][] boxes() {
        return boxes;
    }

    /** What you see, and what the selection outline traces. */
    public VoxelShape shape(Direction facing) {
        return shapes.get(facing);
    }

    /** What you bump into. */
    public VoxelShape collisionShape(Direction facing) {
        return collisionShapes.get(facing);
    }
}
