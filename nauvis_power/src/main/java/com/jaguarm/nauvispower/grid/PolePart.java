package com.jaguarm.nauvispower.grid;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which slice of a four-block pole a given block is, and what that slice is made of.
 *
 * <p>A pole is a multi-block in the way a door is: one block id, one item, one
 * {@code EnumProperty}, and the whole thing placed and broken as a unit. Only {@link #FOOT}
 * carries the block entity and therefore the network membership; the rest is structure. Four
 * blocks because a pole has to stand well clear of the machines it feeds - three left it looking
 * like a tall fence.
 *
 * <p><b>These boxes are the only description of a pole's shape.</b> The block builds its
 * {@code VoxelShape} from them and the model provider builds the model's elements from them, so
 * the thing you see and the thing you can hit are the same numbers rather than two lists that
 * drift. They are in model pixels - sixteen to a block - because that is the unit the model format
 * uses, and converting once here is cheaper than converting in two places.
 *
 * <h2>You can walk through the crossarm</h2>
 *
 * <p>{@link #collisionBoxes()} is the bare post and nothing else. An arm that reaches most of the
 * way across its block would otherwise catch you as you walked past the top of a pole, from a
 * shape you cannot see because it is three blocks over your head. Collision is the post; the
 * outline you get when you look at it is the whole thing.
 *
 * <h2>Why the arms are thinner than the post</h2>
 *
 * <p>Each crossarm is two pixels wide against the post's four, so it passes <em>through</em> the
 * post rather than butting against it, and the crossing arm is split in two so the two never
 * overlap. Nothing here shares a plane with anything else facing the same way - faces that do
 * z-fight, which looks like the model flickering. Vanilla's {@code fence_side} is built this way
 * for the same reason.
 */
public enum PolePart implements StringRepresentable {

    /** The foot: a flared base, so the pole looks planted rather than dropped. */
    FOOT("foot", "foot",
            new float[][] {
                {5, 0, 5, 11, 3, 11},
                {6, 3, 6, 10, 16, 10},
            },
            new float[][] {
                {5, 0, 5, 11, 3, 11},
                {6, 3, 6, 10, 16, 10},
            }),

    /** Plain post. Height, and nothing else. */
    SHAFT_LOWER("shaft_lower", "shaft", PoleShapes.SHAFT, PoleShapes.SHAFT),

    /** The same again - the two are one model and one shape. */
    SHAFT_UPPER("shaft_upper", "shaft", PoleShapes.SHAFT, PoleShapes.SHAFT),

    /**
     * Post, crossarm and cap, which is what makes it read as a power pole rather than a fence.
     *
     * <p>One arm each way at one height, so the pole needs no facing property and looks the same
     * from any angle - a distribution pole with a square crossarm, which is what Factorio's small
     * pole is. The wires attach here; see {@code PoleWireRenderer}.
     */
    HEAD("head", "head",
            new float[][] {
                {6, 0, 6, 10, 13, 10},
                {1, 10, 7, 15, 12, 9},
                {7, 10, 1, 9, 12, 7},
                {7, 10, 9, 9, 12, 15},
                {5, 13, 5, 11, 16, 11},
            },
            PoleShapes.SHAFT);

    /** Shared arrays, so the two shaft parts are one shape rather than two that agree today. */
    private static final class PoleShapes {
        private static final float[][] SHAFT = {{6, 0, 6, 10, 16, 10}};

        private PoleShapes() {}
    }

    private final String name;
    private final String model;
    private final float[][] boxes;
    private final VoxelShape shape;
    private final VoxelShape collisionShape;

    PolePart(String name, String model, float[][] boxes, float[][] collisionBoxes) {
        this.name = name;
        this.model = model;
        this.boxes = boxes;
        this.shape = build(boxes);
        this.collisionShape = boxes == collisionBoxes ? this.shape : build(collisionBoxes);
    }

    private static VoxelShape build(float[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (float[] box : boxes) {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return shape;
    }

    /** Model pixels, {@code {x1, y1, z1, x2, y2, z2}} per box. Read by the model provider. */
    public float[][] boxes() {
        return boxes;
    }

    /**
     * The name of the model this part draws with. Two parts share one - see {@link #SHAFT_UPPER}.
     */
    public String modelName() {
        return model;
    }

    /** What you see, and what the selection outline traces. */
    public VoxelShape shape() {
        return shape;
    }

    /** What you bump into. The post, never the arms. */
    public VoxelShape collisionShape() {
        return collisionShape;
    }

    /** How far above the foot of the pole this part sits. */
    public int height() {
        return ordinal();
    }

    /** The part that must be directly above this one, or null if this is the head. */
    public @Nullable PolePart above() {
        return this == HEAD ? null : values()[ordinal() + 1];
    }

    /** The part that must be directly below this one, or null if this is the foot. */
    public @Nullable PolePart below() {
        return this == FOOT ? null : values()[ordinal() - 1];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
