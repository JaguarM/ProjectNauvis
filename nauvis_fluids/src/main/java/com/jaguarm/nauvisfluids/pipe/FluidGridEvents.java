package com.jaguarm.nauvisfluids.pipe;

import com.jaguarm.nauvisfluids.NauvisFluids;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The two things the world has to tell the pipe network, and nothing else.
 *
 * <p>There is one ticking subscription in this mod and it is here. It visits runs that have work,
 * not pipes. Compare {@code PowerGridEvents}, which also needs a level-wide block-change hook and
 * a chunk-load hook: a pole reaches further than a neighbour notification carries, and a pipe does
 * not, so a pipe learns about its neighbours from {@code neighborChanged} on its own block.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class FluidGridEvents {

    private FluidGridEvents() {}

    /** Post, so steam a boiler made this tick can be in a pipe this tick. */
    @SubscribeEvent
    static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FluidNetworkManager.of(level).tick();
        }
    }

    /** The graph is derived, never saved. When the level goes, so does it. */
    @SubscribeEvent
    static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FluidNetworkManager.forget(level);
        }
    }
}
