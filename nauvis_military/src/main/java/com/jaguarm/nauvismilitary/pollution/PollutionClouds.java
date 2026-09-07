package com.jaguarm.nauvismilitary.pollution;

import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.NauvisMilitary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Where the machines' pollution goes, and the clock that moves it.
 *
 * <p>The sink is installed into {@code nauvis_lib}'s {@link Pollution} when this mod loads, so a
 * furnace in {@code nauvis_machines} adds to the cloud over its chunk without knowing this mod
 * exists. Once a minute of game time the clouds drift, and whatever is thick enough sends
 * something to the factory - see {@link Attacks}.
 */
@EventBusSubscriber(modid = NauvisMilitary.MODID)
public final class PollutionClouds {

    private PollutionClouds() {}

    /** Ticks in a minute: how often the clouds move. */
    public static final int MINUTE = 1200;

    /** The machines' figures, into the cloud over the machine's chunk. */
    public static final Pollution.Sink SINK =
            (level, pos, amount) -> PollutionState.get(level).add(ChunkPos.containing(pos), amount);

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % MINUTE != 0) {
            return;
        }
        PollutionState state = PollutionState.get(level);
        if (state.clouds().isEmpty()) {
            return;
        }
        state.drift();
        Attacks.sweep(level, state);
    }
}
