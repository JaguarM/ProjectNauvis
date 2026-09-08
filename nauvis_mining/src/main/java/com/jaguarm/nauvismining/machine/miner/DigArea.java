package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The columns a drill works: its own footprint, grown outward a ring at a time. */
public record DigArea(int minX, int minZ, int maxX, int maxZ, int y, int rings) {

    /**
     * One column of the area, in world coordinates. {@code y} on the area itself is the machine's
     * own level - digging starts one block under it - which is what the preview draws its lid at.
     */
    public record Column(int x, int z) {}

    /**
     * The area a machine of this shape covers, standing here and facing this way.
     *
     * <p>Taken from the cells themselves rather than from {@code width()} and {@code depth()}, so
     * a machine whose footprint is not a solid rectangle still gets the rectangle that contains
     * it, and a machine that is turned gets the turned footprint.
     */
    public static DigArea of(MachineShape shape, BlockPos anchorPos, Direction facing, int rings) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : shape.positions(anchorPos, facing)) {
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new DigArea(minX, minZ, maxX, maxZ, anchorPos.getY(), Math.max(0, rings));
    }

    /** Width of the footprint alone, in columns. */
    public int footprintWidth() {
        return maxX - minX + 1;
    }

    /** Depth of the footprint alone, in columns. */
    public int footprintDepth() {
        return maxZ - minZ + 1;
    }

    public int outerMinX() {
        return minX - rings;
    }

    public int outerMinZ() {
        return minZ - rings;
    }

    public int outerMaxX() {
        return maxX + rings;
    }

    public int outerMaxZ() {
        return maxZ + rings;
    }

    /** Every column the drill will visit, footprint and rings together. */
    public int columns() {
        return (footprintWidth() + 2 * rings) * (footprintDepth() + 2 * rings);
    }

    /**
     * The column at a 1-based index, or null past the end of the area.
     *
     * <p>One-based because that is what a drill's saved {@code ColumnIndex} has always been, and a
     * drill in the ground is holding one.
     */
    public Column column(int index) {
        if (index < 1 || index > columns()) {
            return null;
        }

        int remaining = index - 1;
        int width = footprintWidth();
        int depth = footprintDepth();

        // Ring 0 is the whole footprint, in rows; every ring after it is a border.
        if (remaining < width * depth) {
            return new Column(minX + remaining % width, minZ + remaining / width);
        }
        remaining -= width * depth;

        for (int ring = 1; ring <= rings; ring++) {
            int ringWidth = width + 2 * ring;
            int ringDepth = depth + 2 * ring;
            int size = 2 * ringWidth + 2 * (ringDepth - 2);
            if (remaining >= size) {
                remaining -= size;
                continue;
            }

            int x0 = minX - ring;
            int z0 = minZ - ring;
            // The two full rows first, then what is left of the two sides between them.
            if (remaining < ringWidth) {
                return new Column(x0 + remaining, z0);
            }
            remaining -= ringWidth;
            if (remaining < ringWidth) {
                return new Column(x0 + remaining, z0 + ringDepth - 1);
            }
            remaining -= ringWidth;
            int interior = ringDepth - 2;
            return remaining < interior
                    ? new Column(x0, z0 + 1 + remaining)
                    : new Column(x0 + ringWidth - 1, z0 + 1 + remaining - interior);
        }
        return null;
    }
}
