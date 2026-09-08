package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
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

/** Every electric network in one level, and the only thing in this mod that runs every tick. */
public final class PowerNetworkManager {


    /**
     * Factorio's small and medium poles supply a 5x5 area - two blocks either side of a
     * one-tile pole. Kept as a cube in Y here, because a machine stacked above another is a
     * reasonable thing to build and refusing it would be a rule the player has to learn for no
     * reason.
     */
    public static final int SUPPLY_RADIUS = 2;

    /** Cell size for the pole index, as a shift. */
    private static final int CELL_BITS = 4;

    private static final int CELL_SIZE = 1 << CELL_BITS;

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

    /**
     * What each indexed pole is, so the graph never has to look at a block state.
     *
     * <p>Read off the block once, when the pole joins. Looking it up on demand would mean a block
     * state read per candidate inside {@link #collectWireNeighbours}, and one of the two poles in
     * that comparison has often just been broken - so the block is already gone and the answer
     * would be wrong exactly when it matters.
     */
    private final Long2ObjectOpenHashMap<Pole> poleData = new Long2ObjectOpenHashMap<>();

    /**
     * The poles that reach further than a cell of the index is wide.
     *
     * <p>Scanned in full by every lookup, which is affordable only because there are few of them -
     * a big pole is what a base has tens of, not thousands. See {@link #CELL_BITS}.
     */
    private final LongOpenHashSet longReach = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> polesByCell = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> polesBySuppliedChunk = new Long2ObjectOpenHashMap<>();

    private final Set<PowerNetwork> networks = new LinkedHashSet<>();
    private final Set<PowerNetwork> active = new LinkedHashSet<>();

    /** Poles whose supply area needs a full rescan, applied at the start of the next tick. */
    private final LongOpenHashSet pendingPoles = new LongOpenHashSet();

    /** Single positions where something changed near a pole. Deduplicated over the tick. */
    private final LongOpenHashSet pendingBlocks = new LongOpenHashSet();

    private long tickCount;

    /**
     * What the graph needs to know about a pole, read off its block when it joins.
     *
     * @param wireReach   how far it throws a wire, in blocks
     * @param width       its footprint east-west, in blocks, from the foot outwards
     * @param depth       its footprint north-south
     * @param supplyReach how far past that footprint it supplies machines. Factorio's areas are
     */
    private record Pole(double wireReach, int width, int depth, int supplyReach) {}

    /**
     * What an unindexed pole is taken to be: a small one.
     *
     * <p>Reached only for a pole that has already left, and only on the paths that pass their own
     * details in anyway, so in practice it is never the answer to anything. It is the short reach
     * rather than the long one because a wrong short answer loses a wire and a wrong long one
     * invents a network.
     */
    private static final Pole UNKNOWN =
            new Pole(SmallElectricPoleBlock.WIRE_REACH, 1, 1, SUPPLY_RADIUS);

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
    public void polePlaced(BlockPos pos, ElectricPoleBlock block) {
        long key = pos.asLong();
        if (networkByPole.containsKey(key)) {
            return;
        }
        index(key, describe(block));

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
        refreshWires(key, reachable);
    }

    /** Everything the graph keeps about a pole, taken off its block once. */
    private static Pole describe(ElectricPoleBlock block) {
        MachineShape shape = block.shape();
        return new Pole(block.wireReach(), shape.width(), shape.depth(), block.supplyReach());
    }

