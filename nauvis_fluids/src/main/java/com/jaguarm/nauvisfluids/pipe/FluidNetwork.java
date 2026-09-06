package com.jaguarm.nauvisfluids.pipe;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * One connected run of pipes, and the machines it touches.
 *
 * <p><b>The network ticks, not the pipes.</b> The third time this pack has needed that sentence -
 * after the belt note and the electric network - and for the same reason: a pipe that ticks costs
 * N ticks a second for N pipes and takes N ticks to move a drop across them. A run that ticks
 * costs one iteration however long it is, and steam crosses it at once.
 *
 * <p>The run holds the fluid, exactly as Factorio's pipes do: a pipeline is one fluid box whose
 * capacity grows with its length, not a queue of little buffers. That is what makes
 * {@code Steam: 8.4 of 100} a sensible thing to say about a pipe, and it is why breaking a run in
 * half has to divide what was in it.
 *
 * <h2>Sources and sinks are not told apart</h2>
 *
 * <p>There is one endpoint table and nothing is labelled. A machine is a source if it will not
 * accept back the fluid it is offering: a boiler refuses steam - {@code SteamAccess} is
 * extract-only - so it gives, and a steam engine accepts it, so it does not. The test is asked of
 * each machine about itself, which is why it still works on the tick a run is empty and has
 * nothing of its own to compare against.
 */
public final class FluidNetwork {

    /**
     * What one pipe adds to the run's capacity.
     *
     * <p>A hundred, so Factorio's {@code Steam: 8.4 of 100} reads the same here for a single pipe.
     * Behaviour rather than identity, so it is yours to tune.
     */
    public static final int CAPACITY_PER_PIPE = 100;

    /** The most a run moves in one tick, so the sums stay in an {@code int}. */
    private static final int MAX_TRANSFER = 1_000_000;

    private final ServerLevel level;

    /** Member pipes, packed. A pipe is a position and nothing else. */
    private final LongOpenHashSet pipes = new LongOpenHashSet();

    /**
     * Machines touching a member pipe, each reached through the face a pipe lies on. Linked, so a
     * shortfall is met in a stable order.
     */
    private final Long2ObjectLinkedOpenHashMap<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>>
            endpoints = new Long2ObjectLinkedOpenHashMap<>();

    /**
     * What the run is holding. One tank, resized as pipes join and leave.
     *
     * <p>A {@code FluidStacksResourceHandler} rather than a pair of fields, so the contents are a
     * {@code FluidResource} and this will carry oil as readily as steam when there is oil.
     */
    private final RunTank contents = new RunTank();

    // Scratch, reused every tick. A network ticks twenty times a second.
    private final LongArrayList hungry = new LongArrayList();
    private final IntArrayList wanted = new IntArrayList();
    private final LongArrayList gone = new LongArrayList();

    private boolean merged;

    FluidNetwork(ServerLevel level) {
        this.level = level;
    }

    /** The run's own tank. Its capacity is however many pipes are in it. */
    private final class RunTank extends FluidStacksResourceHandler {
        private RunTank() {
            super(1, CAPACITY_PER_PIPE);
        }

        void resize() {
            capacity = Math.max(CAPACITY_PER_PIPE, pipes.size() * CAPACITY_PER_PIPE);
        }
    }

    // --- membership -------------------------------------------------------------------------

    boolean isMerged() {
        return merged;
    }

    void markMerged() {
        merged = true;
    }

    LongOpenHashSet pipes() {
        return pipes;
    }

    void addPipe(long pos) {
        pipes.add(pos);
        contents.resize();
    }

    void removePipe(long pos) {
        pipes.remove(pos);
        contents.resize();
    }

    public int pipeCount() {
        return pipes.size();
    }

    public int endpointCount() {
        return endpoints.size();
    }

    /** How much is in the run right now, and how much it could hold. */
    public int amount() {
        return contents.getAmountAsInt(0);
    }

    public int capacity() {
        return Math.max(CAPACITY_PER_PIPE, pipes.size() * CAPACITY_PER_PIPE);
    }

    public FluidResource fluid() {
        return contents.getResource(0);
    }

    ResourceHandler<FluidResource> contents() {
        return contents;
    }

