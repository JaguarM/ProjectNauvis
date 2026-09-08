package com.jaguarm.nauvisfluids.oil;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/** Whoever wants to know that a pumpjack took oil out of a well. */
public final class OilProgress {

    private OilProgress() {}

    /** What a listener is told: where the well is, what it is, and how many cycles were taken. */
    @FunctionalInterface
    public interface Listener {
        void onMined(ServerLevel level, BlockPos well, Identifier resource, int cycles);
    }

    private static final List<Listener> listeners = new ArrayList<>();

    public static synchronized void add(Listener listener) {
        listeners.add(listener);
    }

    /** How many listeners are installed, for a test to assert the wiring exists. */
    public static synchronized int installed() {
        return listeners.size();
    }

    /** Called by a pumpjack that has just completed {@code cycles} on the well at {@code well}. */
    public static void report(ServerLevel level, BlockPos well, Identifier resource, int cycles) {
        List<Listener> current;
        synchronized (OilProgress.class) {
            if (listeners.isEmpty()) {
                return;
            }
            current = List.copyOf(listeners);
        }
        for (Listener listener : current) {
            listener.onMined(level, well, resource, cycles);
        }
    }
}
