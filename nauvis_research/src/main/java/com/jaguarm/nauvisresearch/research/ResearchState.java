package com.jaguarm.nauvisresearch.research;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What the world has researched, what it is researching, and how far in.
 *
 * <h2>Per world, not per player</h2>
 *
 * <p>This is the decision the rest of the design hangs off, and it was made against the cheaper
 * one. The obvious shortcut - a lab grants vanilla advancements and recipes gate on those - is
 * half a day's work and wrong, because <b>advancements are per player and research is not</b>.
 * Two people building one base with different unlocks is a different game from Factorio, where
 * research belongs to the force and everybody on it shares one tree. That is not a thing to find
 * out after building on it, so research is a {@link SavedData} whatever ends up drawn on top of
 * it.
 *
 * <p>One of these exists per world rather than per dimension. {@link Research} always fetches it
 * from the overworld's storage for that reason - a lab in the Nether researches the same tree.
 *
 * <h2>Progress lives here, not in the lab</h2>
 *
 * <p>Units are a property of the research rather than of any machine: twelve labs work on one
 * technology together, which is what makes a lab farm mean anything. {@code LabBlockEntity} keeps
 * its own count of the units it has contributed, but that is a readout, not the state.
 *
 * <h2>Progress is per technology, and it survives a switch</h2>
 *
 * <p>{@link #progress} is keyed on the technology rather than being one counter beside
 * {@link #current}, and that is the whole of it: <b>switching research keeps what was already
 * paid</b>. Factorio does the same - a research swapped away from is waiting where you left it
 * when you come back, and the packs already spent are not spent again. One counter meant a player
 * who looked at something else for a minute threw away an hour of labs, which is a thing you only
 * find out after it has happened to you.
 *
 * <p>The map holds only technologies part-way through - a completed one drops out of it - so it
 * stays a handful of entries rather than growing with the tree.
 */
public class ResearchState extends SavedData {

    private static final Codec<ResourceKey<Technology>> KEY_CODEC = ResourceKey.codec(ModTechnologies.REGISTRY);

    private record Snapshot(List<ResourceKey<Technology>> completed,
            Optional<ResourceKey<Technology>> current, int units,
            Map<ResourceKey<Technology>, Integer> progress, Map<Identifier, Integer> made) {}

    private static final Codec<Snapshot> SNAPSHOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            KEY_CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(Snapshot::completed),
            KEY_CODEC.optionalFieldOf("current").forGetter(Snapshot::current),
            // The one counter this used to be. Still written, so a world saved here opens in an
            // older build with its current research where it left it, and still read, so a world
            // saved by one arrives here the same way.
            Codec.INT.optionalFieldOf("units", 0).forGetter(Snapshot::units),
            Codec.unboundedMap(KEY_CODEC, Codec.INT).optionalFieldOf("progress", Map.of())
                    .forGetter(Snapshot::progress),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("made", Map.of())
                    .forGetter(Snapshot::made))
            .apply(i, Snapshot::new));

    private static final Codec<ResearchState> CODEC = SNAPSHOT_CODEC.xmap(
            snapshot -> {
                ResearchState state = new ResearchState();
                state.completed.addAll(snapshot.completed());
                state.current = snapshot.current().orElse(null);
                state.progress.putAll(snapshot.progress());
                if (snapshot.units() > 0) {
                    snapshot.current().ifPresent(key ->
                            state.progress.putIfAbsent(key, snapshot.units()));
                }
                state.made.putAll(snapshot.made());
                return state;
            },
            state -> new Snapshot(List.copyOf(state.completed), Optional.ofNullable(state.current),
                    state.units(), Map.copyOf(state.progress), Map.copyOf(state.made)));

    public static final SavedDataType<ResearchState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("nauvis_research", "research"),
            ResearchState::new,
            CODEC);

    /** Insertion-ordered so the file reads as the order things were researched in. */
    private final Set<ResourceKey<Technology>> completed = new LinkedHashSet<>();

    private @Nullable ResourceKey<Technology> current;

    /**
     * Units paid towards each technology that is part-way through, the current one included.
     *
     * <p>Insertion-ordered, and small: a technology leaves the map the moment it completes, so
     * this holds the one being worked on plus whatever was set aside half done.
     */
    private final Map<ResourceKey<Technology>, Integer> progress = new LinkedHashMap<>();

    /**
     * How many of each item the world has ever made, for the technologies that finish on a
     * trigger rather than on science.
     *
     * <p>Only items some technology is actually watching for are counted - see
     * {@code ResearchTriggers} - so this map has four entries rather than one per item in the
     * game, and stays that size for the life of a world.
     */
    private final Map<Identifier, Integer> made = new java.util.HashMap<>();

    public Set<ResourceKey<Technology>> completed() {
        return java.util.Collections.unmodifiableSet(completed);
    }

    public boolean isCompleted(ResourceKey<Technology> technology) {
        return completed.contains(technology);
    }

    public @Nullable ResourceKey<Technology> current() {
        return current;
    }

    /** Units of the current technology finished so far. Zero when nothing is current. */
    public int units() {
        return current == null ? 0 : units(current);
    }

    /** Units paid towards any technology, whether or not it is the one being worked on. */
    public int units(ResourceKey<Technology> technology) {
        return progress.getOrDefault(technology, 0);
    }

    /** Everything part-finished, for the sync that lets the tree draw a bar on all of it. */
    public Map<ResourceKey<Technology>, Integer> progress() {
        return java.util.Collections.unmodifiableMap(progress);
    }

    /** Throws away every part-finished research. {@code /research reset} and the gametests. */
    public void clearProgress() {
        if (!progress.isEmpty()) {
            progress.clear();
            setDirty();
        }
    }

    /** How many of this item the world has made, for a trigger to compare against. */
    public int made(Identifier item) {
        return made.getOrDefault(item, 0);
    }

    /** The whole tally, for the sync that lets the research screen draw a trigger's progress. */
    public Map<Identifier, Integer> made() {
        return java.util.Collections.unmodifiableMap(made);
    }

    /** @return the new total. */
    public int recordMade(Identifier item, int count) {
        int total = made(item) + count;
        made.put(item, total);
        setDirty();
        return total;
    }

    /**
     * Points the world's labs at a technology, or at nothing.
     *
     * <p>Progress on the technology being left is <b>kept</b>, in {@link #progress}, and is
     * picked up where it stopped when the labs are pointed back at it - Factorio's rule, and the
     * only one that makes switching research a decision rather than a punishment.
     *
     * @return whether anything changed, so the caller can skip an unnecessary sync.
     */
    public boolean setCurrent(@Nullable ResourceKey<Technology> technology) {
        if (java.util.Objects.equals(current, technology)) {
            return false;
        }
        current = technology;
        setDirty();
        return true;
    }

    /**
     * Records one unit of research, and completes the technology when it is the last one.
     *
     * @param total the current technology's unit count, which the caller has already looked up.
     * @return true when this unit finished the technology.
     */
    public boolean addUnit(int total) {
        if (current == null) {
            return false;
        }
        int done = progress.merge(current, 1, Integer::sum);
        setDirty();
        if (done < total) {
            return false;
        }
        completed.add(current);
        progress.remove(current);
        current = null;
        return true;
    }

    /** For {@code /nauvisresearch} style tooling and the gametests; not a gameplay path. */
    public void complete(ResourceKey<Technology> technology) {
        if (completed.add(technology)) {
            progress.remove(technology);
            if (technology.equals(current)) {
                current = null;
            }
            setDirty();
        }
    }

    /** Un-researches it, and throws away any part-finished work towards it along with it. */
    public void forget(ResourceKey<Technology> technology) {
        boolean removed = completed.remove(technology);
        if (progress.remove(technology) != null || removed) {
            setDirty();
        }
    }
}
