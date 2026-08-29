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
 * The client half: one screen, for the burner inserter, and the renderer that draws the items on
 * a belt.
 *
 * <p>The belt is the interesting one. What is on a belt is not in any block model - it is a
 * position along a line - so it needs a renderer, and the positions come from the client's own
 * copy of the belt run rather than from anything the server sends. See {@code BeltRun}.
 *
 * <p>The chests need no screen here - they are vanilla {@code Container}s on vanilla's own four-
 * and six-row screens, which is the one place in this pack where being a Container buys more than
 * a capability handler would. They do need a renderer, because a chest is drawn by one rather than
 * by a block model, and vanilla's picks its texture from a fixed list that we are not on.
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
