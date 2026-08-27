package com.jaguarm.nauvisresearch.multiblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * What shape a machine is: which blocks it occupies, which one holds everything, and where the
 * player's click lands.
 *
 * <p>One of these per machine, built once in a static initialiser and read by everything else -
 * the block for placement and collision, the model provider for geometry, the loot table for
 * which cell drops the item. A footprint is Factorio identity (see {@code docs/NEXT.md}), so it
 * is stated once and never restated.
 *
 * <h2>Cells are a set, not a box</h2>
 *
 * <p>A 3x3 machine two blocks tall is not necessarily eighteen blocks. The assembler is nine at
 * ground level and one more for the gearbox on top, because the rest of its upper storey is air
 * you can walk through - which is the whole reason a field of assemblers stays crossable. A
 * boiler will be six with a chimney on one tile. Stating the occupied cells rather than a
 * bounding box is what makes that possible, and it costs nothing.
 *
 * <p>The set must be <b>orthogonally connected</b>, and the constructor throws if it is not.
 * That is not tidiness: the teardown rule in {@link Multiblock} propagates from cell to touching
 * cell, so an island would survive its own machine being broken.
 *
 * <h2>Where the anchor is, and why finding it costs nothing</h2>
 *
 * <p>One cell is the anchor. It carries the block entity, the loot table entry and the menu; the
 * rest are structure. Every cell stores <em>which cell it is</em> in an {@code IntegerProperty},
 * so the anchor's position is this cell's position minus its own offset - arithmetic on a
 * blockstate the caller already has, with no block read and no block entity on the other cells.
 * Nine block entities per machine, in a base of thousands of machines, to hold a number that is
 * already in the blockstate would be a poor trade.
 *
 * <h2>Rotation</h2>
 *
 * <p>Cells are stated in the machine's north-facing frame and turned by {@link Boxes}' single
 * rotation, the same one the geometry uses. A machine with no facing - a Factorio assembler has
 * none, because what goes in and what comes out is decided by the inserters around it - simply
 * never asks for anything but north.
 */
public final class MachineShape {

    private final List<MachineCell> cells;
    private final int anchor;
    private final int placement;
    private final IntegerProperty part;

    /** Offsets from the anchor cell, pre-rotated: {@code [part][facing ordinal]}. */
    private final Map<Direction, Vec3i[]> offsets = new HashMap<>();

    private final int width;
    private final int height;
    private final int depth;

    /**
     * One face of one cell, in the machine's own north-facing frame.
     *
     * <p>What a port is <em>for</em> is the machine's business - {@link MachineShape} only knows
     * that some faces are named and most are not.
     */
    public record Port(int part, Direction side) {}

    private final Map<String, Set<Port>> ports;

    public MachineShape(List<MachineCell> cells, int anchor, int placement) {
        this(cells, anchor, placement, Map.of());
    }

