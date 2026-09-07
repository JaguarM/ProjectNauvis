package com.jaguarm.nauvisrocket.silo;

/** Why the silo is doing what it is doing. Read by the screen and by the hover readout. */
public enum RocketSiloStatus {
    /** The pack has no rocket part recipe, or no launch recipe, for it to run. */
    NO_RECIPE,
    /** Short of something a rocket part is made of. */
    NO_INGREDIENTS,
    /** The buffer is empty. */
    NO_POWER,
    /** Building rocket parts. */
    BUILDING,
    /** A hundred parts and no satellite: the rocket waits for its cargo. */
    READY,
    /** The countdown is running. */
    LAUNCHING;

    public static RocketSiloStatus of(int ordinal) {
        RocketSiloStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NO_RECIPE;
    }
}
