package com.jaguarm.nauvismachines;

import com.jaguarm.nauvismachines.machine.assembler.AssemblerScreen;
import com.jaguarm.nauvismachines.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The client half: one screen, registered against one menu.
 *
 * <p>Everything else about the interface belongs to Facrafting, whose panel attaches itself to any
 * container screen and, when that screen's menu is a {@code RecipeSelector}, points the machine
 * instead of queueing a craft. That is why there is so little here.
 */
@Mod(value = NauvisMachines.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisMachines.MODID, value = Dist.CLIENT)
public class NauvisMachinesClient {

    public NauvisMachinesClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
    }
}