    /**
     * @param anchor    index of the cell carrying the block entity
     * @param placement index of the cell that lands on the block the player clicked - the middle
     *                  of the footprint for an odd one, so a 3x3 centres on the cursor the way
     *                  Factorio's ghost does
     * @param ports     named sets of faces that offer something, in the north-facing frame. This
     *                  is what a footprint buys that a cube could not: a boiler's steam leaves one
     *                  named block rather than all six sides of a machine, so where you put your
     *                  pipe is a thing you can be right or wrong about. See
     *                  {@link #hasPort(String, int, Direction, Direction)}
     */
    public MachineShape(List<MachineCell> cells, int anchor, int placement,
            Map<String, Set<Port>> ports) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a machine occupies at least one block");
        }
        if (anchor < 0 || anchor >= cells.size() || placement < 0 || placement >= cells.size()) {
            throw new IllegalArgumentException("anchor and placement must name cells that exist");
        }

        Set<Vec3i> seen = new HashSet<>();
        for (MachineCell cell : cells) {
            if (!seen.add(new Vec3i(cell.x(), cell.y(), cell.z()))) {
                throw new IllegalArgumentException(
                        "two cells at " + cell.x() + "," + cell.y() + "," + cell.z());
            }
            if (cell.boxes().length == 0) {
                throw new IllegalArgumentException(
                        "cell " + cell.x() + "," + cell.y() + "," + cell.z() + " draws nothing - "
                                + "a cell with no boxes is an invisible block you fall over");
            }
        }
        requireConnected(seen);

        this.cells = List.copyOf(cells);
        this.anchor = anchor;
        this.placement = placement;
        // Named for the block, so two machines' properties are distinct objects with the same
        // name - which is what a blockstate file wants.
        this.part = IntegerProperty.create("part", 0, cells.size() - 1);

        MachineCell origin = this.cells.get(anchor);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3i[] turned = new Vec3i[this.cells.size()];
            for (int index = 0; index < this.cells.size(); index++) {
                MachineCell cell = this.cells.get(index);
                turned[index] = rotate(
                        cell.x() - origin.x(), cell.y() - origin.y(), cell.z() - origin.z(), facing);
            }
            offsets.put(facing, turned);
        }

        for (Set<Port> named : ports.values()) {
            for (Port port : named) {
                if (port.part() < 0 || port.part() >= this.cells.size()) {
                    throw new IllegalArgumentException("a port on cell " + port.part()
                            + ", which this machine does not have");
                }
            }
        }
        this.ports = Map.copyOf(ports);

        this.width = extent(this.cells, MachineCell::x);
        this.height = extent(this.cells, MachineCell::y);
        this.depth = extent(this.cells, MachineCell::z);
    }

    private interface Axis {
        int of(MachineCell cell);
    }

    private static int extent(List<MachineCell> cells, Axis axis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (MachineCell cell : cells) {
            min = Math.min(min, axis.of(cell));
            max = Math.max(max, axis.of(cell));
        }
        return max - min + 1;
    }

    /**
     * A flood fill from one cell. Anything it does not reach cannot be told its machine has been
     * broken, so it would be left behind as an indestructible-looking block.
     */
    private static void requireConnected(Set<Vec3i> positions) {
        Deque<Vec3i> queue = new ArrayDeque<>();
        Set<Vec3i> reached = new HashSet<>();
        Vec3i first = positions.iterator().next();
        queue.add(first);
        reached.add(first);
        while (!queue.isEmpty()) {
            Vec3i at = queue.poll();
            for (Direction direction : Direction.values()) {
                Vec3i next = at.offset(direction.getUnitVec3i());
                if (positions.contains(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (reached.size() != positions.size()) {
            throw new IllegalArgumentException(
                    "a machine's cells must touch face to face: " + (positions.size() - reached.size())
                            + " of " + positions.size() + " are cut off from the rest, and the "
                            + "teardown rule would leave them standing");
        }
    }

    /** The one rotation, on an offset between blocks. See {@link Boxes} for the geometry half. */
    private static Vec3i rotate(int x, int y, int z, Direction facing) {
        return switch (Boxes.quarterTurns(facing)) {
            case 1 -> new Vec3i(-z, y, x);
            case 2 -> new Vec3i(-x, y, -z);
            case 3 -> new Vec3i(z, y, -x);
            default -> new Vec3i(x, y, z);
        };
    }

    public List<MachineCell> cells() {
        return cells;
    }

    public MachineCell cell(int part) {
        return cells.get(part);
    }

    public int cellCount() {
        return cells.size();
    }

    /** Which cell this is. Every block of the machine carries it, the anchor included. */
    public IntegerProperty part() {
        return part;
    }

    public int anchor() {
        return anchor;
    }

    public int placement() {
        return placement;
    }

    /** Extent in blocks, used to scale the machine down to one block for the item model. */
    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /** Where this cell sits relative to the anchor, for a machine facing this way. */
    public Vec3i offset(int part, Direction facing) {
        return offsets.get(facing)[part];
    }

    /** Where this cell of a machine anchored here goes. */
    public BlockPos cellPos(BlockPos anchorPos, int part, Direction facing) {
        return anchorPos.offset(offset(part, facing));
    }

    /** Where the anchor of the machine this cell belongs to is. No block read, no lookup. */
    public BlockPos anchorPos(BlockPos pos, int part, Direction facing) {
        return pos.subtract(offset(part, facing));
    }

    /**
     * Whether this face of this cell carries the named port, for a machine turned this way.
     *
     * <p>{@code side} may be null, which is NeoForge's "from nowhere in particular" - a query with
     * no side is answered yes, the way a block entity with sided handlers still has an unsided one.
     * Otherwise the world-space face is turned back into the machine's own frame and looked up,
     * which is the same rotation everything else here uses, run backwards.
     */
    public boolean hasPort(String name, int part, @Nullable Direction side, Direction facing) {
        if (side == null) {
            return true;
        }
        Set<Port> named = ports.get(name);
        return named != null && named.contains(new Port(part, toLocal(side, facing)));
    }

    /** A world-space face, expressed in the machine's own north-facing frame. */
    public static Direction toLocal(Direction side, Direction facing) {
        if (side.getAxis() == Direction.Axis.Y) {
            return side;
        }
        Direction local = side;
        for (int turn = Boxes.quarterTurns(facing); turn > 0; turn--) {
            local = local.getCounterClockWise();
        }
        return local;
    }

    /**
     * A face of the machine's own frame, expressed in the world. The inverse of {@link #toLocal}.
     *
     * <p>What a machine wants this for is usually the block just outside one of its ports: take
     * the port's cell with {@link #cellPos}, turn the port's face out into the world with this,
     * and step one block along it.
     */
    public static Direction toWorld(Direction local, Direction facing) {
        if (local.getAxis() == Direction.Axis.Y) {
            return local;
        }
        Direction side = local;
        for (int turn = Boxes.quarterTurns(facing); turn > 0; turn--) {
            side = side.getClockWise();
        }
        return side;
    }

    /** Every block a machine anchored here would occupy, anchor included. */
    public List<BlockPos> positions(BlockPos anchorPos, Direction facing) {
        List<BlockPos> positions = new ArrayList<>(cells.size());
        for (int index = 0; index < cells.size(); index++) {
            positions.add(cellPos(anchorPos, index, facing));
        }
        return positions;
    }
}
