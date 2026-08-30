package com.jaguarm.nauvismachines.multiblock;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a multi-block machine: where it sits in the machine's own frame, what it draws,
 * and what you bump into.
 *
 * <p><b>The boxes are the only description of this cell's shape.</b> The block builds its
 * {@code VoxelShape} from them and the model provider builds the model's elements from them, so
 * what you see and what you can hit are the same numbers rather than two lists that agree today.
 * That rule started on the electric pole, which was a mechanism of its own until it became one of
 * these; see {@code PoleBoxes} in nauvis_power for the same idea at its smallest.
 *
 * <p>Boxes are in model pixels - sixteen to a block - and relative to <em>this cell</em>, not to
 * the machine. A box may leave {@code 0..16} and hang into a neighbouring block, which is how a
 * chimney or a flywheel gets drawn without occupying a block of its own; the limit is
 * {@code -16..32}, from {@code CuboidModelElement}, and {@code tools/check_models.py} fails the
 * build on a box outside it. Collision, unlike drawing, is clipped to the cell by the game, so an
 * overhanging box is scenery you walk through unless a real cell is there too.
 *
 * <h2>Why collision is a second list</h2>
 *
 * <p>Same reason as the pole's crossarm: a shape you cannot see should not catch you. A machine's
 * silhouette is allowed to be taller than the thing you bump into, and for a field of machines
 * you are meant to walk across, it usually should be.
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
