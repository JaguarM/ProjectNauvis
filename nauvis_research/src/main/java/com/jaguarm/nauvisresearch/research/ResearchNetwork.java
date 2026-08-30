package com.jaguarm.nauvisresearch.research;

import com.jaguarm.nauvisresearch.NauvisResearch;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Getting the tree to the clients, and taking one instruction back.
 *
 * <p>Two messages. {@link ResearchSyncPayload} goes out whenever the state moves and when a
 * player joins; {@link StartResearchPayload} comes in when somebody picks a technology on the
 * research screen.
 *
 * <p>The technologies themselves need no message: they are a synced datapack registry, so
 * {@code ModTechnologies} gets them to the client with the rest of the datapack. What is sent
 * here is only what has been <em>done</em> with them.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class ResearchNetwork {

    private ResearchNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC,
                ResearchNetwork::handleSync);
        registrar.playToServer(StartResearchPayload.TYPE, StartResearchPayload.STREAM_CODEC,
                ResearchNetwork::handleStart);
    }

    private static void handleSync(ResearchSyncPayload payload, IPayloadContext context) {
        ClientResearch.accept(payload.completed(), payload.current().orElse(null), payload.progress(),
                payload.made());
    }

    /**
     * A modified client can send any key, so nothing here is taken on trust:
     * {@link Research#setCurrent} refuses anything already researched, anything whose
     * prerequisites are outstanding, and anything whose science packs no mod registers.
     */
    private static void handleStart(StartResearchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Research.setCurrent(player.level().getServer(), payload.technology().orElse(null));
    }

    static void sendToAll(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(ResearchSyncPayload.of(Research.state(server)));
    }

    /**
     * A joining player, and a datapack reload.
     *
     * <p>The reload case is the one worth naming: the technology registry is replaced, so
     * anything computed from it is stale, and a recipe could otherwise stay locked against a
     * technology that no longer exists. {@code getPlayer()} is null for a reload and a player
     * for a join, and both want the same message sent.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Research.invalidate();
        ResearchTriggers.invalidate();
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            sendToAll(event.getPlayerList().getServer());
            ResearchAdvancements.syncAll(event.getPlayerList().getServer());
        } else {
            ResearchAdvancements.sync(player);
            PacketDistributor.sendToPlayer(player,
                    ResearchSyncPayload.of(Research.state(player.level().getServer())));
        }
    }
}
