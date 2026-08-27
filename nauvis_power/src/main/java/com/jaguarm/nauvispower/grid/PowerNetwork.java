package com.jaguarm.nauvispower.grid;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * One connected electric network: every pole that can reach every other, and the machines those
 * poles supply.
 *
 * <p><b>This object ticks, not the poles.</b> PLAN.md's belt note with the nouns changed - an
 * electric network is one object and poles are members of it - and the whole reason a pole is an
 * architectural decision rather than a block. A pole that ticks costs N ticks a second for N
 * poles and takes N ticks to move a packet of energy across them. A network that ticks costs one
 * iteration however many poles it has, and energy crosses it instantly, which is what it does in
 * Factorio.
 *
 * <p>Poles are positions; they hold nothing and do nothing. Machines are
 * {@link BlockCapabilityCache} handles, so a transfer is never a capability lookup, and the cache
 * reports a machine being replaced or its chunk cycling without anybody polling.
 *
 * <h2>Producers and consumers are not told apart</h2>
 *
 * <p>There is one endpoint table, not two. A steam engine refuses insertion - see
 * {@code GeneratorAccess} - and a machine refuses extraction, so asking every endpoint for both
 * costs one virtual call that returns zero, and there is no classification to go stale when a
 * machine is replaced by a different one. It also means an accumulator, when there is one, is an
 * endpoint like any other. The one thing that cannot yet work is <em>discharging</em>: an
 * endpoint that accepts energy is skipped when supply is collected, so a battery will charge but
 * not feed the grid until this grows a third case.
 *
 * <h2>The tick, in two passes</h2>
 *
 * <p>Demand is measured first, in a transaction that is deliberately never committed, and only
 * then is exactly that much pulled from the producers. Doing it the other way round - fill a
 * budget, then find out nobody wants it - would leave energy in hand with nowhere to put it and
 * no way to give it back, because a transaction rolls back whole or not at all.
 */
public final class PowerNetwork {

    /**
     * The most one network moves in one tick.
     *
     * <p>Not a Factorio number and not meant to be one: Factorio's grid is lossless and has no
     * throughput limit at all, and the real cap here is what each machine's own handler takes in
     * a tick. This exists so the sums stay in an {@code int}.
     */
    static final int MAX_TRANSFER = 1_000_000;

    private final ServerLevel level;

    /** Member poles, packed. A pole is a position and nothing else. */
    private final LongOpenHashSet poles = new LongOpenHashSet();

    /** Machines in range of a member pole. Linked, so a shortfall is met in a stable order. */
    private final Long2ObjectLinkedOpenHashMap<BlockCapabilityCache<EnergyHandler, @Nullable Direction>>
            endpoints = new Long2ObjectLinkedOpenHashMap<>();

    // Scratch, reused every tick. A network ticks twenty times a second; allocating four
    // collections each time is the sort of garbage that only shows up as a stutter much later.
    private final LongArrayList hungry = new LongArrayList();
    private final IntArrayList wanted = new IntArrayList();
    private final LongOpenHashSet hungrySet = new LongOpenHashSet();
    private final LongArrayList gone = new LongArrayList();

    /**
     * One machine, one share, however many blocks it is made of.
     *
     * <p>A machine with a footprint offers its energy handler at every block it occupies, so that
     * a pole supplies it if its area covers any part of it - which is Factorio's rule and the
     * reason the footprints were worth having. The cost is that one steam engine can appear in a
     * pole's supply area five times over.
     *
     * <p>The same handler five times is not five machines. Left alone it would be five shares of
     * a shortfall to one engine, five entries in the count a player reads off a pole, and - once
     * something is both a producer and a consumer - a machine sold energy it had just asked for,
     * through two of its own blocks. So each tick the endpoints are reduced to distinct handlers,
     * by object identity, and the positions that were duplicates sit the tick out.
     *
     * <p>Identity rather than position, because the network has no idea what a multi-block is and
     * should not learn: any mod whose machine hands out one handler from several blocks gets this
     * for free, and one that hands out a fresh wrapper each time is no worse off than before.
     */
    private final Set<EnergyHandler> distinct =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final LongOpenHashSet duplicates = new LongOpenHashSet();

    /** Set when this network has been merged into another and must no longer be ticked. */
    private boolean merged;

    PowerNetwork(ServerLevel level) {
        this.level = level;
    }

    // --- membership -------------------------------------------------------------------------

    boolean isMerged() {
        return merged;
    }

    void markMerged() {
        merged = true;
    }

    LongOpenHashSet poles() {
        return poles;
    }

    void addPole(long pos) {
        poles.add(pos);
    }

    void removePole(long pos) {
        poles.remove(pos);
    }

    public int poleCount() {
        return poles.size();
    }

