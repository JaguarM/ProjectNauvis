package com.jaguarm.nauvispower.grid;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which slice of a three-block pole a given block is, and what that slice is made of.
 *
 * <p>A pole is a multi-block in the way a door is: one block id, one item, one
 * {@code EnumProperty}, and the whole thing placed and broken as a unit. Only {@link #BOTTOM}
 * carries the block entity and therefore the network membership; the other two are structure.
 *
 * <p><b>The boxes here are the only description of a pole's shape.</b> The block builds its
 * {@code VoxelShape} from them and the model provider builds the model's elements from them, so
 * the thing you can hit and the thing you can see are the same numbers rather than two lists that
 * drift. They are in model pixels - sixteen to a block - because that is the unit the model
 * format uses and converting once here is cheaper than converting in two places.
 *
 * <h2>Why the arms are thinner than the post</h2>
 *
 * <p>Each crossarm is two pixels wide against the post's four, so it passes <em>through</em> the
 * post rather than butting against it and no two faces end up coplanar. Coplanar faces z-fight,
 * which looks like the model flickering. Vanilla's {@code fence_side} is built the same way and
 * for the same reason. The two arms sit at different heights for the same reason again.
 */
public enum PolePart implements StringRepresentable {

    /** The foot: a flared base so the pole looks planted rather than dropped. */
    BOTTOM("bottom", new float[][] {
        {5, 0, 5, 11, 3, 11},
        {6, 3, 6, 10, 16, 10},
    }),

    /** Plain post. Height, and nothing else. */
    MIDDLE("middle", new float[][] {
        {6, 0, 6, 10, 16, 10},
    }),

    /**
     * Post and two crossarms, which is what makes it read as a power pole rather than a fence.
     *
     * <p>One arm each way, so the pole needs no facing property and looks the same from any
     * angle - a distribution pole with a square crossarm, which is what Factorio's small pole is.
     */
    TOP("top", new float[][] {
        {6, 0, 6, 10, 16, 10},
        {1, 10, 7, 15, 12, 9},
        {7, 12, 1, 9, 14, 15},
    });

    private final String name;
    private final float[][] boxes;
    private final VoxelShape shape;

    PolePart(String name, float[][] boxes) {
        this.name = name;
        this.boxes = boxes;
        this.shape = build(boxes);
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

    public VoxelShape shape() {
        return shape;
    }

    /** How far above the bottom of the pole this part sits. */
    public int height() {
        return ordinal();
    }

    /** The part that must be directly above this one, or null if this is the top. */
    public @Nullable PolePart above() {
        return this == TOP ? null : values()[ordinal() + 1];
    }

    /** The part that must be directly below this one, or null if this is the bottom. */
    public @Nullable PolePart below() {
        return this == BOTTOM ? null : values()[ordinal() - 1];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
