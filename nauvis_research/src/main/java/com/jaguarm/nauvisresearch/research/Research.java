package com.jaguarm.nauvisresearch.research;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;

/**
 * The server's view of research: read the state, move it, tell everybody.
 *
 * <p>Everything that changes the tree goes through here rather than through {@link ResearchState}
 * directly, because every change has a second half - the clients have to be told, or the recipe
 * panel keeps drawing a locked recipe as locked and the research screen keeps drawing a finished
 * technology as available.
 *
 * <p>{@link ResearchState} lives on the <b>overworld's</b> storage and is fetched from there
 * whatever level asks. A lab in the Nether contributes to the same tree; there is one force.
 */
public final class Research {

    private Research() {}

    /** The locked set for {@link #lockedFor}, and the state it was computed from. */
    private static @Nullable Set<ResourceKey<Recipe<?>>> lockedCache;
    private static @Nullable Registry<Technology> cachedFor;
    private static int cachedRevision = -1;

    /**
     * Bumped whenever an answer might have changed, and read by the recipe panel through
     * {@code RecipeLock#revision}. See {@code compat/facrafting}.
     */
    private static int revision;

    public static ResearchState state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResearchState.TYPE);
    }

    public static ResearchState state(ServerLevel level) {
        return state(level.getServer());
    }

    /** The technology being worked on, or null - including when the current key no longer loads. */
    public static @Nullable Holder.Reference<Technology> current(MinecraftServer server) {
        ResourceKey<Technology> key = state(server).current();
        if (key == null) {
            return null;
        }
        return ModTechnologies.registry(server.registryAccess()).get(key).orElse(null);
    }

    /**
     * Points the world's labs at a technology.
     *
     * @return false when the key names nothing, when its prerequisites are not met, or when it is
     *         already researched - all three of which a modified client can ask for.
     */
    public static boolean setCurrent(MinecraftServer server, @Nullable ResourceKey<Technology> technology) {
        ResearchState state = state(server);
        if (technology != null && !isAvailable(server, technology)) {
            return false;
        }
        if (state.setCurrent(technology)) {
            changed(server);
        }
        return true;
    }

    /**
     * Whether a technology can be started: it exists, it is not done, every prerequisite is done,
     * and every science pack it wants is a registered item.
     *
     * <p>The last of those is the one that is easy to forget and is most of the tree today. A
     * technology asking for green science in a pack where no mod registers a green science pack
     * is not "expensive", it is impossible, and a lab pointed at it would sit still for ever with
     * no way to say why.
     */
    public static boolean isAvailable(MinecraftServer server, ResourceKey<Technology> technology) {
        ResearchState state = state(server);
        if (state.isCompleted(technology)) {
            return false;
        }
        Technology value = ModTechnologies.registry(server.registryAccess()).get(technology)
                .map(Holder.Reference::value).orElse(null);
        if (value == null || !value.isResearchable()) {
            return false;
        }
        for (ResourceKey<Technology> prerequisite : value.prerequisites()) {
            if (!state.isCompleted(prerequisite)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One unit of research, reported by a lab that has just consumed a set of packs.
     *
     * @return true when that unit finished the technology.
     */
    public static boolean addUnit(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Holder.Reference<Technology> technology = current(server);
        if (technology == null) {
            return false;
        }

        ResearchState state = state(server);
        ResourceKey<Technology> finished = state.current();
        boolean complete = state.addUnit(technology.value().units());
        changed(server);

        if (complete && finished != null) {
            announce(server, technology.value().title(finished));
        }
        return complete;
    }

    /** Whether this recipe may be crafted. Recipes no technology names are always craftable. */
    public static boolean isUnlocked(MinecraftServer server, ResourceKey<Recipe<?>> recipe) {
        return !lockedFor(server).contains(recipe);
    }

    /**
     * The locked set, rebuilt when the state moves or the tree is reloaded.
     *
     * <p>Keyed on the registry object as well as the revision because a datapack reload replaces
     * the registry without touching the saved state, and a stale index would then gate recipes
     * against technologies that no longer exist.
     */
    private static Set<ResourceKey<Recipe<?>>> lockedFor(MinecraftServer server) {
        Registry<Technology> technologies = ModTechnologies.registry(server.registryAccess());
        if (lockedCache == null || cachedFor != technologies || cachedRevision != revision) {
            lockedCache = Unlocks.locked(technologies, state(server).completed());
            cachedFor = technologies;
            cachedRevision = revision;
        }
        return lockedCache;
    }

    public static int revision() {
        return revision;
    }

    /**
     * Records that something moved: drop the cache, move the revision, tell every client.
     *
     * <p>Sending the whole state to everybody on every unit is deliberate and is cheap: it is
     * three fields, and a unit is at least five seconds of a lab's work. The alternative - a
     * delta, or a progress-only packet on a cadence - buys nothing until research is a hot path,
     * and would make "the client is exactly one message behind" stop being true.
     */
    static void changed(MinecraftServer server) {
        revision++;
        lockedCache = null;
        ResearchNetwork.sendToAll(server);
    }

    /**
     * The {@link #changed} half of a completion, for a gametest that finished a technology by
     * writing the state directly rather than by running a lab for ten minutes.
     */
    public static void changedForTest(MinecraftServer server) {
        changed(server);
    }

    /** Forget everything cached, for a datapack reload or a world unload. */
    public static void invalidate() {
        lockedCache = null;
        cachedFor = null;
        cachedRevision = -1;
        revision++;
    }

    private static void announce(MinecraftServer server, Component title) {
        Component message = Component.translatable("message.nauvis_research.research_complete", title);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
