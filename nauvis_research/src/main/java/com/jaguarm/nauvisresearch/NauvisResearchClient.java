package com.jaguarm.nauvisresearch;

import com.jaguarm.nauvisresearch.lab.LabScreen;
import com.jaguarm.nauvisresearch.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half: the lab's screen, and nothing else yet. */
@Mod(value = NauvisResearch.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisResearch.MODID, value = Dist.CLIENT)
public class NauvisResearchClient {

    public NauvisResearchClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.LAB.get(), LabScreen::new);
    }
}
