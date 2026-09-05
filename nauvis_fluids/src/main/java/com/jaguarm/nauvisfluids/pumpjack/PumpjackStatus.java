package com.jaguarm.nauvisfluids.pumpjack;

/**
 * Why a pumpjack is, or is not, running. Factorio's status line for the machine.
 *
 * <p>Kept on the block entity rather than worked out by the readout, because the readout is drawn
 * on a client that cannot see the well's amount or the tank's room, and because the same four
 * words are what a test asserts. The ordinal is saved; <b>append, never reorder</b>.
 */
public enum PumpjackStatus {
    /** Has a well, has power, has room, and is spending all three. */
    PUMPING,
    /** The tank is full and nothing is drawing. Not a fault. */
    OUTPUT_FULL,
    /** Not enough electricity for this tick. */
    NO_POWER,
    /** Nothing under the middle of the machine to pump. */
    NO_WELL;

    private static final PumpjackStatus[] VALUES = values();

    public static PumpjackStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NO_WELL;
    }
}
