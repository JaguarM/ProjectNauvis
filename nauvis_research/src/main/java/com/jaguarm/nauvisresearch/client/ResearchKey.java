package com.jaguarm.nauvisresearch.client;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** The key that opens the technology screen. */
@EventBusSubscriber(modid = NauvisResearch.MODID, value = Dist.CLIENT)
public final class ResearchKey {

    private ResearchKey() {}

    /**
     * Its own category rather than {@code MISC}, because the pack will grow more of these and a
     * player looking for "the research key" should find it under the pack's name.
     */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "nauvis"));

    public static final KeyMapping OPEN_RESEARCH = new KeyMapping(
            "key.nauvis_research.open_research", InputConstants.KEY_G, CATEGORY);

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_RESEARCH);
    }

    /**
     * {@code consumeClick} drains one press per call, so this must run exactly once a tick and
     * nowhere else - reading it from a render method would swallow presses at frame rate and lose
     * most of them.
     *
     * <p>Guarded on there being no screen open: without that, the key fires again the moment the
     * research screen has focus and the screen reopens on top of itself.
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        while (OPEN_RESEARCH.consumeClick()) {
            minecraft.gui.setScreen(new ResearchScreen());
        }
    }
}
