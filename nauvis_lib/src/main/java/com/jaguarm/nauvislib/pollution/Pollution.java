package com.jaguarm.nauvislib.pollution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What a machine breathes out: Factorio's pollution, as a number a machine hands to whoever is
 * keeping count.
 */
public final class Pollution {

    private Pollution() {}

    /** Ticks in a minute, since Factorio's rates are per minute and a machine emits per tick. */
    public static final double TICKS_PER_MINUTE = 1200.0;

    /** Who keeps count. One per game; installed by whatever owns the clouds. */
    public interface Sink {

        /** Adds this much pollution to the air here. */
        void emit(ServerLevel level, BlockPos pos, double amount);
    }

    private static final Sink NONE = (level, pos, amount) -> {};

    private static Sink sink = NONE;

    /** Installs the counter. The last one installed wins, which a gametest relies on to stand in. */
    public static void install(Sink installed) {
        sink = installed;
    }

    /** The current counter, for a stand-in to put back afterwards. */
    public static Sink sink() {
        return sink;
    }

    /** Whether anything is keeping count, for a test that asserts the wiring. */
    public static boolean installed() {
        return sink != NONE;
    }

    /** Adds pollution here. Nothing happens for nothing, so an idle machine may call this freely. */
    public static void emit(ServerLevel level, BlockPos pos, double amount) {
        if (amount > 0) {
            sink.emit(level, pos, amount);
        }
    }

    /**
     * One tick of a machine's per-minute rate, scaled: a machine running on efficiency modules
     * pollutes less by the same fraction it draws less, which is Factorio's rule.
     */
    public static void emitTick(ServerLevel level, BlockPos pos, double perMinute, double factor) {
        emit(level, pos, perMinute / TICKS_PER_MINUTE * factor);
    }
}
