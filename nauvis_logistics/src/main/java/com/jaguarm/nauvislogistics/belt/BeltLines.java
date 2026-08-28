package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Every belt run in one level, and the only thing in this mod that runs every tick.
 *
 * <p>It iterates <em>runs</em> that have something on them, of which a base has a handful, rather
 * than belts, of which it has thousands. An empty run costs nothing at all, and - unlike the pipe
 * and power networks, which re-check dormant members every ten ticks - a run that is jammed rather
 * than empty stays awake, because {@link BeltLane} makes a jam cost nothing to tick. Being awake
 * is about having items, not about moving them, which is why belts need no wake-up plumbing.
 *
 * <p>This is {@code FluidNetworkManager} with the nouns changed, and the third time this pack has
 * needed the shape. Two things here are new:
 *
 * <ul>
 *   <li><b>It runs on the client too.</b> A run is derived from block states, and a client has
 *       those, so it can build the same runs and move the same items - which is how belts get
 *       visibly moving items without a packet per item per tick. See {@link BeltRun}.</li>
 *   <li><b>Runs are directed.</b> A pipe joins the six blocks it touches and the graph is a set of
 *       edges; a belt line is a <em>sequence</em>, and merging or splitting one moves the point
 *       every distance on it is measured from. That is what {@link BeltRun#park()} is for.</li>
 * </ul>
 */
public final class BeltLines {

    private static final Map<Level, BeltLines> LINES = new IdentityHashMap<>();

    private final Level level;

    /** Every loaded belt, whether or not it is in a run yet. */
    private final LongOpenHashSet belts = new LongOpenHashSet();

    private final Long2ObjectOpenHashMap<BeltRun> runByBelt = new Long2ObjectOpenHashMap<>();

    /** Runs with something on them. The rest are not visited. */
    private final Set<BeltRun> active = new LinkedHashSet<>();

    /** Reused every tick: a run may put an item on another run, which touches {@link #active}. */
    private final List<BeltRun> ticking = new ArrayList<>();

    private BeltLines(Level level) {
        this.level = level;
    }

    public static BeltLines of(Level level) {
        return LINES.computeIfAbsent(level, BeltLines::new);
    }

    /** The level is going away; so is its graph. Belts rebuild it when they load again. */
    static void forget(Level level) {
        LINES.remove(level);
    }

    public @Nullable BeltRun runAt(BlockPos pos) {
        return runByBelt.get(pos.asLong());
    }

    public boolean isActive(BeltRun run) {
        return active.contains(run);
    }

    void markActive(BeltRun run) {
        if (!run.isDissolved() && !run.isEmpty()) {
            active.add(run);
        }
    }

    // --- what the world tells us ----------------------------------------------------------------

    /** A belt exists at {@code pos}. Rebuilds whatever lines it joins, splits or lengthens. */
    public void beltPlaced(BlockPos pos) {
        if (!belts.add(pos.asLong())) {
            return;
        }
        rebuildAround(pos);
    }

    /**
     * A belt is gone.
     *
     * <p>Anything that was standing on it has already been dealt with by
     * {@link BeltBlockEntity#preRemoveSideEffects}, which runs while the run still exists. What is
     * left is the line, which may now be two lines, or one shorter one, or none.
     */
    public void beltRemoved(BlockPos pos) {
        if (!belts.remove(pos.asLong())) {
            return;
        }
        rebuildAround(pos);
    }

    /**
     * A belt is still there but points somewhere else.
     *
     * <p>Nothing else notices. Turning a belt only changes a block state, so its block entity is
     * never removed and never reloaded, and neither {@link #beltPlaced} nor {@link #beltRemoved}
     * ever hears about it - while the lines through it are now entirely different lines. The items
     * on them are put back where they were standing, block by block, exactly as they are when a
     * line is cut.
     */
    public void beltTurned(BlockPos pos) {
        if (belts.contains(pos.asLong())) {
            rebuildAround(pos);
        }
    }

    // --- the tick -------------------------------------------------------------------------------

    void tick() {
        if (active.isEmpty()) {
            return;
        }
        // Copied, because a run handing an item to the next run marks that one active.
        ticking.clear();
        ticking.addAll(active);
        for (BeltRun run : ticking) {
            if (!run.isDissolved()) {
                run.tick();
            }
        }
        active.removeIf(run -> run.isDissolved() || run.isEmpty());
    }

    // --- the graph ------------------------------------------------------------------------------

    /**
     * Rebuilds every line the block at {@code around} can have changed.
     *
     * <p>Only a belt's own neighbours can gain or lose a feeder, so only the lines through those
     * five positions can be different afterwards. They are taken apart, their items lifted off by
     * block, the lines rebuilt from what is there now, and the items put back where they stood.
     *
     * <p>The order runs are rebuilt in is fixed - by packed position - so that a client doing this
     * from the same block states arrives at exactly the same answer. That is what lets a player
     * edit a belt line with nothing being sent about it.
     */
    private void rebuildAround(BlockPos around) {
        LongOpenHashSet pending = new LongOpenHashSet();
        List<BeltRun.Parked> parked = new ArrayList<>();

        take(around, pending, parked);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            take(around.relative(side), pending, parked);
        }

        while (true) {
            long next = Long.MAX_VALUE;
            boolean found = false;
            for (LongIterator it = pending.iterator(); it.hasNext();) {
                long key = it.nextLong();
                if (belts.contains(key) && !runByBelt.containsKey(key) && key < next) {
                    next = key;
                    found = true;
                }
            }
            if (!found) {
                break;
            }
            build(BlockPos.of(next), pending, parked);
        }

        replace(parked);
    }

    /** Dissolves the run covering {@code pos}, keeping its blocks to rebuild and its items to replace. */
    private void take(BlockPos pos, LongOpenHashSet pending, List<BeltRun.Parked> parked) {
        long key = pos.asLong();
        if (!belts.contains(key)) {
            return;
        }
        pending.add(key);

        BeltRun run = runByBelt.get(key);
        if (run == null) {
            return;
        }
        parked.addAll(run.park());
        for (BlockPos member : run.blocks()) {
            runByBelt.remove(member.asLong());
            pending.add(member.asLong());
        }
        run.dissolve();
        active.remove(run);
    }

    /** Builds the whole line the belt at {@code seed} lies on, from its tail to its head. */
    private void build(BlockPos seed, LongOpenHashSet pending, List<BeltRun.Parked> parked) {
        BeltBlock block = beltAt(seed);
        if (block == null) {
            belts.remove(seed.asLong());
            return;
        }

        LongOpenHashSet walked = new LongOpenHashSet();
        walked.add(seed.asLong());

        BlockPos tail = seed;
        while (true) {
            BlockPos feeder = soleFeeder(tail, block);
            if (feeder == null || !walked.add(feeder.asLong())) {
                break;
            }
            take(feeder, pending, parked);
            tail = feeder;
        }

        List<BlockPos> members = new ArrayList<>();
        LongOpenHashSet inRun = new LongOpenHashSet();
        boolean loops = false;
        BlockPos at = tail;
        while (true) {
            members.add(at);
            inRun.add(at.asLong());
            BlockPos next = successor(at, block);
            if (next == null || feederCount(next, block) != 1) {
                break;
            }
            if (inRun.contains(next.asLong())) {
                // Back where we started: a ring of belts with no beginning, broken open here.
                loops = next.equals(members.get(0));
                break;
            }
            take(next, pending, parked);
            at = next;
        }

        Direction[] facings = new Direction[members.size()];
        for (int i = 0; i < members.size(); i++) {
            facings[i] = facing(members.get(i));
        }

        BeltRun run = new BeltRun(level, this, block, members, facings, loops);
        for (BlockPos member : members) {
            runByBelt.put(member.asLong(), run);
        }
    }

    /** Puts every lifted item back on whatever line now owns the block it was standing on. */
    private void replace(List<BeltRun.Parked> parked) {
        if (parked.isEmpty()) {
            return;
        }
        Map<BeltRun, List<BeltRun.Parked>> byRun = new LinkedHashMap<>();
        for (BeltRun.Parked item : parked) {
            BeltRun run = runAt(item.block());
            if (run == null) {
                // Its belt is gone and nothing took its place. On the server that is loose cargo.
                if (!level.isClientSide()) {
                    Containers.dropItemStack(level, item.block().getX() + 0.5,
                            item.block().getY() + 0.5, item.block().getZ() + 0.5, item.item().toStack(1));
                }
                continue;
            }
            byRun.computeIfAbsent(run, ignored -> new ArrayList<>()).add(item);
        }

        for (var entry : byRun.entrySet()) {
            BeltRun run = entry.getKey();
            List<BeltRun.Parked> items = entry.getValue();
            // Leading item of each lane first, which is the order unpark has to see them in.
            items.sort(Comparator.<BeltRun.Parked>comparingInt(BeltRun.Parked::lane)
                    .thenComparingInt(item -> run.frontEdge(run.indexOf(item.block())) + item.offset()));
            run.unpark(items);
            markActive(run);
        }
    }

    // --- who feeds whom --------------------------------------------------------------------------

    private @Nullable BeltBlock beltAt(BlockPos pos) {
        if (!belts.contains(pos.asLong())) {
            return null;
        }
        return level.getBlockState(pos).getBlock() instanceof BeltBlock belt ? belt : null;
    }

    private Direction facing(BlockPos pos) {
        return level.getBlockState(pos).getValue(BeltBlock.FACING);
    }

    /**
     * The belt this one hands to: the block it faces, if that is a belt of the same tier which is
     * not facing straight back at it.
     *
     * <p>Same tier, because a run has one speed. A fast belt after a yellow one is a second run
     * that the first hands off into, which is what Factorio does with its transport lines.
     */
    private @Nullable BlockPos successor(BlockPos pos, BeltBlock block) {
        Direction out = facing(pos);
        BlockPos next = pos.relative(out);
        if (beltAt(next) != block) {
            return null;
        }
        return facing(next) == out.getOpposite() ? null : next;
    }

    /** How many belts hand to this one. Two or more, and it is the start of a line of its own. */
    private int feederCount(BlockPos pos, BeltBlock block) {
        int count = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (feeds(pos.relative(side), pos, side.getOpposite(), block)) {
                count++;
            }
        }
        return count;
    }

    private @Nullable BlockPos soleFeeder(BlockPos pos, BeltBlock block) {
        BlockPos found = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(side);
            if (!feeds(neighbour, pos, side.getOpposite(), block)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = neighbour;
        }
        return found;
    }

    /** Whether the belt at {@code from} hands to {@code to}, which lies {@code towards} of it. */
    private boolean feeds(BlockPos from, BlockPos to, Direction towards, BeltBlock block) {
        if (beltAt(from) != block) {
            return false;
        }
        return facing(from) == towards && facing(to) != towards.getOpposite();
    }

    // --- the two things a client has to be told ---------------------------------------------------

    /**
     * An item was put on the belt at {@code pos} by something that is not a belt.
     *
     * <p>One of exactly two messages a belt sends, and the reason there are only two: everything a
     * belt does on its own - moving, jamming, handing off to the next line - both sides work out
     * for themselves from block states they already have. Only the boundary with the rest of the
     * world has to travel.
     */
    public void putOn(BlockPos pos, int lane, int offset, ItemResource item) {
        BeltRun run = runAt(pos);
        if (run == null) {
            return;
        }
        int index = run.indexOf(pos);
        if (index < 0) {
            return;
        }
        run.place(lane, run.frontEdge(index) + offset, item);
        markActive(run);
    }

    /** An item was taken off the belt at {@code pos}, near {@code offset} along it. */
    public void takeOff(BlockPos pos, int lane, int offset) {
        BeltRun run = runAt(pos);
        if (run == null) {
            return;
        }
        int index = run.indexOn(pos, lane, offset);
        if (index >= 0) {
            run.lane(lane).removeAt(index);
        }
    }

    // --- what a belt block asks ---------------------------------------------------------------

    /** Whether a block state is a belt, without the graph having to know about it yet. */
    public static boolean isBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock;
    }
}