    /**
     * How many machines this network reaches - machines, not blocks.
     *
     * <p>Counted rather than stored, because it is read when a player right-clicks a pole and
     * nowhere else. See {@link #distinct} for why the two numbers differ.
     */
    public int endpointCount() {
        Set<EnergyHandler> machines = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var cache : endpoints.values()) {
            EnergyHandler handler = cache.getCapability();
            if (handler != null) {
                machines.add(handler);
            }
        }
        return machines.size();
    }

    public boolean hasEndpoint(long pos) {
        return endpoints.containsKey(pos);
    }

    LongArrayList endpointPositions() {
        return new LongArrayList(endpoints.keySet());
    }

    /**
     * Starts watching the machine at {@code pos}, if there is one there to watch.
     *
     * <p>Idempotent by design. A machine in the supply area of three poles is offered three
     * times, and a network that had to be told exactly once would need somebody to keep count.
     */
    void addEndpoint(long pos) {
        if (endpoints.containsKey(pos)) {
            return;
        }
        BlockPos blockPos = BlockPos.of(pos);
        // isLoaded first, and not as an optimisation: asking for a capability in an unloaded
        // chunk loads it, so a pole placed at the edge of the loaded world would drag in its
        // neighbours. The chunk loading is itself a trigger to look again - see the manager.
        if (!level.isLoaded(blockPos)
                || level.getCapability(Capabilities.Energy.BLOCK, blockPos, null) == null) {
            return;
        }
        endpoints.put(pos, BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, blockPos, null));
    }

    void removeEndpoint(long pos) {
        endpoints.remove(pos);
    }

    /** Takes a cache another network already built, when this one absorbs or succeeds it. */
    void adoptEndpoint(long pos, BlockCapabilityCache<EnergyHandler, @Nullable Direction> cache) {
        endpoints.putIfAbsent(pos, cache);
    }

    @Nullable
    BlockCapabilityCache<EnergyHandler, @Nullable Direction> endpoint(long pos) {
        return endpoints.get(pos);
    }

    /** Whether ticking this could possibly do anything. A network of bare poles cannot. */
    boolean couldWork() {
        return !merged && !poles.isEmpty() && !endpoints.isEmpty();
    }

    // --- the tick ---------------------------------------------------------------------------

    /**
     * Moves as much energy as the consumers will take and the producers can give.
     *
     * @return how much moved. Zero means there was nothing to do, and the manager stops ticking
     *         this network every tick until something changes.
     */
    int tick() {
        hungry.clear();
        wanted.clear();
        hungrySet.clear();
        gone.clear();

        long demand = measureDemand();
        dropMissing();
        if (demand <= 0) {
            return 0;
        }

        return deliver((int) Math.min(demand, MAX_TRANSFER));
    }

    /**
     * Asks every endpoint how much it would take, and takes nothing.
     *
     * <p>The transaction is never committed, so every insertion below rolls back on the way out
     * of the block. This is the "can I?" half of the pattern the whole pack uses, and it is the
     * same call as the "do it" half, so the two cannot drift.
     */
    private long measureDemand() {
        long demand = 0;
        distinct.clear();
        duplicates.clear();
        try (Transaction probe = Transaction.openRoot()) {
            for (var entry : endpoints.long2ObjectEntrySet()) {
                long pos = entry.getLongKey();
                EnergyHandler handler = live(pos, entry.getValue());
                if (handler == null) {
                    continue;
                }
                // Another block of a machine already counted. See distinct.
                if (!distinct.add(handler)) {
                    duplicates.add(pos);
                    continue;
                }
                int want = handler.insert(MAX_TRANSFER, probe);
                if (want > 0) {
                    hungry.add(pos);
                    wanted.add(want);
                    hungrySet.add(pos);
                    demand += want;
                }
            }
            // Deliberately not committed.
        }
        return demand;
    }

    /** Pulls up to {@code target} from the producers and hands it out, or does nothing at all. */
    private int deliver(int target) {
        try (Transaction transfer = Transaction.openRoot()) {
            int supply = 0;
            for (var entry : endpoints.long2ObjectEntrySet()) {
                if (supply >= target) {
                    break;
                }
                // Skip anything that wanted energy, so two half-full machines cannot spend the
                // tick passing the same joule back and forth - and anything that is another
                // block of a machine already dealt with, so one cannot do it to itself.
                if (hungrySet.contains(entry.getLongKey())
                        || duplicates.contains(entry.getLongKey())) {
                    continue;
                }
                EnergyHandler handler = entry.getValue().getCapability();
                if (handler != null) {
                    supply += handler.extract(target - supply, transfer);
                }
            }

            if (supply <= 0) {
                return 0;
            }

            int placed = 0;
            for (int i = 0; i < hungry.size() && placed < supply; i++) {
                var cache = endpoints.get(hungry.getLong(i));
                if (cache == null) {
                    continue;
                }
                EnergyHandler handler = cache.getCapability();
                if (handler != null) {
                    placed += handler.insert(Math.min(wanted.getInt(i), supply - placed), transfer);
                }
            }

            if (placed < supply) {
                // A consumer went back on what it said it would take. Falling out of the block
                // uncommitted puts the energy back where it came from: nothing moved, nothing was
                // lost, and the next tick tries again.
                return 0;
            }

            transfer.commit();
            return placed;
        }
    }

    /**
     * The handler at {@code pos}, noting the endpoint for removal if the machine is really gone.
     *
     * <p>Null with the chunk still loaded means the block was broken or replaced. Null with the
     * chunk unloaded means only that - the machine is coming back, and its cache will start
     * answering again by itself, so dropping it would cost a rediscovery for nothing.
     */
    private @Nullable EnergyHandler live(
            long pos, BlockCapabilityCache<EnergyHandler, @Nullable Direction> cache) {
        EnergyHandler handler = cache.getCapability();
        if (handler == null && level.isLoaded(BlockPos.of(pos))) {
            gone.add(pos);
        }
        return handler;
    }

    /** Applied after iteration, never during it. */
    private void dropMissing() {
        for (LongIterator it = gone.iterator(); it.hasNext();) {
            endpoints.remove(it.nextLong());
        }
    }
}
