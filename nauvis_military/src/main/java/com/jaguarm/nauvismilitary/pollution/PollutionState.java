package com.jaguarm.nauvismilitary.pollution;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The clouds: how much pollution is over each chunk of a level, where in each chunk it last came
 * from, and how much the level has ever made.
 *
 * <h2>Factorio's model, at chunk size</h2>
 *
 * <p>Factorio keeps pollution per chunk - a number over each 32-by-32 - and every minute a chunk
 * gives two percent of what it holds to each of its four neighbours and the ground absorbs some.
 * This is the same, on Minecraft's 16-by-16 chunks: {@link #SPREAD} to each neighbour and an
 * absorption per chunk, every minute, in {@link #drift}. What the ground takes is the caller's
 * to say - {@link Absorption} reads the biome, so a forest takes more than a desert - and the
 * flat {@link #ABSORB_PER_MINUTE} is what a test with no world uses. What it gives is a cloud that
 * grows until the edges thin to nothing, which is the shape a Factorio cloud has.
 *
 * <p>The <b>source</b> of a chunk's cloud is the position of the last machine that breathed into
 * it. Factorio's attack groups walk to the polluters, so the cloud has to remember where they
 * are; one position a chunk is enough, since the group fights whatever else it finds there.
 *
 * <p>One of these per level, on that level's storage, since the clouds over the Nether are not the
 * clouds over the overworld. Pure arithmetic apart from the level it is fetched from, so the drift
 * is tested on a fresh one without a world.
 *
 * <p>The total is what the level has emitted since it began, which is what stands in for
 * Factorio's evolution: the longer and dirtier the factory, the worse what comes for it.
 */
public class PollutionState extends SavedData {

    /** What a chunk gives to each of its four neighbours a minute: Factorio's two percent. */
    public static final double SPREAD = 0.02;

    /** What the ground under a chunk takes back a minute, where nothing knows the ground. */
    public static final double ABSORB_PER_MINUTE = 5.0;

    private record Entry(int x, int z, double amount, Optional<BlockPos> source) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Codec.DOUBLE.fieldOf("amount").forGetter(Entry::amount),
                BlockPos.CODEC.optionalFieldOf("source").forGetter(Entry::source))
                .apply(i, Entry::new));
    }

    private record Snapshot(List<Entry> chunks, double total) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("chunks").forGetter(Snapshot::chunks),
                Codec.DOUBLE.fieldOf("total").forGetter(Snapshot::total))
                .apply(i, Snapshot::new));
    }

    public static final Codec<PollutionState> CODEC = Snapshot.CODEC.xmap(PollutionState::new, PollutionState::snapshot);

    public static final SavedDataType<PollutionState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, "pollution"), PollutionState::new, CODEC);

    private final Map<Long, Double> clouds = new HashMap<>();
    private final Map<Long, BlockPos> sources = new HashMap<>();
    private double total;

    public PollutionState() {}

    private PollutionState(Snapshot snapshot) {
        for (Entry entry : snapshot.chunks()) {
            long key = ChunkPos.pack(entry.x(), entry.z());
            clouds.put(key, entry.amount());
            entry.source().ifPresent(source -> sources.put(key, source));
        }
        total = snapshot.total();
    }

    private Snapshot snapshot() {
        List<Entry> entries = new ArrayList<>();
        clouds.forEach((key, amount) -> entries.add(new Entry(ChunkPos.getX(key), ChunkPos.getZ(key), amount,
                Optional.ofNullable(sources.get(key)))));
        return new Snapshot(entries, total);
    }

    /** The clouds over this level. */
    public static PollutionState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** How much is over this chunk. */
    public double at(ChunkPos chunk) {
        return clouds.getOrDefault(chunk.pack(), 0.0);
    }

    /** Where the machine that last breathed into this chunk stands, or null for a cloud that only drifted here. */
    public @Nullable BlockPos source(ChunkPos chunk) {
        return sources.get(chunk.pack());
    }

    /** Adds to the cloud over this chunk, and to what the level has ever made. */
    public void add(ChunkPos chunk, double amount) {
        if (amount <= 0) {
            return;
        }
        clouds.merge(chunk.pack(), amount, Double::sum);
        total += amount;
        setDirty();
    }

    /** The same, from a machine standing here: the chunk remembers it as where its cloud comes from. */
    public void add(ChunkPos chunk, double amount, BlockPos source) {
        if (amount <= 0) {
            return;
        }
        add(chunk, amount);
        sources.put(chunk.pack(), source.immutable());
    }

    /** Sets the cloud over this chunk, without counting it as made. For the command and the tests. */
    public void set(ChunkPos chunk, double amount) {
        if (amount <= 0) {
            clouds.remove(chunk.pack());
            sources.remove(chunk.pack());
        } else {
            clouds.put(chunk.pack(), amount);
        }
        setDirty();
    }

    /** Everything the level has ever emitted. */
    public double total() {
        return total;
    }

    /** Every chunk with a cloud over it, by {@link ChunkPos#pack()}. */
    public Map<Long, Double> clouds() {
        return Collections.unmodifiableMap(clouds);
    }

    /**
     * The polluter the cloud over this chunk came from: this chunk's own source, or the source of
     * the thickest neighbouring cloud, followed uphill for up to {@code steps} chunks - which is
     * how a group spawned at a cloud's thin edge finds the factory that made it. Null when no
     * machine is remembered along the way.
     */
    public @Nullable BlockPos polluterOf(ChunkPos chunk, int steps) {
        ChunkPos at = chunk;
        for (int step = 0; step <= steps; step++) {
            BlockPos source = source(at);
            if (source != null) {
                return source;
            }
            ChunkPos thickest = at;
            double most = at(at);
            for (ChunkPos next : List.of(new ChunkPos(at.x() + 1, at.z()), new ChunkPos(at.x() - 1, at.z()),
                    new ChunkPos(at.x(), at.z() + 1), new ChunkPos(at.x(), at.z() - 1))) {
                if (at(next) > most) {
                    most = at(next);
                    thickest = next;
                }
            }
            if (thickest.equals(at)) {
                return null;
            }
            at = thickest;
        }
        return null;
    }

    /** The remembered polluter nearest to here within so many chunks, or null. */
    public @Nullable BlockPos nearestSource(BlockPos from, int chunkRadius) {
        ChunkPos middle = ChunkPos.containing(from);
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                BlockPos source = sources.get(ChunkPos.pack(middle.x() + x, middle.z() + z));
                if (source != null && source.distSqr(from) < best) {
                    best = source.distSqr(from);
                    nearest = source;
                }
            }
        }
        return nearest;
    }

    /** One minute of weather with the flat absorption: what a test with no world uses. */
    public void drift() {
        drift(chunk -> ABSORB_PER_MINUTE);
    }

    /**
     * One minute of weather: every cloud gives {@link #SPREAD} of itself to each neighbour, then
     * the ground takes what {@code absorption} says from every chunk. A chunk left with nothing
     * drops out of the map, so the map is the cloud and not the history of one.
     */
    public void drift(ToDoubleFunction<ChunkPos> absorption) {
        Map<Long, Double> next = new HashMap<>();
        for (Map.Entry<Long, Double> entry : clouds.entrySet()) {
            long key = entry.getKey();
            double amount = entry.getValue();
            double given = amount * SPREAD;
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            next.merge(key, amount - 4 * given, Double::sum);
            next.merge(ChunkPos.pack(x + 1, z), given, Double::sum);
            next.merge(ChunkPos.pack(x - 1, z), given, Double::sum);
            next.merge(ChunkPos.pack(x, z + 1), given, Double::sum);
            next.merge(ChunkPos.pack(x, z - 1), given, Double::sum);
        }
        clouds.clear();
        next.forEach((key, amount) -> {
            double left = amount - absorption.applyAsDouble(ChunkPos.unpack(key));
            if (left > 0) {
                clouds.put(key, left);
            }
        });
        // A source is only worth remembering while a cloud hangs over it.
        sources.keySet().retainAll(clouds.keySet());
        setDirty();
    }
}
