package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvislogistics.net.BeltItemAddedPayload;
import com.jaguarm.nauvislogistics.net.BeltItemRemovedPayload;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * One belt line: the blocks it runs over, the two lanes on it, and everything on those lanes.
 *
 * <p><b>A run is one object however long it is, and items on it are positions rather than
 * entities.</b> That is the model Factorio uses to move millions of items, it is the model Create
 * arrived at, and it is the one this pack settled on over the block-entity-per-belt shortcut
 * {@code PLAN.md} originally allowed. The two reasons are the same two the pipe run gives: a run
 * ticks once whatever its length, and an item crosses it at the belt's speed rather than at one
 * block a tick.
 *
 * <p>Never an {@code ItemEntity}. An entity costs a tick, a chunk membership, collision and a
 * network presence each, cannot be compressed against its neighbour, and cannot be on a lane.
 * Immersive Engineering's conveyors do move real entities, and reading them is the quickest way to
 * see why its conveyors are not Factorio belts.
 *
 * <h2>Where a run starts and stops</h2>
 *
 * <p>A belt feeds the block it faces. A run follows that link for as long as it is unambiguous:
 * the moment a belt has <em>two</em> belts feeding it, it begins a run of its own and the feeders
 * hand off into it. So a line that turns a corner is one run - a curve has one feeder - and a
 * merge is two runs meeting a third, which is exactly what a merge is. A run never spans two
 * tiers, because {@link BeltBlock} makes speed a property of the block.
 *
 * <h2>The same simulation runs on the client</h2>
 *
 * <p>The client builds runs too, out of the same block states, and advances them with this same
 * code. That is what makes visible moving items affordable: the alternative is telling every
 * client where every item is, every tick, which is a packet per item per tick and the reason most
 * mods draw belts badly.
 *
 * <p>Everything the two sides do from the same information they both hold - moving, jamming,
 * handing off between runs - needs no message at all. Only the boundary with the rest of the world
 * does: an inserter putting something on, or taking something off. Those two go out as
 * {@link BeltItemAddedPayload} and {@link BeltItemRemovedPayload}, once each, when the transaction
 * that caused them commits.
 *
 * <h2>Transactions</h2>
 *
 * <p>An inserter moves an item by extracting and inserting inside one transaction, and rolls the
 * whole thing back if either half fails. So a belt has to be able to un-accept an item. The
 * journal below is that: every change made inside a transaction records how to undo itself, a
 * snapshot is just a mark in that list, and rolling back is popping down to the mark.
 */
public final class BeltRun extends SnapshotJournal<Integer> {

    private final Level level;
    private final BeltLines lines;
    private final BeltBlock block;
    private final int speed;

    /** The blocks, from the tail an item enters at to the head it leaves from. */
    private final List<BlockPos> blocks;

    /** Each block's direction of travel, read once so the tick never touches a block state. */
    private final Direction[] facings;

    private final Long2IntOpenHashMap indexByBlock = new Long2IntOpenHashMap();

    private final BeltLane[] lanes = {new BeltLane(), new BeltLane()};

    /**
     * Whether the head of this run feeds its own tail: four belts turning in a square, or any
     * longer loop.
     *
     * <p>A loop has no beginning, so the run is broken open at an arbitrary block and the two ends
     * are joined back up here. Two things then have to know: an item leaving the head wraps round
     * to the tail rather than being handed to a run, and the tail's incoming edge is the one it
     * shares with the head rather than the one behind it.
     */
    private final boolean loops;

    /** Undo entries for the transaction in progress, and the messages it would send if it holds. */
    private final List<Op> journal = new ArrayList<>();

    private boolean dissolved;

    BeltRun(Level level, BeltLines lines, BeltBlock block, List<BlockPos> blocks, Direction[] facings,
            boolean loops) {
        this.level = level;
        this.lines = lines;
        this.block = block;
        this.speed = block.speed();
        this.blocks = List.copyOf(blocks);
        this.facings = facings;
        this.loops = loops;
        indexByBlock.defaultReturnValue(-1);
        for (int i = 0; i < this.blocks.size(); i++) {
            indexByBlock.put(this.blocks.get(i).asLong(), i);
        }
    }

    /** One change made inside a transaction: how to undo it, and what to tell clients if it holds. */
    private record Op(int lane, int index, @Nullable ItemResource item, int slack, boolean inserted,
            BlockPos at, CustomPacketPayload message) {}

    // --- shape ---------------------------------------------------------------------------------

    public List<BlockPos> blocks() {
        return blocks;
    }

    public BeltBlock block() {
        return block;
    }

    public int speed() {
        return speed;
    }

    public BeltLane lane(int lane) {
        return lanes[lane];
    }

    public boolean isDissolved() {
        return dissolved;
    }

