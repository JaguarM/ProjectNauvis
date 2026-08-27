package com.jaguarm.nauvisfluids.pipe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Every pipe run in one level, and the only thing in this mod that runs every tick.
 *
 * <p>It iterates <em>runs</em>, of which a base has a handful, rather than pipes, of which it has
 * thousands. A run that moved nothing last tick drops out of the active set and stops costing
 * anything per tick at all. This is {@code PowerNetworkManager} with the nouns changed, and
 * deliberately so - it is the third time this pack has needed the shape.
 *
 * <h2>Why this one is simpler than the electric grid</h2>
 *
 * <p>A pole reaches 7.5 blocks and supplies a 5x5 area, so the grid needs a spatial index to find
 * its neighbours and a level-wide block-change hook to notice a machine appearing two blocks away.
 * A pipe connects to the six blocks it touches, which is exactly the range
 * {@code neighborChanged} already reports for free. So there is no cell index here, no chunk
 * pre-filter and no global subscription: the pipe is simply told.
 */
public final class FluidNetworkManager {

    /** How often a run that is moving nothing is looked at again. */
    private static final int DORMANT_INTERVAL = 10;

    private static final Map<ServerLevel, FluidNetworkManager> MANAGERS = new IdentityHashMap<>();

    private final ServerLevel level;

    private final Long2ObjectOpenHashMap<FluidNetwork> networkByPipe = new Long2ObjectOpenHashMap<>();
    private final Set<FluidNetwork> networks = new LinkedHashSet<>();
    private final Set<FluidNetwork> active = new LinkedHashSet<>();

    /** Pipes whose neighbours need looking at again, applied at the start of the next tick. */
    private final LongOpenHashSet pendingPipes = new LongOpenHashSet();

    private long tickCount;

