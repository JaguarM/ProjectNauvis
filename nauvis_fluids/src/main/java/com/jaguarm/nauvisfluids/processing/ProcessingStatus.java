package com.jaguarm.nauvisfluids.processing;

/**
 * Why a refinery or a chemical plant is, or is not, running: Factorio's status line for the
 * machine, one word each.
 *
 * <p>Kept on the block entity rather than worked out by the screen, because the screen is drawn
 * on a client and the same words are what a test asserts. The ordinal travels in a data slot
 * and is saved; <b>append, never reorder</b>.
 */
public enum ProcessingStatus {
    /** Has a recipe, its ingredients, room for the products and power, and is counting down. */
    WORKING,
    /** No recipe chosen, or one that no longer exists. */
    NO_RECIPE,
    /** Short of a fluid or an item the recipe wants. */
    NO_INGREDIENTS,
    /** A product will not fit: an output tank or the output slot is full and nothing is taking from it. */
    OUTPUT_FULL,
    /** The buffer is empty. */
    NO_POWER;

    private static final ProcessingStatus[] VALUES = values();

    public static ProcessingStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NO_RECIPE;
    }
}
