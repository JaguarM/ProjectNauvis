package com.jaguarm.nauvislogistics;

import com.jaguarm.nauvislogistics.registry.ModMenus;
import com.jaguarm.nauvislogistics.transport.BurnerInserterScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The client half: one screen, for the burner inserter.
 *
 * <p>The iron chest needs nothing here - it is a vanilla {@code Container} on vanilla's own
 * four-row screen, which is the one place in this pack where being a Container buys more than a
 * capability handler would.
 */
@Mod(value = NauvisLogistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisLogistics.MODID, value = Dist.CLIENT)
public class NauvisLogisticsClient {

    public NauvisLogisticsClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BURNER_INSERTER.get(), BurnerInserterScreen::new);
    }
}