    private FluidNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static FluidNetworkManager of(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, FluidNetworkManager::new);
    }

    /** The level is going away; so is its graph. Pipes rebuild it when they load again. */
    static void forget(ServerLevel level) {
        MANAGERS.remove(level);
    }

    // --- what the world tells us ------------------------------------------------------------

    /** A pipe exists at {@code pos}: join it to whatever it touches, merging runs if it bridges. */
    public void pipePlaced(BlockPos pos) {
        long key = pos.asLong();
        if (networkByPipe.containsKey(key)) {
            return;
        }

        LongArrayList neighbours = adjacentPipes(key);
        FluidNetwork network;
        if (neighbours.isEmpty()) {
            network = new FluidNetwork(level);
            networks.add(network);
        } else {
            network = networkByPipe.get(neighbours.getLong(0));
            for (int i = 1; i < neighbours.size(); i++) {
                network = merge(network, networkByPipe.get(neighbours.getLong(i)));
            }
        }

        network.addPipe(key);
        networkByPipe.put(key, network);
        pendingPipes.add(key);
    }

    /** A pipe is gone: drop what only it touched, and split the run if it held two halves together. */
    public void pipeRemoved(BlockPos pos) {
        long key = pos.asLong();
        FluidNetwork network = networkByPipe.remove(key);
        pendingPipes.remove(key);
        if (network == null) {
            return;
        }

        network.removePipe(key);
        dropUnreachedEndpoints(network, key);

        if (network.pipes().isEmpty()) {
            networks.remove(network);
            active.remove(network);
            return;
        }

        splitIfSevered(network, key);
    }

    /**
     * Something changed beside a pipe.
     *
     * <p>The electric grid needs a level-wide hook for this because a pole reaches further than
     * any neighbour notification carries. A pipe does not: everything it can connect to is one
     * block away, which is precisely what {@code neighborChanged} reports.
     */
    public void neighbourChanged(BlockPos pos) {
        if (networkByPipe.containsKey(pos.asLong())) {
            pendingPipes.add(pos.asLong());
        }
    }

    // --- the tick ---------------------------------------------------------------------------

    void tick() {
        tickCount++;
        resolvePending();

        active.removeIf(network -> !network.couldWork() || network.tick() == 0);

        if (tickCount % DORMANT_INTERVAL == 0) {
            for (FluidNetwork network : networks) {
                if (!active.contains(network) && network.couldWork() && network.tick() > 0) {
                    active.add(network);
                }
            }
        }
    }

    private void resolvePending() {
        if (pendingPipes.isEmpty()) {
            return;
        }
        for (LongIterator it = pendingPipes.iterator(); it.hasNext();) {
            long pipe = it.nextLong();
            FluidNetwork network = networkByPipe.get(pipe);
            if (network == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(pipe);
            for (Direction side : Direction.values()) {
                long neighbour = pos.relative(side).asLong();
                if (!networkByPipe.containsKey(neighbour)) {
                    network.addEndpoint(neighbour);
                }
            }
            wake(network);
        }
        pendingPipes.clear();
    }

    private void wake(FluidNetwork network) {
        if (network.couldWork()) {
            active.add(network);
        }
    }

    // --- the graph ---------------------------------------------------------------------------

    /** The pipes touching this position. Six sides, and no distance test to make. */
    private LongArrayList adjacentPipes(long pipe) {
        LongArrayList found = new LongArrayList();
        BlockPos pos = BlockPos.of(pipe);
        for (Direction side : Direction.values()) {
            long neighbour = pos.relative(side).asLong();
            if (networkByPipe.containsKey(neighbour)) {
                found.add(neighbour);
            }
        }
        return found;
    }

    /** Folds the smaller run into the larger, and returns whichever survived. */
    private FluidNetwork merge(FluidNetwork a, @Nullable FluidNetwork b) {
        if (b == null || a == b) {
            return a;
        }
        FluidNetwork kept = a.pipeCount() >= b.pipeCount() ? a : b;
        FluidNetwork lost = kept == a ? b : a;

        for (LongIterator it = lost.pipes().iterator(); it.hasNext();) {
            long pipe = it.nextLong();
            kept.addPipe(pipe);
            networkByPipe.put(pipe, kept);
        }
        for (LongIterator it = lost.endpointPositions().iterator(); it.hasNext();) {
            long endpoint = it.nextLong();
            var cache = lost.endpoint(endpoint);
            if (cache != null) {
                kept.adoptEndpoint(endpoint, cache);
            }
        }
        // What was in the smaller run goes with it rather than evaporating.
        lost.pourInto(kept, 1.0);

        lost.markMerged();
        networks.remove(lost);
        active.remove(lost);
        wake(kept);
        return kept;
    }

    /**
     * Splits {@code network} if removing the pipe at {@code removed} disconnected it.
     *
     * <p>The early return is what makes breaking a pipe cheap in the case that actually happens: a
     * pipe with one neighbour, or none, was the end of a line and cannot have been holding two
     * halves together.
     */
    private void splitIfSevered(FluidNetwork network, long removed) {
        if (adjacentPipes(removed).size() <= 1) {
            return;
        }

        List<LongOpenHashSet> parts = components(network);
        if (parts.size() <= 1) {
            return;
        }

        LongArrayList endpoints = network.endpointPositions();
        int total = network.pipeCount();
        List<FluidNetwork> created = new ArrayList<>(parts.size());

        for (LongOpenHashSet part : parts) {
            FluidNetwork piece = new FluidNetwork(level);
            for (LongIterator it = part.iterator(); it.hasNext();) {
                long pipe = it.nextLong();
                piece.addPipe(pipe);
                networkByPipe.put(pipe, piece);
            }
            networks.add(piece);
            created.add(piece);
            // Each half keeps the share of the contents its length earned.
            network.pourInto(piece, total == 0 ? 0 : (double) part.size() / total);
        }

        for (LongIterator it = endpoints.iterator(); it.hasNext();) {
            long endpoint = it.nextLong();
            var cache = network.endpoint(endpoint);
            if (cache == null) {
                continue;
            }
            for (FluidNetwork piece : created) {
                if (touches(piece, endpoint)) {
                    piece.adoptEndpoint(endpoint, cache);
                }
            }
        }

        network.markMerged();
        networks.remove(network);
        active.remove(network);
        created.forEach(this::wake);
    }

    /** Flood fills over the run's own pipes, one component at a time. */
    private List<LongOpenHashSet> components(FluidNetwork network) {
        List<LongOpenHashSet> parts = new ArrayList<>();
        LongOpenHashSet remaining = new LongOpenHashSet(network.pipes());
        LongArrayList frontier = new LongArrayList();

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().nextLong();
            remaining.remove(seed);

            LongOpenHashSet part = new LongOpenHashSet();
            part.add(seed);
            frontier.clear();
            frontier.add(seed);

            while (!frontier.isEmpty()) {
                BlockPos pos = BlockPos.of(frontier.removeLong(frontier.size() - 1));
                for (Direction side : Direction.values()) {
                    long other = pos.relative(side).asLong();
                    if (remaining.remove(other)) {
                        part.add(other);
                        frontier.add(other);
                    }
                }
            }
            parts.add(part);
        }
        return parts;
    }

    /** Whether any pipe of {@code network} touches {@code endpoint}. */
    private static boolean touches(FluidNetwork network, long endpoint) {
        BlockPos pos = BlockPos.of(endpoint);
        for (Direction side : Direction.values()) {
            if (network.pipes().contains(pos.relative(side).asLong())) {
                return true;
            }
        }
        return false;
    }

    /** Forgets the machines the pipe at {@code removed} was the last one touching. */
    private void dropUnreachedEndpoints(FluidNetwork network, long removed) {
        BlockPos pos = BlockPos.of(removed);
        for (Direction side : Direction.values()) {
            long neighbour = pos.relative(side).asLong();
            if (!touches(network, neighbour)) {
                network.removeEndpoint(neighbour);
            }
        }
    }

    // --- what the tests and the readout look at ------------------------------------------------

    /** The run the pipe at {@code pos} belongs to, or null if there is no pipe there. */
    public @Nullable FluidNetwork networkAt(BlockPos pos) {
        return networkByPipe.get(pos.asLong());
    }

    public boolean isActive(FluidNetwork network) {
        return active.contains(network);
    }
}
