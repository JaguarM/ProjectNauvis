package com.jaguarm.nauvismining.machine.miner;

import net.minecraft.network.chat.Component;

/**
 * Why the drill is or is not running, surfaced in the screen and the hover readout.
 *
 * <p>A machine that sits idle without saying why is indistinguishable from a bug. Each constant
 * maps to a translation key.
 */
public enum MinerStatus {
    /**
     * Ordinal 0 deliberately. ContainerData starts zeroed on the client, so whatever sits first
     * is what an unsynced screen shows; that must not be a state that claims the machine is
     * working.
     */
    IDLE("idle"),
    /** Taking ore out of the ground. */
    MINING("mining"),
    /** Walking its columns for the next ore, which it does with a budget rather than all at once. */
    SEARCHING("searching"),
    /** The pickaxe slot is empty. */
    NO_PICKAXE("no_pickaxe"),
    /** A burner with nothing to burn. */
    NO_FUEL("no_fuel"),
    /** An electric drill with an empty buffer. */
    NO_POWER("no_power"),
    /** What it mined has nowhere to go: the output slot is full and nothing in front takes it. */
    OUTPUT_FULL("output_full"),
    /** Factorio's "no minable resources": nothing under it that this pickaxe can mine. */
    NO_ORE("no_ore");

    private final String key;

    MinerStatus(String key) {
        this.key = key;
    }

    public Component label() {
        return Component.translatable("gui.nauvis_mining.miner.status." + key);
    }

    public static MinerStatus byOrdinal(int ordinal) {
        MinerStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
    }
}
