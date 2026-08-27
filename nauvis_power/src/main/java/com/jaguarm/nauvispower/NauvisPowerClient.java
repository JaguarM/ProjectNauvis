package com.jaguarm.nauvispower;

import com.jaguarm.nauvispower.client.PoleWireRenderer;
import com.jaguarm.nauvispower.generator.BoilerScreen;
import com.jaguarm.nauvispower.registry.ModBlockEntities;
import com.jaguarm.nauvispower.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The client half: the boiler's screen, and the renderer for the wires between poles.
 *
 * <p>The wires are the interesting one. A wire is not in any block - it hangs between two - so no
 * block model can describe it and it needs a renderer of its own.
 */
@Mod(value = NauvisPower.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisPower.MODID, value = Dist.CLIENT)
public class NauvisPowerClient {

    public NauvisPowerClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.SMALL_ELECTRIC_POLE.get(), context -> new PoleWireRenderer());
    }
}