    /**
     * A pole is gone: drop the machines only it reached, and split the network if it was the
     * only thing holding two halves together.
     */
    public void poleRemoved(BlockPos pos) {
        long key = pos.asLong();
        PowerNetwork network = networkByPole.remove(key);
        // Its own numbers have to outlive the index entry: everything below asks what this pole
        // could see and what it supplied, and a pole that has left still decides half of that.
        Pole self = poleOf(key);
        unindex(key);
        pendingPoles.remove(key);
        if (network == null) {
            return;
        }

        network.removePole(key);
        dropUnreachedEndpoints(network, key, self);

        // This pole is already out of the index, so what it could reach is what is left standing.
        LongArrayList orphaned = new LongArrayList();
        collectWireNeighbours(key, self, orphaned);
        for (int i = 0; i < orphaned.size(); i++) {
            refreshWires(orphaned.getLong(i), null);
        }

        if (network.poles().isEmpty()) {
            networks.remove(network);
            active.remove(network);
            return;
        }

        splitIfSevered(network, key, self);
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

    /**
     * Tells a pole, and everything it can see, what they are wired to.
     *
     * @param reachable the poles in range, if the caller already worked them out.
     */
    private void refreshWires(long pole, @Nullable LongArrayList reachable) {
        LongArrayList wired = reachable;
        if (wired == null) {
            wired = new LongArrayList();
            collectWireNeighbours(pole, wired);
        }
        setLinks(pole, wired);

        for (int i = 0; i < wired.size(); i++) {
            long neighbour = wired.getLong(i);
            LongArrayList theirs = new LongArrayList();
            collectWireNeighbours(neighbour, theirs);
            setLinks(neighbour, theirs);
        }
    }

    private void setLinks(long pole, LongArrayList wired) {
        BlockPos pos = BlockPos.of(pole);
        // isLoaded first: asking for a block entity in an unloaded chunk would load it.
        if (level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof ElectricPoleBlockEntity entity) {
            entity.setLinks(wired.toLongArray());
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
    private void splitIfSevered(PowerNetwork network, long removed, Pole removedPole) {
        LongArrayList seeds = new LongArrayList();
        collectWireNeighbours(removed, removedPole, seeds);
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
    private void dropUnreachedEndpoints(PowerNetwork network, long removed, Pole data) {
        forEachSupplyPosition(removed, data, position -> {
            if (network.hasEndpoint(position) && !reaches(network, position)) {
                network.removeEndpoint(position);
            }
        });
    }

    private void scanSupplyArea(long pole, PowerNetwork network) {
        forEachSupplyPosition(pole, poleOf(pole), network::addEndpoint);
    }

    // --- indexes ------------------------------------------------------------------------------

    private void index(long pole, Pole data) {
        poleData.put(pole, data);
        polesByCell.computeIfAbsent(cellOf(pole), key -> new LongOpenHashSet()).add(pole);
        if (data.wireReach() > CELL_SIZE) {
            longReach.add(pole);
        }
        forEachSuppliedChunk(pole, data, chunk ->
                polesBySuppliedChunk.computeIfAbsent(chunk, key -> new LongOpenHashSet()).add(pole));
    }

    private void unindex(long pole) {
        Pole data = poleOf(pole);
        poleData.remove(pole);
        longReach.remove(pole);
        LongOpenHashSet cell = polesByCell.get(cellOf(pole));
        if (cell != null && cell.remove(pole) && cell.isEmpty()) {
            polesByCell.remove(cellOf(pole));
        }
        forEachSuppliedChunk(pole, data, chunk -> {
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
        collectWireNeighbours(pole, poleOf(pole), out);
    }

    /**
     * The same, for a pole the index no longer holds - which is every caller on the removal
     * path, because the pole is out of the index before its consequences are worked out.
     */
    private void collectWireNeighbours(long pole, Pole self, LongArrayList out) {
        int cellX = BlockPos.getX(pole) >> CELL_BITS;
        int cellY = BlockPos.getY(pole) >> CELL_BITS;
        int cellZ = BlockPos.getZ(pole) >> CELL_BITS;
        int span = cellSpan(self.wireReach());

        for (int dx = -span; dx <= span; dx++) {
            for (int dy = -span; dy <= span; dy++) {
                for (int dz = -span; dz <= span; dz++) {
                    LongOpenHashSet cell =
                            polesByCell.get(BlockPos.asLong(cellX + dx, cellY + dy, cellZ + dz));
                    if (cell == null) {
                        continue;
                    }
                    for (LongIterator it = cell.iterator(); it.hasNext();) {
                        long other = it.nextLong();
                        if (other != pole && withinWireReach(pole, self, other)) {
                            out.add(other);
                        }
                    }
                }
            }
        }

        for (LongIterator it = longReach.iterator(); it.hasNext();) {
            long other = it.nextLong();
            if (other == pole || withinCells(cellX, cellY, cellZ, other, span)) {
                continue;  // itself, or already looked at by the scan above
            }
            if (withinWireReach(pole, self, other)) {
                out.add(other);
            }
        }
    }

    /**
     * How many cells away a pole reaching this far can still be.
     *
     * <p>Two positions at most {@code reach} apart differ by at most
     * {@code floor((CELL_SIZE - 1 + reach) / CELL_SIZE)} cells, the worst case being a pole
     * sitting hard against a cell boundary. The reach is ceilinged first, since 7.5 has to cover
     * 7.5 rather than 7.
     */
    private static int cellSpan(double reach) {
        return (CELL_SIZE - 1 + (int) Math.ceil(reach)) / CELL_SIZE;
    }

    private static boolean withinCells(int cellX, int cellY, int cellZ, long other, int span) {
        return Math.abs((BlockPos.getX(other) >> CELL_BITS) - cellX) <= span
                && Math.abs((BlockPos.getY(other) >> CELL_BITS) - cellY) <= span
                && Math.abs((BlockPos.getZ(other) >> CELL_BITS) - cellZ) <= span;
    }

    /**
     * Whether a wire runs between two poles: the distance is within the <em>longer</em> of the
     * two reaches.
     */
    private boolean withinWireReach(long a, Pole poleA, long b) {
        double dx = BlockPos.getX(a) - BlockPos.getX(b);
        double dy = BlockPos.getY(a) - BlockPos.getY(b);
        double dz = BlockPos.getZ(a) - BlockPos.getZ(b);
        double reach = Math.max(poleA.wireReach(), poleOf(b).wireReach());
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** What the index says a pole is, or {@link #UNKNOWN} if it is not in there. */
    private Pole poleOf(long pole) {
        return poleData.getOrDefault(pole, UNKNOWN);
    }

    /** Whether a pole supplies a position: inside its footprint grown by its own margin. */
    private boolean supplies(long pole, long position) {
        return supplies(pole, poleOf(pole), position);
    }

    private static boolean supplies(long pole, Pole data, long position) {
        int dx = BlockPos.getX(position) - BlockPos.getX(pole);
        int dz = BlockPos.getZ(position) - BlockPos.getZ(pole);
        return dx >= -data.supplyReach() && dx < data.width() + data.supplyReach()
                && dz >= -data.supplyReach() && dz < data.depth() + data.supplyReach()
                && Math.abs(BlockPos.getY(pole) - BlockPos.getY(position)) <= SUPPLY_RADIUS;
    }

    private interface PositionSink {
        void accept(long position);
    }

    private interface ChunkSink {
        void accept(long chunk);
    }

    private static void forEachSupplyPosition(long pole, Pole data, PositionSink sink) {
        int x = BlockPos.getX(pole);
        int y = BlockPos.getY(pole);
        int z = BlockPos.getZ(pole);
        int margin = data.supplyReach();
        for (int dx = -margin; dx < data.width() + margin; dx++) {
            for (int dy = -SUPPLY_RADIUS; dy <= SUPPLY_RADIUS; dy++) {
                for (int dz = -margin; dz < data.depth() + margin; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        sink.accept(BlockPos.asLong(x + dx, y + dy, z + dz));
                    }
                }
            }
        }
    }

    private static void forEachSuppliedChunk(long pole, Pole data, ChunkSink sink) {
        int margin = data.supplyReach();
        int minX = (BlockPos.getX(pole) - margin) >> 4;
        int maxX = (BlockPos.getX(pole) + data.width() - 1 + margin) >> 4;
        int minZ = (BlockPos.getZ(pole) - margin) >> 4;
        int maxZ = (BlockPos.getZ(pole) + data.depth() - 1 + margin) >> 4;
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
