package com.jaguarm.nauvisresearch.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
 * <p>{@link #units} is how many units of the current technology are done, and it is a property of
 * the research rather than of any machine: twelve labs work on one technology together, which is
 * what makes a lab farm mean anything. {@code LabBlockEntity} keeps its own count of the units it
 * has contributed, but that is a readout, not the state.
 */
public class ResearchState extends SavedData {

    private static final Codec<ResourceKey<Technology>> KEY_CODEC = ResourceKey.codec(ModTechnologies.REGISTRY);

    private record Snapshot(List<ResourceKey<Technology>> completed,
            Optional<ResourceKey<Technology>> current, int units) {}

    private static final Codec<Snapshot> SNAPSHOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            KEY_CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(Snapshot::completed),
            KEY_CODEC.optionalFieldOf("current").forGetter(Snapshot::current),
            Codec.INT.optionalFieldOf("units", 0).forGetter(Snapshot::units))
            .apply(i, Snapshot::new));

    private static final Codec<ResearchState> CODEC = SNAPSHOT_CODEC.xmap(
            snapshot -> {
                ResearchState state = new ResearchState();
                state.completed.addAll(snapshot.completed());
                state.current = snapshot.current().orElse(null);
                state.units = snapshot.units();
                return state;
            },
            state -> new Snapshot(List.copyOf(state.completed), Optional.ofNullable(state.current), state.units));

    public static final SavedDataType<ResearchState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("nauvis_research", "research"),
            ResearchState::new,
            CODEC);

    /** Insertion-ordered so the file reads as the order things were researched in. */
    private final Set<ResourceKey<Technology>> completed = new LinkedHashSet<>();

    private @Nullable ResourceKey<Technology> current;
    private int units;

    public Set<ResourceKey<Technology>> completed() {
        return java.util.Collections.unmodifiableSet(completed);
    }

    public boolean isCompleted(ResourceKey<Technology> technology) {
        return completed.contains(technology);
    }

    public @Nullable ResourceKey<Technology> current() {
        return current;
    }

    /** Units of the current technology finished so far. Meaningless when nothing is current. */
    public int units() {
        return units;
    }

    /**
     * Points the world's labs at a technology, or at nothing.
     *
     * <p>Progress on the technology being left is <b>lost</b>, which is Factorio's rule too: a
     * research swapped away from starts again. Keeping it would mean a units-per-technology map
     * and a second decision about what happens when the tree reloads under it.
     *
     * @return whether anything changed, so the caller can skip an unnecessary sync.
     */
    public boolean setCurrent(@Nullable ResourceKey<Technology> technology) {
        if (java.util.Objects.equals(current, technology)) {
            return false;
        }
        current = technology;
        units = 0;
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
        units++;
        setDirty();
        if (units < total) {
            return false;
        }
        completed.add(current);
        current = null;
        units = 0;
        return true;
    }

    /** For {@code /nauvisresearch} style tooling and the gametests; not a gameplay path. */
    public void complete(ResourceKey<Technology> technology) {
        if (completed.add(technology)) {
            if (technology.equals(current)) {
                current = null;
                units = 0;
            }
            setDirty();
        }
    }

    public void forget(ResourceKey<Technology> technology) {
        if (completed.remove(technology)) {
            setDirty();
        }
    }
}
