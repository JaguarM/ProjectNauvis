package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.NauvisPower;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The four things the world has to tell the grid, and nothing else.
 *
 * <p>There is one ticking subscription in this mod and it is here. It calls
 * {@link PowerNetworkManager#tick()} once per level, and that call visits networks that have work
 * - not poles, and not machines that are already full.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class PowerGridEvents {

    private PowerGridEvents() {}

    /**
     * Post rather than Pre, so a generator that made energy this tick can spend it this tick
     * instead of next. Also fires for client levels, hence the cast.
     */
    @SubscribeEvent
    static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PowerNetworkManager.of(level).tick();
        }
    }

    /**
     * The one hook that says "a block changed" without naming which mod owns it.
     *
     * <p>This is how a machine placed two blocks from a pole is noticed at all: it is in nobody's
     * neighbourhood, it cannot announce itself without {@code nauvis_machines} learning what a
     * pole is, and a pole that went looking on a schedule would be the per-tick scan this design
     * exists to avoid. {@code updateNeighborsAt} fires for a block placed or broken by any means,
     * and the manager's first act is one hash lookup that rejects everywhere near no pole.
     *
     * <p>Nothing is done here beyond remembering the position. The block entity may not be ready
     * to answer a capability query yet, and this fires deep inside a {@code setBlock}.
     */
    @SubscribeEvent
    static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PowerNetworkManager.of(level).blockChanged(event.getPos());
        }
    }

    /**
     * A pole cannot see into an unloaded chunk without dragging it in, so it skips those
     * positions and looks again when they arrive.
     *
     * <p>Only the chunk position is read. The event's own javadoc forbids touching the level from
     * here, on pain of deadlock.
     */
    @SubscribeEvent
    static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PowerNetworkManager.of(level).chunkLoaded(event.getChunk().getPos());
        }
    }

    /** The graph is derived, never saved. When the level goes, so does it. */
    @SubscribeEvent
    static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PowerNetworkManager.forget(level);
        }
    }
}
