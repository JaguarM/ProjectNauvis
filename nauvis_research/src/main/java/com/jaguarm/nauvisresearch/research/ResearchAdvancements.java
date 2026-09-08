package com.jaguarm.nauvisresearch.research;

import com.jaguarm.nauvisresearch.NauvisResearch;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/** Gives each player the advancement for what the world has researched. */
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
