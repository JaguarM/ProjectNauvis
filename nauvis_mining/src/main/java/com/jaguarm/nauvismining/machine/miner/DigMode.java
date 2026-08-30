package com.jaguarm.nauvismining.machine.miner;

import net.minecraft.network.chat.Component;

/**
 * What a drill is willing to break, cycled by a button in its screen.
 *
 * <p>A setting rather than a module: it costs no slot, it is discoverable without reading
 * a wiki, and it is one click to change your mind. Modules are for trade-offs you commit
 * to; this is a mode you flip depending on whether you want ore or want the ground gone.
 *
 * <p>{@link #ORE_ONLY} is first, and therefore the default, deliberately. A machine whose
 * first act is to bury the player in thousands of cobblestone does not get played long
 * enough to be judged on anything else.
 */
public enum DigMode {

    /** Ore only. Terrain is left standing, nothing needs backfilling, no stone is output. */
    ORE_ONLY("ore_only", false, false),

    /** Everything, and the hole is filled back in, consuming one block of fill each time. */
    CLEAR_AND_FILL("clear_and_fill", true, true),

    /** Everything, hole left open. The classic quarry pit. */
    CLEAR("clear", true, false);

    private final String id;
    private final boolean breaksEverything;
    private final boolean fills;

    DigMode(String id, boolean breaksEverything, boolean fills) {
        this.id = id;
        this.breaksEverything = breaksEverything;
        this.fills = fills;
    }

    public String id() {
        return id;
    }

    /** False means ore only. */
    public boolean breaksEverything() {
        return breaksEverything;
    }

    /** Whether holes get filled, which is also what makes the fill slot live. */
    public boolean fills() {
        return fills;
    }

    public DigMode next() {
        DigMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Component label() {
        return Component.translatable("gui.nauvis_mining.miner.mode." + id);
    }

    public Component description() {
        return Component.translatable("gui.nauvis_mining.miner.mode." + id + ".desc");
    }

    public static DigMode byOrdinal(int ordinal) {
        DigMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ORE_ONLY;
    }
}
