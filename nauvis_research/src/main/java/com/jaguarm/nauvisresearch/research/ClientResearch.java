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
    private static int units;
    private static int revision;

    /** The locked set, and the registry and revision it was computed for. */
    private static @Nullable Set<ResourceKey<Recipe<?>>> lockedCache;
    private static @Nullable Registry<Technology> cachedFor;
    private static int cachedRevision = -1;

    public static void accept(List<ResourceKey<Technology>> completedKeys,
            @Nullable ResourceKey<Technology> currentKey, int unitsDone) {
        completed = new LinkedHashSet<>(completedKeys);
        current = currentKey;
        units = unitsDone;
        revision++;
        lockedCache = null;
    }

    /** Logging out: a stale tree would gate the next world's recipes against this one's research. */
    public static void clear() {
        accept(List.of(), null, 0);
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

    public static int units() {
        return units;
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
