package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.multiblock.MachineShape;
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


    /**
     * Factorio's small and medium poles supply a 5x5 area - two blocks either side of a one-tile
     * pole. Kept as a cube in Y here, because a machine stacked above another is a reasonable
     * thing to build and refusing it would be a rule the player has to learn for no reason.
     *
     * <p>The big pole is 4x4, which is not a smaller radius around a bigger point: it is its
     * two-by-two footprint plus one tile on every side. So supply is measured from the
     * <em>footprint</em> rather than from the foot, and a one-tile pole with a reach of two comes
     * out at exactly the 5x5 it always was. See {@link Pole}.
     */
    public static final int SUPPLY_RADIUS = 2;

    /**
     * Cell size for the pole index, as a shift.
     *
     * <p>It was eight while every pole reached 7.5, and the neighbourhood was the 27 cells around
     * one, which is complete only while the cell is wider than the reach. The medium pole reaches
     * nine, so cells are sixteen; the alternative - keeping eight and widening the neighbourhood -
     * covers a smaller volume for more lookups.
     *
     * <p>The big pole reaches thirty and no sane cell size covers that, so the scan is sized from
     * the asking pole's own reach ({@link #cellSpan}) and poles that out-reach a cell are held in
     * {@link #longReach} as well, where everybody looks. **That second index is not an
     * optimisation, it is the correctness.** Without it, a small pole would find a big one only
     * when the big one happened to fall inside the small one's own three-cell scan, so a wire
     * would be there or not depending on where the two stood - and both halves would look right
     * in isolation.
     */
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
     * <p>Four numbers rather than a reference to the block, because every one of them is still
     * wanted after the block is gone: a pole leaving is the expensive half of its life, and every
     * question asked on that path - what it was wired to, what it was the last one supplying - is
     * about a pole that no longer exists.
     *
     * @param wireReach   how far it throws a wire, in blocks
     * @param width       its footprint east-west, in blocks, from the foot outwards
     * @param depth       its footprint north-south
     * @param supplyReach how far past that footprint it supplies machines. Factorio's areas are
     *                    5x5 for a one-tile pole and 4x4 for the two-tile one, which is this plus
     *                    the footprint in both cases and is not a radius in either
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
     * <p>Only the client cares - see {@code ElectricPoleBlockEntity#links}. It runs on
     * placement and removal, which is the only time a wire can appear or disappear, and never on a
     * tick. The neighbours have to be told too: a wire has two ends, and the one that already
     * existed does not otherwise know that something just came into view.
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
     * The same, for a pole the index no longer holds - which is every caller on the removal path,
     * because the pole is out of the index before its consequences are worked out.
     *
     * <p>Two passes, and the second is not a shortcut. The cell scan is sized from <em>this</em>
     * pole's reach, so it finds everything this pole can see; what it cannot find is a pole that
     * out-reaches it from further away, and a wire runs on the longer of the two reaches. That is
     * what {@link #longReach} is for, and skipping the ones the first pass already covered is what
     * keeps a neighbour from being listed twice.
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
     * Whether a wire runs between two poles: the distance is within the <em>longer</em> of the two
     * reaches.
     *
     * <p>The longer and not the shorter, which is Factorio's rule and not an accident of it. A
     * medium pole is bought to span a gap, and a gap has a small pole at each end of it as often
     * as not; a rule that took the shorter reach would make a medium pole useless everywhere
     * except in a line of other medium poles. It also keeps the relation symmetric, which the
     * flood fill in {@link #components} quietly depends on - an asymmetric one would put two poles
     * in the same network or not depending on which end the walk started from.
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

    /**
     * Whether a pole supplies a position: inside its footprint grown by its own margin.
     *
     * <p>Horizontally that is Factorio's area exactly - 5x5 around a one-tile pole with a margin
     * of two, 4x4 around a two-tile one with a margin of one, 18x18 around a substation with a
     * margin of eight. Vertically it stays two blocks either side of the <em>foot</em> for every
     * tier, which is deliberate: a taller pole is not a pole that feeds machines further into the
     * sky, and letting a substation supply eight blocks up would put a machine on a roof on the
     * grid without the player doing anything to put it there.
     */
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
