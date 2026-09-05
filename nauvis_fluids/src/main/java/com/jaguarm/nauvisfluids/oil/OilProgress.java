package com.jaguarm.nauvisfluids.oil;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * Whoever wants to know that a pumpjack took oil out of a well.
 *
 * <p>Factorio's oil processing technology is finished by pumping crude oil once - a
 * {@code mine-entity} trigger - and the research that watches for it lives in another mod this one
 * may not compile against. So the pumpjack says what it did here, and whoever is listening decides
 * what it means. With Facrafting installed, {@code compat/facrafting} forwards every report to its
 * {@code MiningListeners}, which is where research hears it; without Facrafting nobody listens and
 * the report costs a list check.
 *
 * <p>The resource is the well's block id, {@code nauvis_fluids:crude_oil}, and the count is how
 * many cycles - not how many units came out. Factorio counts mining operations, whatever the yield.
 */
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
