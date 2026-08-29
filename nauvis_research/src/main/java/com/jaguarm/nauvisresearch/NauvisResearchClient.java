package com.jaguarm.nauvisresearch;

import com.jaguarm.nauvisresearch.lab.LabScreen;
import com.jaguarm.nauvisresearch.registry.ModMenus;
import com.jaguarm.nauvisresearch.research.ClientResearch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half: the lab's screen, and the research state the server keeps it told about. */
@Mod(value = NauvisResearch.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisResearch.MODID, value = Dist.CLIENT)
public class NauvisResearchClient {

    public NauvisResearchClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.LAB.get(), LabScreen::new);
    }

    /**
     * Forget the tree on the way out.
     *
     * <p>Not tidiness: the state is static, so a player leaving one world and joining another
     * would carry the first world's research into the second until the join sync landed - and
     * for those frames the crafting panel would show recipes this world has not unlocked.
     */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientResearch.clear();
    }
}
