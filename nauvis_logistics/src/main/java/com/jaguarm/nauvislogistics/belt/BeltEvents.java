package com.jaguarm.nauvislogistics.belt;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** The two things the world has to tell the belt graph. */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class BeltEvents {

    private BeltEvents() {}

    /** Post, so an item an inserter put on this tick starts moving on this tick. */
    @SubscribeEvent
    static void tick(LevelTickEvent.Post event) {
        BeltLines.of(event.getLevel()).tick();
    }

    /** The graph is derived, never saved. When the level goes, so does it. */
    @SubscribeEvent
    static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            BeltLines.forget(level);
        }
    }
}