    /** Whether this run's head feeds its own tail - a closed loop of belts. */
    public boolean loops() {
        return loops;
    }

    void dissolve() {
        dissolved = true;
    }

    public int itemCount() {
        return lanes[Belts.LEFT].size() + lanes[Belts.RIGHT].size();
    }

    public boolean isEmpty() {
        return itemCount() == 0;
    }

    /** How long the run is, in {@link Belts#UNITS_PER_BLOCK}ths of a block. */
    public int length() {
        return blocks.size() * Belts.UNITS_PER_BLOCK;
    }

    public int indexOf(BlockPos pos) {
        return indexByBlock.get(pos.asLong());
    }

    public Direction travelAt(int index) {
        return facings[index];
    }

    /** The distance from the end of the run of the forward edge of block {@code index}. */
    public int frontEdge(int index) {
        return (blocks.size() - 1 - index) * Belts.UNITS_PER_BLOCK;
    }

    /** Which block a distance from the end of the run falls on. */
    public int blockAt(int position) {
        int fromHead = Math.min(position / Belts.UNITS_PER_BLOCK, blocks.size() - 1);
        return blocks.size() - 1 - fromHead;
    }

    /**
     * Where an item at {@code position} is in the world, in level coordinates.
     *
     * <p>Within a block an item goes from the edge it came in at, through the middle, to the edge
     * it leaves by. On a straight belt that is a straight line; on a corner it is the corner,
     * which is why a run that turns needs nothing else to make its items turn.
     */
    public Vec3 pointAt(double position, int lane) {
        int index = blockAt((int) position);
        BlockPos pos = blocks.get(index);
        Direction out = facings[index];
        Direction in = entrySide(index);

        // 0 at the back edge of the block, 1 at the forward edge.
        double progress = 1.0 - (position - frontEdge(index)) / Belts.UNITS_PER_BLOCK;
        progress = Math.clamp(progress, 0.0, 1.0);

        Vec3 middle = new Vec3(pos.getX() + 0.5, pos.getY() + Belts.HEIGHT, pos.getZ() + 0.5);
        Direction travel;
        Vec3 point;
        if (progress < 0.5) {
            travel = in.getOpposite();
            Vec3 entry = middle.add(in.getStepX() * 0.5, 0, in.getStepZ() * 0.5);
            point = entry.lerp(middle, progress * 2.0);
        } else {
            travel = out;
            Vec3 exit = middle.add(out.getStepX() * 0.5, 0, out.getStepZ() * 0.5);
            point = middle.lerp(exit, (progress - 0.5) * 2.0);
        }

        Direction side = Belts.sideOf(travel, lane);
        return point.add(side.getStepX() * Belts.LANE_OFFSET, 0, side.getStepZ() * Belts.LANE_OFFSET);
    }