    /**
     * Starts watching the machine at {@code pos} through {@code face}, the side of it a pipe of
     * this run touches - if there is a machine there, and it offers anything on that side.
     *
     * <p>The face is not optional. A machine's ports are sided - a boiler gives steam at its
     * back, a pumpjack at one corner - and a capability asked for with no side answers for every
     * side, which is NeoForge's convention and the right one for a hopper. For a run it meant
     * that every face of every machine was an outlet, and the only thing saying otherwise was
     * the pipe's drawn connection. The face a pipe actually lies on is the question a run has
     * to ask, and it is what {@code PipeBlock.connects} asks too, so what is drawn and what flows
     * agree.
     */
    void addEndpoint(long pos, Direction face) {
        if (endpoints.containsKey(pos)) {
            return;
        }
        BlockPos blockPos = BlockPos.of(pos);
        // isLoaded first, and not as an optimisation: asking for a capability in an unloaded
        // chunk loads it.
        if (!level.isLoaded(blockPos)
                || level.getCapability(Capabilities.Fluid.BLOCK, blockPos, face) == null) {
            return;
        }
        endpoints.put(pos, BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, blockPos, face));
    }

    void removeEndpoint(long pos) {
        endpoints.remove(pos);
    }

    void adoptEndpoint(long pos,
            BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache) {
        endpoints.putIfAbsent(pos, cache);
    }

    @Nullable
    BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> endpoint(long pos) {
        return endpoints.get(pos);
    }

    LongArrayList endpointPositions() {
        return new LongArrayList(endpoints.keySet());
    }

    /**
     * Moves what is in this run into the one that succeeds it, in proportion to how much of the
     * run it took with it.
     *
     * <p>Breaking a pipe in the middle of a full line should leave two half-full lines, not one
     * full one and one empty, and certainly not two full ones.
     */
    void pourInto(FluidNetwork other, double share) {
        if (contents.getAmountAsInt(0) <= 0) {
            return;
        }
        int moved = (int) Math.round(contents.getAmountAsInt(0) * share);
        if (moved <= 0) {
            return;
        }
        other.contents.set(0, contents.getResource(0),
                Math.min(moved, other.capacity()));
    }

    /** Whether ticking this could possibly do anything. A run touching nothing cannot. */
    boolean couldWork() {
        return !merged && !pipes.isEmpty() && !endpoints.isEmpty();
    }

    // --- the tick ---------------------------------------------------------------------------

    /**
     * Fills what wants filling, then takes what is going spare.
     *
     * @return how much moved. Zero means there was nothing to do, and the manager stops ticking
     *         this run every tick until something changes.
     */
    int tick() {
        hungry.clear();
        wanted.clear();
        gone.clear();

        int moved = 0;
        // Only worth asking who wants some if there is some. An empty run has nothing to push,
        // and probing with an empty resource is not a question a handler can answer.
        if (contents.getAmountAsInt(0) > 0) {
            long demand = measureDemand();
            if (demand > 0) {
                moved += push((int) Math.min(demand, MAX_TRANSFER));
            }
        }
        moved += pull();
        dropMissing();
        return moved;
    }

    /** Asks every endpoint how much of what we are holding it would take, and gives it nothing. */
    private long measureDemand() {
        long demand = 0;
        FluidResource resource = contents.getResource(0);
        try (Transaction probe = Transaction.openRoot()) {
            for (var entry : endpoints.long2ObjectEntrySet()) {
                long pos = entry.getLongKey();
                ResourceHandler<FluidResource> handler = live(pos, entry.getValue());
                if (handler == null) {
                    continue;
                }
                int want = handler.insert(resource, MAX_TRANSFER, probe);
                if (want > 0) {
                    hungry.add(pos);
                    wanted.add(want);
                    demand += want;
                }
            }
            // Deliberately not committed.
        }
        return demand;
    }

    /** Empties the run into whatever wanted some, as far as it goes. */
    private int push(int target) {
        int available = Math.min(target, contents.getAmountAsInt(0));
        if (available <= 0) {
            return 0;
        }

        int moved = 0;
        FluidResource resource = contents.getResource(0);
        for (int i = 0; i < hungry.size() && moved < available; i++) {
            var cache = endpoints.get(hungry.getLong(i));
            if (cache == null) {
                continue;
            }
            ResourceHandler<FluidResource> handler = cache.getCapability();
            if (handler == null) {
                continue;
            }

            try (Transaction transfer = Transaction.openRoot()) {
                int room = Math.min(wanted.getInt(i), available - moved);
                int taken = contents.extract(resource, room, transfer);
                if (taken > 0 && handler.insert(resource, taken, transfer) == taken) {
                    transfer.commit();
                    moved += taken;
                }
            }
        }
        return moved;
    }

    /**
     * Draws from anything that gives without taking, which is this network's whole definition of
     * a source.
     *
     * <p>The test is asked of the endpoint about itself: would it accept back the very fluid it is
     * offering? A boiler says no - {@code SteamAccess} refuses insertion - so it is a source. An
     * engine says yes, because an engine is a length of pipe that happens to consume, so it is
     * not. Without that test a pipe run would drain the engines it is supposed to be feeding, and
     * it would do it on exactly the tick the run was empty and had nothing to compare against.
     */
    private int pull() {
        int room = capacity() - contents.getAmountAsInt(0);
        if (room <= 0) {
            return 0;
        }

        int moved = 0;
        for (var entry : endpoints.long2ObjectEntrySet()) {
            if (moved >= room) {
                break;
            }
            ResourceHandler<FluidResource> handler = live(entry.getLongKey(), entry.getValue());
            if (handler == null) {
                continue;
            }

            for (int index = 0; index < handler.size() && moved < room; index++) {
                FluidResource offered = handler.getResource(index);
                if (offered.isEmpty() || !accepts(offered) || takesItBack(handler, offered)) {
                    continue;
                }
                try (Transaction transfer = Transaction.openRoot()) {
                    int taken = handler.extract(index, offered, room - moved, transfer);
                    if (taken > 0 && contents.insert(offered, taken, transfer) == taken) {
                        transfer.commit();
                        moved += taken;
                    }
                }
            }
        }
        return moved;
    }

    /** Whether this handler would accept back what it is offering - a sink, not a source. */
    private static boolean takesItBack(ResourceHandler<FluidResource> handler, FluidResource offered) {
        try (Transaction probe = Transaction.openRoot()) {
            return handler.insert(offered, 1, probe) > 0;
        }
    }

    /** A run carries one fluid at a time, whichever arrived first. */
    private boolean accepts(FluidResource offered) {
        FluidResource held = contents.getResource(0);
        return held.isEmpty() || held.equals(offered);
    }

    /** What the run would hand out: whatever it is holding, or nothing if it is empty. */
    private FluidResource fluidToMove() {
        return contents.getResource(0);
    }

    private @Nullable ResourceHandler<FluidResource> live(long pos,
            BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache) {
        ResourceHandler<FluidResource> handler = cache.getCapability();
        if (handler == null && level.isLoaded(BlockPos.of(pos))) {
            gone.add(pos);
        }
        return handler;
    }

    private void dropMissing() {
        for (LongIterator it = gone.iterator(); it.hasNext();) {
            endpoints.remove(it.nextLong());
        }
    }
}
