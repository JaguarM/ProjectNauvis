package com.jaguarm.nauvisresearch.research;

import com.jaguarm.nauvisresearch.NauvisResearch;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gives each player the advancement for what the world has researched.
 *
 * <h2>A record, not the record</h2>
 *
 * <p>Research is per world and advancements are per player - that difference is the reason the
 * tree is a {@code SavedData} in the first place, and nothing here changes it. These are awarded
 * <em>because</em> the world finished something, never the other way round, and their criterion is
 * {@code minecraft:impossible} so nothing a player does can earn one.
 *
 * <p>What they buy is the part Minecraft already does well: a toast in the corner with the
 * technology's name and the sound that goes with it, and a tab that fills in as a log of what has
 * been researched. Writing either by hand would be worse than what vanilla ships.
 *
 * <p>They are <b>not</b> the technology screen. An advancement has exactly one parent and a third
 * of these technologies have more than one prerequisite, so an advancement tree would silently
 * omit the edges a player opens it to see - which is why every one of these hangs off a single
 * root instead of pretending to be the graph.
 */
public final class ResearchAdvancements {

    private ResearchAdvancements() {}

    /** Matches what {@code tools/gen_technologies.py} writes, including for the root. */
    private static final String CRITERION = "researched";

    private static final Identifier ROOT =
            Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "root");

    /**
     * Brings one player's advancements up to date with the world.
     *
     * <p>Idempotent, and called both when a player joins and whenever research moves, because a
     * player who was offline when something completed still has to be told. Awarding one that is
     * already awarded is a no-op, so "grant everything completed" is the whole algorithm and there
     * is nothing to keep in step.
     */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerAdvancementManager manager = server.getAdvancements();

        award(player, manager, ROOT);
        for (ResourceKey<Technology> key : Research.state(server).completed()) {
            award(player, manager, key.identifier());
        }
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    /**
     * A technology's key and its advancement share a path, which is what makes this a lookup
     * rather than a table. A missing one is skipped in silence on purpose: a datapack may add a
     * technology without an advancement, and that is a technology with no toast rather than a
     * crash while somebody is playing.
     */
    private static void award(ServerPlayer player, ServerAdvancementManager manager, Identifier id) {
        AdvancementHolder holder = manager.get(id);
        if (holder != null) {
            player.getAdvancements().award(holder, CRITERION);
        }
    }
}
