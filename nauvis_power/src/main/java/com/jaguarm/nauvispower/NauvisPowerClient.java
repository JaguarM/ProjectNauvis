package com.jaguarm.nauvispower;

import com.jaguarm.nauvispower.client.PoleWireRenderer;
import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The client half: one renderer, for the wires between poles.
 *
 * <p>Nothing else in this mod needs a client at all - a boiler and an engine are a block model and
 * a status line. Wires are the exception because a wire is not in any block: it hangs between two,
 * so no block model can describe it.
 */
@Mod(value = NauvisPower.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisPower.MODID, value = Dist.CLIENT)
public class NauvisPowerClient {

    public NauvisPowerClient() {}

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.SMALL_ELECTRIC_POLE.get(), context -> new PoleWireRenderer());
    }
}
