package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvismilitary.registry.ModEntities;
import com.jaguarm.nauvismilitary.registry.ModMenus;
import com.jaguarm.nauvismilitary.turret.GunTurretScreen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client half: the turret's screen, and the grenade drawn as the item it is. */
@Mod(value = NauvisMilitary.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisMilitary.MODID, value = Dist.CLIENT)
public class NauvisMilitaryClient {

    public NauvisMilitaryClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GUN_TURRET.get(), GunTurretScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GRENADE.get(), ThrownItemRenderer::new);
    }
}
