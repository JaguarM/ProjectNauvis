package com.jaguarm.nauvislogistics.net;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltLines;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The two messages a belt sends, and nothing else. */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class ModNetwork {

    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BeltItemAddedPayload.TYPE, BeltItemAddedPayload.STREAM_CODEC,
                ModNetwork::handleAdded);
        registrar.playToClient(BeltItemRemovedPayload.TYPE, BeltItemRemovedPayload.STREAM_CODEC,
                ModNetwork::handleRemoved);
    }

    // Handlers run on the main thread: PayloadRegistrar defaults to HandlerThread.MAIN.
    private static void handleAdded(BeltItemAddedPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        BeltLines.of(level).putOn(payload.belt(), payload.lane(), payload.offset(), payload.item());
    }

    private static void handleRemoved(BeltItemRemovedPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        BeltLines.of(level).takeOff(payload.belt(), payload.lane(), payload.offset());
    }
}
