package com.jaguarm.nauvismining;

import com.jaguarm.nauvismining.machine.miner.MinerScreen;
import com.jaguarm.nauvismining.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = NauvisMining.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisMining.MODID, value = Dist.CLIENT)
public class NauvisMiningClient {

    public NauvisMiningClient(ModContainer container) {
        // Lets NeoForge build a config screen for this mod, reachable from the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MINER.get(), MinerScreen::new);
    }
}
