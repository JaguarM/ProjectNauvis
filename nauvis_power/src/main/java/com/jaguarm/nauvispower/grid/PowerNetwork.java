package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.transfer.EnergyBuffer;
import com.jaguarm.nauvislib.transfer.GeneratorAccess;
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
 * <h2>Producers and consumers are not told apart; batteries are</h2>
 *
 * <p>There is one endpoint table, not two. A steam engine refuses insertion - see
 * {@link GeneratorAccess} - and a machine refuses extraction, so asking every endpoint for both
 * costs one virtual call that returns zero, and there is no classification to go stale when a
 * machine is replaced by a different one.
 *
 * <p>An accumulator refuses neither, and that is why it is the one endpoint that has to say what
 * it is: a handler carrying {@link EnergyBuffer} is a <b>battery</b>, and Factorio's rule for a
 * battery is the third case of the tick. It takes only what the generators leave over once every
 * machine is fed, and it gives only what the generators cannot cover. Two batteries never trade,
 * because a battery is never counted as demand and never drawn on for surplus - so a full one and
 * an empty one on the same network sit still, which is what lets the network sleep.
 *
 * <h2>The tick, in three passes</h2>
 *
 * <p>Demand is measured first, in a transaction that is deliberately never committed, and only
 * then is exactly that much pulled from the producers - and from the batteries, for whatever the
 * producers fell short by. Doing it the other way round - fill a budget, then find out nobody
 * wants it - would leave energy in hand with nowhere to put it and no way to give it back, because
 * a transaction rolls back whole or not at all. Then, if the producers covered the demand alone,
 * whatever they still have goes into the batteries, each in a nested transaction so one that goes
 * back on its word costs only its own charge.
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

    // Scratch, reused every tick. A network ticks twenty times a second; allocating six
    // collections each time is the sort of garbage that only shows up as a stutter much later.
    private final LongArrayList hungry = new LongArrayList();
    private final IntArrayList wanted = new IntArrayList();
    private final LongOpenHashSet hungrySet = new LongOpenHashSet();
    private final LongArrayList buffers = new LongArrayList();
    private final IntArrayList room = new IntArrayList();
    private final LongOpenHashSet bufferSet = new LongOpenHashSet();
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
     * How many machines this network reaches - machines, not blocks, and batteries among them.
     *
     * <p>Counted rather than stored, because it is read when a player right-clicks a pole and
     * nowhere else. See {@link #distinct} for why the two numbers differ.
     */
    public int endpointCount() {
        return distinctHandlers().size();
    }

    /** How many batteries this network reaches. For the readout, like {@link #endpointCount}. */
    public int bufferCount() {
        int count = 0;
        for (EnergyHandler handler : distinctHandlers()) {
            if (handler instanceof EnergyBuffer) {
                count++;
            }
        }
        return count;
    }

    /** What the network's batteries hold between them. For the readout. */
    public long storedInBuffers() {
        long stored = 0;
        for (EnergyHandler handler : distinctHandlers()) {
            if (handler instanceof EnergyBuffer) {
                stored += handler.getAmountAsLong();
            }
        }
        return stored;
    }

    /** What the network's batteries could hold between them. For the readout. */
    public long bufferCapacity() {
        long capacity = 0;
        for (EnergyHandler handler : distinctHandlers()) {
            if (handler instanceof EnergyBuffer) {
                capacity += handler.getCapacityAsLong();
            }
        }
        return capacity;
    }

    private Set<EnergyHandler> distinctHandlers() {
        Set<EnergyHandler> machines = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var cache : endpoints.values()) {
            EnergyHandler handler = cache.getCapability();
            if (handler != null) {
                machines.add(handler);
            }
        }
        return machines;
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
     * Moves as much energy as the consumers will take and the producers can give, and banks what
     * is left over.
     *
     * @return how much moved. Zero means there was nothing to do, and the manager stops ticking
     *         this network every tick until something changes.
     */
    int tick() {
        hungry.clear();
        wanted.clear();
        hungrySet.clear();
        buffers.clear();
        room.clear();
        bufferSet.clear();
        gone.clear();

        long demand = measureDemand();
        dropMissing();
        if (demand <= 0 && !anyRoom()) {
            return 0;
        }

        return deliver((int) Math.min(demand, MAX_TRANSFER));
    }

    private boolean anyRoom() {
        for (int i = 0; i < room.size(); i++) {
            if (room.getInt(i) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asks every endpoint how much it would take, and takes nothing.
     *
     * <p>The transaction is never committed, so every insertion below rolls back on the way out
     * of the block. This is the "can I?" half of the pattern the whole pack uses, and it is the
     * same call as the "do it" half, so the two cannot drift.
     *
     * <p>A battery is asked the same question and the answer is kept apart: what it would take is
     * its room for surplus, not demand a generator has to meet.
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
                if (handler instanceof EnergyBuffer) {
                    buffers.add(pos);
                    room.add(want);
                    bufferSet.add(pos);
                    continue;
                }
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

    /**
     * Pulls up to {@code target} from the producers - and from the batteries for the rest - hands
     * it out, then banks the producers' surplus in the batteries. Or does nothing at all.
     */
    private int deliver(int target) {
        try (Transaction transfer = Transaction.openRoot()) {
            int supply = drawFromGenerators(target, transfer);

            // The shortfall is the batteries' to cover, and only the shortfall: a battery that fed
            // a machine the generators could have fed would be a battery that never fills.
            int discharged = 0;
            for (int i = 0; i < buffers.size() && supply < target; i++) {
                EnergyHandler battery = handlerAt(buffers.getLong(i));
                if (battery != null) {
                    int taken = battery.extract(target - supply, transfer);
                    supply += taken;
                    discharged += taken;
                }
            }

            int placed = 0;
            for (int i = 0; i < hungry.size() && placed < supply; i++) {
                EnergyHandler handler = handlerAt(hungry.getLong(i));
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

            // Surplus goes to the batteries, and only when there was one: a tick the batteries
            // had to help on is a tick the generators had nothing spare.
            int charged = discharged == 0 ? chargeBuffers(transfer) : 0;

            if (placed + charged <= 0) {
                return 0;
            }
            transfer.commit();
            return placed + charged;
        }
    }

    /** Extracts up to {@code target} from everything that is neither hungry nor a battery. */
    private int drawFromGenerators(int target, Transaction transaction) {
        int supply = 0;
        for (var entry : endpoints.long2ObjectEntrySet()) {
            if (supply >= target) {
                break;
            }
            long pos = entry.getLongKey();
            // Skip anything that wanted energy, so two half-full machines cannot spend the tick
            // passing the same joule back and forth; anything that is another block of a machine
            // already dealt with, so one cannot do it to itself; and every battery, which gives
            // only into a shortfall and never as a generator.
            if (hungrySet.contains(pos) || duplicates.contains(pos) || bufferSet.contains(pos)) {
                continue;
            }
            EnergyHandler handler = entry.getValue().getCapability();
            if (handler != null) {
                supply += handler.extract(target - supply, transaction);
            }
        }
        return supply;
    }

    /**
     * Fills each battery with what the generators still have, one nested transaction a battery.
     *
     * <p>Nested so that a battery which takes less than it said costs only its own charge: the
     * machines have already been fed inside the parent, and a rollback here does not touch them.
     * The first battery the generators cannot fill ends it - there is nothing left for the rest.
     */
    private int chargeBuffers(Transaction parent) {
        int charged = 0;
        for (int i = 0; i < buffers.size(); i++) {
            int want = room.getInt(i);
            if (want <= 0) {
                continue;
            }
            EnergyHandler battery = handlerAt(buffers.getLong(i));
            if (battery == null) {
                continue;
            }
            try (Transaction charge = Transaction.open(parent)) {
                int spare = drawFromGenerators(want, charge);
                if (spare <= 0) {
                    break;
                }
                if (battery.insert(spare, charge) != spare) {
                    continue;
                }
                charge.commit();
                charged += spare;
            }
        }
        return charged;
    }

    private @Nullable EnergyHandler handlerAt(long pos) {
        var cache = endpoints.get(pos);
        return cache == null ? null : cache.getCapability();
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
