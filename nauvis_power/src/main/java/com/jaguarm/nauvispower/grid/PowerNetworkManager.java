package com.jaguarm.nauvispower.grid;

import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Every electric network in one level, and the only thing in this mod that runs every tick.
 *
 * <p>It iterates <em>networks</em>, of which a large base has a handful, rather than poles, of
 * which it has thousands. A network that moved no energy last tick is dropped out of the active
 * set and stops costing anything per tick at all.
 *
 * <h2>Three indexes, and why each exists</h2>
 *
 * <ul>
 *   <li><b>pole to network</b> - the answer to "what did I just connect to", and the only state
 *       that says a network exists at all.
 *   <li><b>poles by cell</b> - poles bucketed into 8-block cubes. Wire reach is 7.5, which is
 *       less than 8, so two poles that can see each other are in the same cell or in one of the
 *       26 around it. That turns "which poles are in range of this one" into 27 map lookups
 *       instead of a 15x15x15 scan, and it is what keeps breaking a pole in the middle of a big
 *       network from being a visible stutter: splitting has to walk the graph, and walking it
 *       costs a constant per pole rather than three and a half thousand.
 *   <li><b>poles by supplied chunk</b> - which poles could possibly reach into a given chunk.
 *       This is the pre-filter for {@link #blockChanged}, which is called for every block change
 *       in the level; one lookup rejects everywhere that is not near a pole.
 * </ul>
 *
 * <h2>How a machine is found</h2>
 *
 * <p>Non-negotiable #3 forbids {@code nauvis_machines} from knowing what a pole is, so a machine
 * cannot come and register itself. Poles do the finding, through
 * {@code Capabilities.Energy.BLOCK} - which is NeoForge's, so a third-party machine is found on
 * exactly the same terms as ours.
 *
 * <p>The scan has to be triggered, never polled. Three things trigger it, and between them they
 * cover every way a machine can appear beside a pole:
 *
 * <ul>
 *   <li>a pole is placed or its chunk loads - it scans its own supply area once;
 *   <li>a block changes inside a chunk some pole reaches into - {@link #blockChanged}, fed by
 *       {@code BlockEvent.NeighborNotifyEvent}, which fires for any block placed or broken by any
 *       means, not just by a player;
 *   <li>a chunk a pole reaches into loads - the machines in it were unreachable when the pole
 *       last scanned, so that pole scans again.
 * </ul>
 *
 * <p>Nothing here is per-tick. The alternative - a capability invalidation listener registered on
 * each of the 125 positions in every pole's supply area - is exact and needs no pre-filter, but a
 * base of ten thousand poles would hold well over a million weak references to pay for it.
 *
 * <h2>The graph is never saved</h2>
 *
 * <p>It is derivable from where the poles are, so saving it would be caching, and invalidating
 * that cache across a chunk load is where the bugs would live. Poles register from
 * {@code onLoad} and deregister from {@code setRemoved} and {@code onChunkUnloaded}; the level
 * unloading throws the whole thing away.
 */
public final class PowerNetworkManager {

    /** Factorio's small pole reaches 7.5 blocks to another pole. Behaviour, so it is tunable. */
    public static final double WIRE_REACH = 7.5;

    private static final double WIRE_REACH_SQ = WIRE_REACH * WIRE_REACH;

    /**
     * Factorio's small pole supplies a 5x5 area - two blocks either side. Kept as a cube here,
     * because a machine stacked above another is a reasonable thing to build and refusing it
     * would be a rule the player has to learn for no reason.
     */
    public static final int SUPPLY_RADIUS = 2;

    /**
     * Cell size for the pole index, as a shift. Eight blocks, which must stay larger than
     * {@link #WIRE_REACH} - the 27-cell neighbourhood below is only complete because it is.
     */
    private static final int CELL_BITS = 3;

    /**
     * How often a network that is not moving energy is looked at again.
     *
     * <p>A network learns about a pole or a machine changing the moment it happens. What it
     * cannot hear is a generator elsewhere in the level filling up, or a machine deciding it
     * wants power again, because those are handlers in other mods that owe us no signal. Half a
     * second of latency, paid by a handful of objects rather than by every pole, is the price of
     * not requiring one.
     */
    private static final int DORMANT_INTERVAL = 10;

    private static final Map<ServerLevel, PowerNetworkManager> MANAGERS = new IdentityHashMap<>();

    private final ServerLevel level;

    private final Long2ObjectOpenHashMap<PowerNetwork> networkByPole = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> polesByCell = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> polesBySuppliedChunk = new Long2ObjectOpenHashMap<>();

    private final Set<PowerNetwork> networks = new LinkedHashSet<>();
    private final Set<PowerNetwork> active = new LinkedHashSet<>();

    /** Poles whose supply area needs a full rescan, applied at the start of the next tick. */
    private final LongOpenHashSet pendingPoles = new LongOpenHashSet();

    /** Single positions where something changed near a pole. Deduplicated over the tick. */
    private final LongOpenHashSet pendingBlocks = new LongOpenHashSet();

    private long tickCount;

    private PowerNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static PowerNetworkManager of(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, PowerNetworkManager::new);
    }

    /** The level is going away; so is its graph. Poles rebuild it when they load again. */
    static void forget(ServerLevel level) {
        MANAGERS.remove(level);
    }

    // --- what the world tells us ------------------------------------------------------------

    /**
     * A pole exists at {@code pos}: join it to whatever it can reach, merging networks if it
     * bridges two.
     */
    public void polePlaced(BlockPos pos) {
        long key = pos.asLong();
        if (networkByPole.containsKey(key)) {
            return;
        }
        index(key);

        LongArrayList reachable = new LongArrayList();
        collectWireNeighbours(key, reachable);

        PowerNetwork network;
        if (reachable.isEmpty()) {
            network = new PowerNetwork(level);
            networks.add(network);
        } else {
            network = networkByPole.get(reachable.getLong(0));
            for (int i = 1; i < reachable.size(); i++) {
                network = merge(network, networkByPole.get(reachable.getLong(i)));
            }
        }

        network.addPole(key);
        networkByPole.put(key, network);
        pendingPoles.add(key);
    }

    /**
     * A pole is gone: drop the machines only it reached, and split the network if it was the
     * only thing holding two halves together.
     */
    public void poleRemoved(BlockPos pos) {
        long key = pos.asLong();
        PowerNetwork network = networkByPole.remove(key);
        unindex(key);
        pendingPoles.remove(key);
        if (network == null) {
            return;
        }

        network.removePole(key);
        dropUnreachedEndpoints(network, key);

        if (network.poles().isEmpty()) {
            networks.remove(network);
            active.remove(network);
            return;
        }

        splitIfSevered(network, key);
    }

    /**
     * Something was placed or broken at {@code pos}. Called for every block change in the level,
     * so the first line is the whole point of {@link #polesBySuppliedChunk}.
     */
    public void blockChanged(BlockPos pos) {
        if (polesBySuppliedChunk.containsKey(ChunkPos.pack(pos))) {
            pendingBlocks.add(pos.asLong());
        }
    }

    /**
     * A chunk loaded. Any pole that reaches into it skipped those positions when it last scanned,
     * because querying an unloaded position would drag the chunk in.
     */
    public void chunkLoaded(ChunkPos chunk) {
        LongOpenHashSet poles = polesBySuppliedChunk.get(chunk.pack());
        if (poles != null) {
            pendingPoles.addAll(poles);
        }
    }

    // --- the tick ---------------------------------------------------------------------------

    void tick() {
        tickCount++;
        resolvePending();

        active.removeIf(network -> !network.couldWork() || network.tick() == 0);

        if (tickCount % DORMANT_INTERVAL == 0) {
            for (PowerNetwork network : networks) {
                if (!active.contains(network) && network.couldWork() && network.tick() > 0) {
                    active.add(network);
                }
            }
        }
    }

    /** Discovery, batched to the start of a tick so a burst of block changes is paid for once. */
    private void resolvePending() {
        if (!pendingPoles.isEmpty()) {
            for (LongIterator it = pendingPoles.iterator(); it.hasNext();) {
                long pole = it.nextLong();
                PowerNetwork network = networkByPole.get(pole);
                if (network == null) {
                    continue;
                }
                scanSupplyArea(pole, network);
                wake(network);
            }
            pendingPoles.clear();
        }

        if (!pendingBlocks.isEmpty()) {
            for (LongIterator it = pendingBlocks.iterator(); it.hasNext();) {
                long changed = it.nextLong();
                LongOpenHashSet poles = polesBySuppliedChunk.get(ChunkPos.pack(BlockPos.of(changed)));
                if (poles == null) {
                    continue;
                }
                for (LongIterator poleIt = poles.iterator(); poleIt.hasNext();) {
                    long pole = poleIt.nextLong();
                    if (!supplies(pole, changed)) {
                        continue;
                    }
                    PowerNetwork network = networkByPole.get(pole);
                    if (network == null) {
                        continue;
                    }
                    // A machine that has gone is not removed here. Its cache answers null, and
                    // the network drops it on its next tick - which the wake below guarantees.
                    network.addEndpoint(changed);
                    wake(network);
                }
            }
            pendingBlocks.clear();
        }
    }

    private void wake(PowerNetwork network) {
        if (network.couldWork()) {
            active.add(network);
        }
    }

    // --- the graph ---------------------------------------------------------------------------

    /** Folds the smaller network into the larger, and returns whichever survived. */
    private PowerNetwork merge(PowerNetwork a, @Nullable PowerNetwork b) {
        if (b == null || a == b) {
            return a;
        }
        PowerNetwork kept = a.poleCount() >= b.poleCount() ? a : b;
        PowerNetwork lost = kept == a ? b : a;

        for (LongIterator it = lost.poles().iterator(); it.hasNext();) {
            long pole = it.nextLong();
            kept.addPole(pole);
            networkByPole.put(pole, kept);
        }
        for (LongIterator it = lost.endpointPositions().iterator(); it.hasNext();) {
            long endpoint = it.nextLong();
            var cache = lost.endpoint(endpoint);
            if (cache != null) {
                kept.adoptEndpoint(endpoint, cache);
            }
        }

        lost.markMerged();
        networks.remove(lost);
        active.remove(lost);
        wake(kept);
        return kept;
    }

    /**
     * Splits {@code network} if removing the pole at {@code removed} disconnected it.
     *
     * <p>The early return is what makes breaking a pole cheap in the case that actually happens:
     * a pole with one wire neighbour, or none, cannot have been holding anything together, and
     * most poles in a base are the end of a line rather than the middle of one.
     */
    private void splitIfSevered(PowerNetwork network, long removed) {
        LongArrayList seeds = new LongArrayList();
        collectWireNeighbours(removed, seeds);
        if (seeds.size() <= 1) {
            return;
        }

        List<LongOpenHashSet> parts = components(network);
        if (parts.size() <= 1) {
            return;
        }

        LongArrayList endpoints = network.endpointPositions();
        List<PowerNetwork> created = new ArrayList<>(parts.size());
        for (LongOpenHashSet part : parts) {
            PowerNetwork piece = new PowerNetwork(level);
            for (LongIterator it = part.iterator(); it.hasNext();) {
                long pole = it.nextLong();
                piece.addPole(pole);
                networkByPole.put(pole, piece);
            }
            networks.add(piece);
            created.add(piece);
        }

        // Endpoints move rather than being rediscovered: the caches are already built, and a
        // machine supplied by both halves of a severed network is still supplied by both.
        for (LongIterator it = endpoints.iterator(); it.hasNext();) {
            long endpoint = it.nextLong();
            var cache = network.endpoint(endpoint);
            if (cache == null) {
                continue;
            }
            for (PowerNetwork piece : created) {
                if (reaches(piece, endpoint)) {
                    piece.adoptEndpoint(endpoint, cache);
                }
            }
        }

        network.markMerged();
        networks.remove(network);
        active.remove(network);
        created.forEach(this::wake);
    }

    /** Flood fills over the network's own poles, one component at a time. */
    private List<LongOpenHashSet> components(PowerNetwork network) {
        List<LongOpenHashSet> parts = new ArrayList<>();
        LongOpenHashSet remaining = new LongOpenHashSet(network.poles());
        LongArrayList frontier = new LongArrayList();
        LongArrayList neighbours = new LongArrayList();

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().nextLong();
            remaining.remove(seed);

            LongOpenHashSet part = new LongOpenHashSet();
            part.add(seed);
            frontier.clear();
            frontier.add(seed);

            while (!frontier.isEmpty()) {
                long pole = frontier.removeLong(frontier.size() - 1);
                neighbours.clear();
                collectWireNeighbours(pole, neighbours);
                for (int i = 0; i < neighbours.size(); i++) {
                    long other = neighbours.getLong(i);
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

    /** Whether any pole of {@code network} supplies {@code endpoint}. */
    private boolean reaches(PowerNetwork network, long endpoint) {
        LongOpenHashSet poles = polesBySuppliedChunk.get(ChunkPos.pack(BlockPos.of(endpoint)));
        if (poles == null) {
            return false;
        }
        for (LongIterator it = poles.iterator(); it.hasNext();) {
            long pole = it.nextLong();
            if (network.poles().contains(pole) && supplies(pole, endpoint)) {
                return true;
            }
        }
        return false;
    }

    /** Forgets the machines that the pole at {@code removed} was the last one reaching. */
    private void dropUnreachedEndpoints(PowerNetwork network, long removed) {
        forEachSupplyPosition(removed, position -> {
            if (network.hasEndpoint(position) && !reaches(network, position)) {
                network.removeEndpoint(position);
            }
        });
    }

    private void scanSupplyArea(long pole, PowerNetwork network) {
        forEachSupplyPosition(pole, network::addEndpoint);
    }

    // --- indexes ------------------------------------------------------------------------------

    private void index(long pole) {
        polesByCell.computeIfAbsent(cellOf(pole), key -> new LongOpenHashSet()).add(pole);
        forEachSuppliedChunk(pole, chunk ->
                polesBySuppliedChunk.computeIfAbsent(chunk, key -> new LongOpenHashSet()).add(pole));
    }

    private void unindex(long pole) {
        LongOpenHashSet cell = polesByCell.get(cellOf(pole));
        if (cell != null && cell.remove(pole) && cell.isEmpty()) {
            polesByCell.remove(cellOf(pole));
        }
        forEachSuppliedChunk(pole, chunk -> {
            LongOpenHashSet poles = polesBySuppliedChunk.get(chunk);
            if (poles != null && poles.remove(pole) && poles.isEmpty()) {
                polesBySuppliedChunk.remove(chunk);
            }
        });
    }

    private static long cellOf(long pos) {
        return BlockPos.asLong(
                BlockPos.getX(pos) >> CELL_BITS,
                BlockPos.getY(pos) >> CELL_BITS,
                BlockPos.getZ(pos) >> CELL_BITS);
    }

    /**
     * Every pole within wire reach of {@code pole}, itself excluded.
     *
     * <p>27 cells, because wire reach is smaller than a cell and two poles that can see each
     * other therefore cannot be more than one cell apart on any axis.
     */
    private void collectWireNeighbours(long pole, LongArrayList out) {
        int cellX = BlockPos.getX(pole) >> CELL_BITS;
        int cellY = BlockPos.getY(pole) >> CELL_BITS;
        int cellZ = BlockPos.getZ(pole) >> CELL_BITS;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LongOpenHashSet cell =
                            polesByCell.get(BlockPos.asLong(cellX + dx, cellY + dy, cellZ + dz));
                    if (cell == null) {
                        continue;
                    }
                    for (LongIterator it = cell.iterator(); it.hasNext();) {
                        long other = it.nextLong();
                        if (other != pole && withinWireReach(pole, other)) {
                            out.add(other);
                        }
                    }
                }
            }
        }
    }

    private static boolean withinWireReach(long a, long b) {
        double dx = BlockPos.getX(a) - BlockPos.getX(b);
        double dy = BlockPos.getY(a) - BlockPos.getY(b);
        double dz = BlockPos.getZ(a) - BlockPos.getZ(b);
        return dx * dx + dy * dy + dz * dz <= WIRE_REACH_SQ;
    }

    private static boolean supplies(long pole, long position) {
        return Math.abs(BlockPos.getX(pole) - BlockPos.getX(position)) <= SUPPLY_RADIUS
                && Math.abs(BlockPos.getY(pole) - BlockPos.getY(position)) <= SUPPLY_RADIUS
                && Math.abs(BlockPos.getZ(pole) - BlockPos.getZ(position)) <= SUPPLY_RADIUS;
    }

    private interface PositionSink {
        void accept(long position);
    }

    private interface ChunkSink {
        void accept(long chunk);
    }

    private static void forEachSupplyPosition(long pole, PositionSink sink) {
        int x = BlockPos.getX(pole);
        int y = BlockPos.getY(pole);
        int z = BlockPos.getZ(pole);
        for (int dx = -SUPPLY_RADIUS; dx <= SUPPLY_RADIUS; dx++) {
            for (int dy = -SUPPLY_RADIUS; dy <= SUPPLY_RADIUS; dy++) {
                for (int dz = -SUPPLY_RADIUS; dz <= SUPPLY_RADIUS; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        sink.accept(BlockPos.asLong(x + dx, y + dy, z + dz));
                    }
                }
            }
        }
    }

    private static void forEachSuppliedChunk(long pole, ChunkSink sink) {
        int minX = (BlockPos.getX(pole) - SUPPLY_RADIUS) >> 4;
        int maxX = (BlockPos.getX(pole) + SUPPLY_RADIUS) >> 4;
        int minZ = (BlockPos.getZ(pole) - SUPPLY_RADIUS) >> 4;
        int maxZ = (BlockPos.getZ(pole) + SUPPLY_RADIUS) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                sink.accept(ChunkPos.pack(x, z));
            }
        }
    }

    // --- what the tests look at ---------------------------------------------------------------

    /** The network the pole at {@code pos} belongs to, or null if there is no pole there. */
    public @Nullable PowerNetwork networkAt(BlockPos pos) {
        return networkByPole.get(pos.asLong());
    }

    /** Every network in this level, whether or not it is doing anything. */
    public Collection<PowerNetwork> networks() {
        return networks;
    }

    /**
     * Whether this network is being ticked every tick.
     *
     * <p>The assertion non-negotiable #5 turns into for a graph: a network whose consumers are
     * all full moved nothing on its last tick, so it is not in here, and stops being visited
     * twenty times a second.
     */
    public boolean isActive(PowerNetwork network) {
        return active.contains(network);
    }

    public int activeCount() {
        return active.size();
    }
}
