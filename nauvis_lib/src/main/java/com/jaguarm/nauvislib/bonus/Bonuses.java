package com.jaguarm.nauvislib.bonus;

import net.minecraft.server.level.ServerLevel;

/**
 * A named number the world answers: how much of some effect has been earned.
 *
 * <p>Factorio's technologies do two kinds of thing. They unlock recipes, which the crafting hook
 * carries, and they <em>modify</em> - an inserter's hand grows, a lab works faster, a bullet hits
 * harder - and a machine that wants to know how much has to ask the world. The machine and the
 * world are in different mods that may not name each other, so the question goes through here:
 * whoever keeps the world's progress installs a {@link Source}, and a machine asks by the effect's
 * name. Nothing installed means nothing earned, which is what every machine did before this
 * existed - a hook's default is always the old behaviour.
 *
 * <p>The names are Factorio's own modifier types - {@code inserter-stack-size-bonus},
 * {@code laboratory-speed} - written where they are used, on the machine that reads them and in
 * the tree that grants them. This class has no opinion about what any of them means: it is a
 * string in and a number out, the same seam as {@code RecipeLocks} for the other kind of effect.
 *
 * <p>Server only. A bonus is a fact about the world's research and lives on the server; a client
 * that wants to draw one reads it off the synced technologies rather than asking here.
 */
public final class Bonuses {

    private Bonuses() {}

    /** Who answers. One per game; installed by whatever owns the world's progress. */
    public interface Source {

        /** The sum of every earned modifier of this type, or zero for one nothing has granted. */
        double bonus(ServerLevel level, String effect);
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

    /**
     * The same, as a whole number: for a hand size or a slot count, where a fraction of an item
     * is not a thing. Rounded to the nearest whole, so a sum of tenths that should be one is.
     */
    public static int count(ServerLevel level, String effect) {
        return (int) Math.round(of(level, effect));
    }
}
