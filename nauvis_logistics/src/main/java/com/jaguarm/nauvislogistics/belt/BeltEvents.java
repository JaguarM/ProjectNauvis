package com.jaguarm.nauvislogistics.belt;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The two things the world has to tell the belt graph.
 *
 * <p>There is one ticking subscription for belts and it is here. It visits runs that have
 * something on them, not belts.
 *
 * <p><b>It does not filter for a server level, and that is deliberate.</b> Every other manager in
 * this pack does - a fluid run and a power grid are the server's business and a client has no
 * copy. A belt run is different: it is worked out from block states, which a client has, and
 * having the client run the same simulation is what makes items visibly moving on a belt cost
 * nothing to send. See {@link BeltRun}.
 */
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
