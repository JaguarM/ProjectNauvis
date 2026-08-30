package com.jaguarm.nauvismining.machine.miner;

import net.minecraft.network.chat.Component;

/**
 * Why the miner is or is not running, surfaced in the GUI.
 *
 * <p>The original mod gave no feedback at all when a machine sat idle, which made an empty
 * fuel slot indistinguishable from a bug. Each constant maps to a translation key.
 */
public enum MinerStatus {
    /**
     * Ordinal 0 deliberately. ContainerData starts zeroed on the client, so whatever sits
     * first is what an unsynced screen shows; that must not be a state that claims the
     * machine is working.
     */
    IDLE("idle"),
    RUNNING("running"),
    NO_FUEL("no_fuel"),
    NO_ENERGY("no_energy"),
    NO_PICKAXE("no_pickaxe"),
    NO_COBBLE("no_cobble"),
    COMPLETE("complete");

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
