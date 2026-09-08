package com.jaguarm.nauvislib.bonus;

import net.minecraft.server.level.ServerLevel;

/** A named number the world answers: how much of some effect has been earned. */
public final class Bonuses {

    private Bonuses() {}

    /** Who answers. One per game; installed by whatever owns the world's progress. */
    public interface Source {

        /** The sum of every earned modifier of this type, or zero for one nothing has granted. */
        double bonus(ServerLevel level, String effect);

        /**
         * The same for a modifier that names a target - {@code ammo-damage} for {@code bullet},
         * {@code turret-attack} for {@code gun-turret} - counting only the ones aimed at it. A
         * source that does not distinguish answers as for the type alone.
         */
        default double bonus(ServerLevel level, String effect, String target) {
            return bonus(level, effect);
        }
    }

    private static final Source NONE = (level, effect) -> 0;

    private static Source source = NONE;

    /** Installs the answerer. The last one installed wins, which a gametest relies on to stand in. */
    public static void install(Source installed) {
        source = installed;
    }

    /** The current answerer, for a stand-in to put back afterwards. */
    public static Source source() {
        return source;
    }

    /** Whether anything has installed itself, for a test that asserts the wiring. */
    public static boolean installed() {
        return source != NONE;
    }

    /** How much of this effect the world has earned. Zero when nothing answers. */
    public static double of(ServerLevel level, String effect) {
        return source.bonus(level, effect);
    }

    /** How much of this effect, aimed at this target, the world has earned. */
    public static double of(ServerLevel level, String effect, String target) {
        return source.bonus(level, effect, target);
    }

    /**
     * The same, as a whole number: for a hand size or a slot count, where a fraction of an item
     * is not a thing. Rounded to the nearest whole, so a sum of tenths that should be one is.
     */
    public static int count(ServerLevel level, String effect) {
        return (int) Math.round(of(level, effect));
    }
}
