package com.jaguarm.nauvisrocket;

import com.jaguarm.nauvisrocket.registry.ModMenus;
import com.jaguarm.nauvisrocket.silo.RocketSiloScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half: the silo's screen, registered against its menu. */
@Mod(value = NauvisRocket.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisRocket.MODID, value = Dist.CLIENT)
public class NauvisRocketClient {

    public NauvisRocketClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROCKET_SILO.get(), RocketSiloScreen::new);
    }
}
