package com.jaguarm.nauvisresearch.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * The client's copy of what the world has researched.
 *
 * <p>Two things read it: the research screen, which draws it, and the recipe panel's lock, which
 * decides what to hide. Both are presentation - the server checks everything again - and both
 * have to be right the moment a technology finishes, because the panel is very often open when
 * one does.
 *
 * <p>{@link #revision} is how the panel notices. It moves on every sync, and Facrafting's
 * {@code RecipeLock#revision} reports it, so a strip built before a technology completed is
 * rebuilt on the next frame after it did. There is no event for this on purpose: an event would
 * be Facrafting subscribing to something that knows what research is.
 *
 * <p>Nothing here is client-only by type, so common code may touch it. On a dedicated server it
 * simply stays empty and its revision stays zero.
 */
public final class ClientResearch {

    private ClientResearch() {}

    private static Set<ResourceKey<Technology>> completed = Set.of();
    private static @Nullable ResourceKey<Technology> current;
    private static int revision;

    /** Units paid towards every technology part-way through, as of the last sync. */
    private static java.util.Map<ResourceKey<Technology>, Integer> progress = java.util.Map.of();

    /** The trigger tally, as of the last sync. */
    private static java.util.Map<net.minecraft.resources.Identifier, Integer> made = java.util.Map.of();

    /** The locked set, and the registry and revision it was computed for. */
    private static @Nullable Set<ResourceKey<Recipe<?>>> lockedCache;
    private static @Nullable Registry<Technology> cachedFor;
    private static int cachedRevision = -1;

    public static void accept(List<ResourceKey<Technology>> completedKeys,
            @Nullable ResourceKey<Technology> currentKey,
            java.util.Map<ResourceKey<Technology>, Integer> paid,
            java.util.Map<net.minecraft.resources.Identifier, Integer> tally) {
        completed = new LinkedHashSet<>(completedKeys);
        current = currentKey;
        progress = java.util.Map.copyOf(paid);
        made = java.util.Map.copyOf(tally);
        revision++;
        lockedCache = null;
    }

    /** How many of this item the world has made, for drawing a trigger's progress. */
    public static int made(net.minecraft.resources.Identifier item) {
        return made.getOrDefault(item, 0);
    }

    /** Logging out: a stale tree would gate the next world's recipes against this one's research. */
    public static void clear() {
        accept(List.of(), null, java.util.Map.of(), java.util.Map.of());
    }

    public static Set<ResourceKey<Technology>> completed() {
        return completed;
    }

    public static boolean isCompleted(ResourceKey<Technology> technology) {
        return completed.contains(technology);
    }

    public static @Nullable ResourceKey<Technology> current() {
        return current;
    }

    /** Units done on the current technology, for the heading and the corner readout. */
    public static int units() {
        return current == null ? 0 : units(current);
    }

    /** Units done on any technology, including ones the labs were pointed away from. */
    public static int units(ResourceKey<Technology> technology) {
        return progress.getOrDefault(technology, 0);
    }

    public static int revision() {
        return revision;
    }

    /** Display only. The server decides, and {@code ModNetwork} asks it again on every click. */
    public static boolean isUnlocked(RegistryAccess access, ResourceKey<Recipe<?>> recipe) {
        Registry<Technology> technologies = ModTechnologies.registry(access);
        if (lockedCache == null || cachedFor != technologies || cachedRevision != revision) {
            lockedCache = Unlocks.locked(technologies, completed);
            cachedFor = technologies;
            cachedRevision = revision;
        }
        return !lockedCache.contains(recipe);
    }

    /** True when every prerequisite is done and it is not itself done - what the screen lists. */
    public static boolean isAvailable(RegistryAccess access, ResourceKey<Technology> key) {
        if (completed.contains(key)) {
            return false;
        }
        Technology technology = ModTechnologies.registry(access).get(key)
                .map(net.minecraft.core.Holder.Reference::value).orElse(null);
        if (technology == null) {
            return false;
        }
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            if (!completed.contains(prerequisite)) {
                return false;
            }
        }
        return true;
    }
}
