package com.jaguarm.nauvisfluids.offshorepump;

/**
 * Why an offshore pump is, or is not, running. Factorio's status line for the machine.
 *
 * <p>Kept on the block entity rather than worked out by the readout, because the readout is drawn
 * on a client and the same words are what a test asserts. The ordinal is saved; <b>append, never
 * reorder</b>.
 */
public enum OffshorePumpStatus {
    /** Has water at the intake, has room, and is filling. */
    PUMPING,
    /** The tank is full and nothing is drawing. Not a fault. */
    OUTPUT_FULL,
    /** Nothing at the intake to draw from. */
    NO_WATER,
    /**
     * Water at the intake that is not the world's: a bucket's, or the flowing edge of a lake.
     * The status a player meets when they try to pump from a puddle, and the one worth a word.
     */
    WRONG_WATER;

    private static final OffshorePumpStatus[] VALUES = values();

    public static OffshorePumpStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NO_WATER;
    }
}
