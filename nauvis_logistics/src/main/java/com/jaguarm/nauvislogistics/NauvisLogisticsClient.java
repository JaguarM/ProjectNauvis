package com.jaguarm.nauvislogistics;

import com.jaguarm.nauvislogistics.client.BeltRenderer;
import com.jaguarm.nauvislogistics.client.MetalChestRenderer;
import com.jaguarm.nauvislogistics.client.SplitterRenderer;
import com.jaguarm.nauvislogistics.registry.ModBlockEntities;
import com.jaguarm.nauvislogistics.registry.ModMenus;
import com.jaguarm.nauvislogistics.transport.BurnerInserterScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The client half: one screen, for the burner inserter, and the renderer that draws the items
 * on a belt.
 */
@Mod(value = NauvisLogistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisLogistics.MODID, value = Dist.CLIENT)
public class NauvisLogisticsClient {

    public NauvisLogisticsClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BURNER_INSERTER.get(), BurnerInserterScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPORT_BELT.get(), BeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPLITTER.get(), SplitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.IRON_CHEST.get(), MetalChestRenderer::iron);
        event.registerBlockEntityRenderer(ModBlockEntities.STEEL_CHEST.get(), MetalChestRenderer::steel);
    }
}