    /**
     * Which edge of block {@code index} items arrive over.
     *
     * <p>The edge shared with the block before it - or, on a closed loop, the edge the tail shares
     * with the head, because a loop's first block is only first by an arbitrary choice of where to
     * break the ring open. Getting this wrong on a loop puts the tail's entry on its outside edge,
     * so items appear from nowhere in the middle of the block instead of coming round the corner.
     */
    private Direction entrySide(int index) {
        if (index > 0) {
            return directionBetween(blocks.get(index), blocks.get(index - 1));
        }
        if (loops) {
            return directionBetween(blocks.get(0), blocks.get(blocks.size() - 1));
        }
        return facings[0].getOpposite();
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        return Direction.getApproximateNearest(
                to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    // --- the tick ------------------------------------------------------------------------------

    /**
     * Hands off whatever has reached the end, then moves everything up.
     *
     * <p>Handing off first rather than after means an item that leaves this tick makes room this
     * tick, so a corner does not cost a tick that a straight belt does not.
     *
     * @return whether anything moved, which is only used for the readout - a run stays awake while
     *         it has anything on it at all. A jammed run costs almost nothing; see {@link BeltLane}.
     */
    boolean tick() {
        boolean moved = false;
        for (int lane = 0; lane < Belts.LANES; lane++) {
            if (lanes[lane].lead() == 0) {
                handOff(lane);
            }
            moved |= lanes[lane].advance(speed);
        }
        return moved;
    }

    /**
     * Gives the leading item to whatever the head of the run points at, if that is another run.
     *
     * <p><b>A belt does not load a chest.</b> In Factorio a belt running into a container simply
     * backs up, and moving things off a belt is what inserters are for. Doing it the Minecraft way
     * instead would make half the inserters in a base pointless, so the belt offers items to
     * nothing: it publishes {@code Capabilities.Item.BLOCK} and waits to be asked. See
     * {@link BeltAccess}.
     */
    private void handOff(int lane) {
        if (loops) {
            wrapRound(lane);
            return;
        }
        int head = blocks.size() - 1;
        Direction out = facings[head];
        BlockPos target = blocks.get(head).relative(out);

        BeltRun other = lines.runAt(target);
        if (other == null || other.dissolved) {
            return;
        }
        int index = other.indexOf(target);
        if (index < 0) {
            return;
        }

        Direction from = out.getOpposite();
        Direction travel = other.facings[index];
        // Straight in at the back of the tile; onto the middle of it from a side.
        boolean behind = from == travel.getOpposite();
        int position = other.frontEdge(index)
                + (behind ? Belts.UNITS_PER_BLOCK : Belts.UNITS_PER_BLOCK / 2);
        int targetLane = behind ? lane : Belts.laneFor(travel, from);
        if (targetLane < 0) {
            // Head to head. The two belts face each other and neither can give way.
            return;
        }

        if (!other.lanes[targetLane].hasRoomAt(position)) {
            return;
        }
        other.lanes[targetLane].insertAt(position, lanes[lane].removeAt(0));
        lines.markActive(other);

        // An item has left one chunk and joined another, and either may be saved without the
        // other. Both ends say so; a run moving items along itself does not.
        markChanged(blocks.get(head));
        other.markChanged(target);
    }

    /**
     * Carries the leading item off the head of a loop and back on at its tail.
     *
     * <p>The two are the same face, so nothing about this is a jump: the item crosses one edge, as
     * it does at every other block boundary on the run. It keeps its lane, because going round a
     * corner does not swap a Factorio belt's lanes over.
     *
     * <p>Nothing here talks to another run, which is what makes a loop cheap: a ring of belts is
     * one object that never hands anything to anyone.
     */
    private void wrapRound(int lane) {
        if (!lanes[lane].hasRoomAt(length())) {
            // The ring is full, all the way round to its own tail. It jams, as it should.
            return;
        }
        lanes[lane].insertAt(length(), lanes[lane].removeAt(0));
    }

    // --- what the world puts on and takes off ---------------------------------------------------

    /**
     * Puts one item on the belt at {@code block}, inside a transaction.
     *
     * <p>Four places are tried, back of the tile first, which is the quarter-tile grid Factorio
     * itself spaces items on. Back first because an item dropped at the near edge then rides the
     * whole tile, which is what an inserter looks like it is doing.
     *
     * @return whether it went on.
     */
    boolean insert(BlockPos block, int lane, ItemResource item, TransactionContext transaction) {
        int index = indexOf(block);
        if (index < 0) {
            return false;
        }
        int edge = frontEdge(index);
        for (int slot = 3; slot >= 0; slot--) {
            int position = edge + slot * Belts.SPACING;
            if (!lanes[lane].hasRoomAt(position)) {
                continue;
            }
            updateSnapshots(transaction);
            int at = lanes[lane].insertAt(position, item);
            journal.add(new Op(lane, at, null, 0, true, block,
                    new BeltItemAddedPayload(block, lane, position - edge, item)));
            return true;
        }
        return false;
    }

    /** Takes the item at {@code index} of lane {@code lane} off, inside a transaction. */
    void extract(int lane, int index, TransactionContext transaction) {
        updateSnapshots(transaction);
        int position = lanes[lane].position(index);
        int slack = lanes[lane].slackAt(index);
        ItemResource item = lanes[lane].removeAt(index);

        int on = blockAt(position);
        journal.add(new Op(lane, index, item, slack, false, blocks.get(on),
                new BeltItemRemovedPayload(blocks.get(on), lane, position - frontEdge(on))));
    }

    @Override
    protected Integer createSnapshot() {
        return journal.size();
    }

    @Override
    protected void revertToSnapshot(Integer mark) {
        while (journal.size() > mark) {
            Op op = journal.remove(journal.size() - 1);
            if (op.inserted()) {
                lanes[op.lane()].removeAt(op.index());
            } else {
                lanes[op.lane()].restoreAt(op.index(), op.item(), op.slack());
            }
        }
    }

    /**
     * The transaction held, so the changes are real and the clients need telling.
     *
     * <p>This is the only place in the belt that sends anything. Everything else either side does
     * is worked out from information both already have.
     */
    @Override
    protected void onRootCommit(Integer original) {
        for (Op op : journal) {
            if (level instanceof ServerLevel server) {
                PacketDistributor.sendToPlayersTrackingChunk(
                        server, new ChunkPos(op.at().getX() >> 4, op.at().getZ() >> 4), op.message());
            }
            markChanged(op.at());
        }
        journal.clear();
        lines.markActive(this);
    }

    /**
     * Puts an item straight on, outside any transaction. Loading and the client's own copy of a
     * belt use this.
     *
     * <p>The position is nudged back if something is already too close. Everything that calls this
     * is replaying items that were properly spaced when they were written down, so it should never
     * bite - but a lane whose gaps went negative would break the one invariant
     * {@link BeltLane#advance(int)} rests on, and a saved file is not something to take on trust.
     */
    void place(int lane, int position, ItemResource item) {
        int index = lanes[lane].indexFor(position);
        int at = position;
        if (index > 0) {
            at = Math.max(at, lanes[lane].position(index - 1) + Belts.SPACING);
        }
        if (at > length()) {
            return;
        }
        lanes[lane].insertAt(at, item);
    }

    // --- coming apart and going back together ---------------------------------------------------

    /** Where one item was, as a block and an offset into it - which survives the run changing. */
    public record Parked(BlockPos block, int lane, int offset, ItemResource item) {}

    /**
     * Lifts every item off, saying which block and where on it each one was.
     *
     * <p><b>This is what makes placing and breaking belts safe.</b> A run is rebuilt from scratch
     * whenever the belts around it change - two runs become one, one becomes two, a chunk arrives
     * with more of it - and every one of those moves the point distances are measured from. An
     * item pinned to a block and an offset into that block does not care: it is put back exactly
     * where it was standing, on whatever run now owns that block.
     *
     * <p>It is also why the client needs no message when a player edits a belt line. Both sides
     * lift, rebuild and replace by the same rule, from block states they both already have.
     */
    List<Parked> park() {
        List<Parked> parked = new ArrayList<>(itemCount());
        for (int lane = 0; lane < Belts.LANES; lane++) {
            var positions = lanes[lane].positions();
            for (int i = 0; i < positions.size(); i++) {
                int position = positions.getInt(i);
                int on = blockAt(position);
                parked.add(new Parked(blocks.get(on), lane, position - frontEdge(on), lanes[lane].item(i)));
            }
            lanes[lane].clear();
        }
        return parked;
    }

    /** Puts back items from {@link #park()}, in order, never closer together than they may be. */
    void unpark(List<Parked> parked) {
        int[] behind = {-1, -1};
        for (Parked item : parked) {
            int index = indexOf(item.block());
            if (index < 0) {
                continue;
            }
            int position = frontEdge(index) + item.offset();
            if (behind[item.lane()] >= 0) {
                position = Math.max(position, behind[item.lane()] + Belts.SPACING);
            }
            if (position > length()) {
                continue;
            }
            lanes[item.lane()].insertAt(position, item.item());
            behind[item.lane()] = position;
        }
    }

    /** Everything standing on one block, leading item of each lane first. */
    public List<Parked> itemsOn(BlockPos block) {
        int index = indexOf(block);
        if (index < 0) {
            return List.of();
        }
        int edge = frontEdge(index);
        List<Parked> found = new ArrayList<>();
        for (int lane = 0; lane < Belts.LANES; lane++) {
            var positions = lanes[lane].positions();
            for (int i = 0; i < positions.size(); i++) {
                int position = positions.getInt(i);
                if (blockAt(position) == index) {
                    found.add(new Parked(block, lane, position - edge, lanes[lane].item(i)));
                }
            }
        }
        return found;
    }

    /** Lifts everything off one block, for a belt that is being broken or re-read from disk. */
    List<Parked> takeOn(BlockPos block) {
        int index = indexOf(block);
        if (index < 0) {
            return List.of();
        }
        int edge = frontEdge(index);
        List<Parked> taken = new ArrayList<>();
        for (int lane = 0; lane < Belts.LANES; lane++) {
            // A copy: the lane's own list is thrown away by the first removal below.
            var positions = new IntArrayList(lanes[lane].positions());
            // Backwards, so removing one does not move the index of the next to remove.
            for (int i = positions.size() - 1; i >= 0; i--) {
                int position = positions.getInt(i);
                if (blockAt(position) == index) {
                    taken.add(new Parked(block, lane, position - edge, lanes[lane].removeAt(i)));
                }
            }
        }
        return taken;
    }

    /** The index within its lane of the item nearest {@code offset} on {@code block}. */
    int indexOn(BlockPos block, int lane, int offset) {
        int index = indexOf(block);
        if (index < 0) {
            return -1;
        }
        int wanted = frontEdge(index) + offset;
        var positions = lanes[lane].positions();
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < positions.size(); i++) {
            int distance = Math.abs(positions.getInt(i) - wanted);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return bestDistance <= Belts.UNITS_PER_BLOCK ? best : -1;
    }

    /**
     * Marks one block of the run as having changed, so its chunk is saved.
     *
     * <p>Only the block an item actually arrived at or left from, and only when something crossed
     * the boundary between the belt and the rest of the world. Movement along a run does not do
     * this: an item shuffling forward twenty times a second is not twenty reasons to save a chunk,
     * and {@code setChanged} is not free - it notifies all six neighbours, which is the signal
     * every inserter in the pack listens to.
     */
    void markChanged(BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt) {
            belt.setChanged();
        }
    }
}
