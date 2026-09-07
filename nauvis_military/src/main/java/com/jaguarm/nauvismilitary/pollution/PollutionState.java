package com.jaguarm.nauvismilitary.pollution;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The clouds: how much pollution is over each chunk of a level, and how much the level has ever
 * made.
 *
 * <h2>Factorio's model, at chunk size</h2>
 *
 * <p>Factorio keeps pollution per chunk - a number over each 32-by-32 - and every minute a chunk
 * gives two percent of what it holds to each of its four neighbours and the ground absorbs some.
 * This is the same, on Minecraft's 16-by-16 chunks: {@link #SPREAD} to each neighbour and
 * {@link #ABSORB_PER_MINUTE} lost to the ground, every minute, in {@link #drift}. Factorio's
 * absorption depends on what the tiles are - grass a little, trees a lot - and this one does not;
 * it is a flat figure, which is the shortcut {@code PLAN.md} chose. What it gives is a cloud that
 * grows until the edges thin to nothing, which is the shape a Factorio cloud has.
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

    /** What the ground under a chunk takes back a minute. */
    public static final double ABSORB_PER_MINUTE = 5.0;

    private record Entry(int x, int z, double amount) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Codec.DOUBLE.fieldOf("amount").forGetter(Entry::amount))
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
    private double total;

    public PollutionState() {}

    private PollutionState(Snapshot snapshot) {
        for (Entry entry : snapshot.chunks()) {
            clouds.put(ChunkPos.pack(entry.x(), entry.z()), entry.amount());
        }
        total = snapshot.total();
    }

    private Snapshot snapshot() {
        List<Entry> entries = new ArrayList<>();
        clouds.forEach((key, amount) -> entries.add(new Entry(ChunkPos.getX(key), ChunkPos.getZ(key), amount)));
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

    /** Adds to the cloud over this chunk, and to what the level has ever made. */
    public void add(ChunkPos chunk, double amount) {
        if (amount <= 0) {
            return;
        }
        clouds.merge(chunk.pack(), amount, Double::sum);
        total += amount;
        setDirty();
    }

    /** Sets the cloud over this chunk, without counting it as made. For the command and the tests. */
    public void set(ChunkPos chunk, double amount) {
        if (amount <= 0) {
            clouds.remove(chunk.pack());
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
     * One minute of weather: every cloud gives {@link #SPREAD} of itself to each neighbour, then
     * the ground takes {@link #ABSORB_PER_MINUTE} from every chunk. A chunk left with nothing
     * drops out of the map, so the map is the cloud and not the history of one.
     */
    public void drift() {
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
            double left = amount - ABSORB_PER_MINUTE;
            if (left > 0) {
                clouds.put(key, left);
            }
        });
        setDirty();
    }
}
