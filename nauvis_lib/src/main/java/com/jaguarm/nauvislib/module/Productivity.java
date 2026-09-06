package com.jaguarm.nauvislib.module;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The free craft a machine is working towards: Factorio's productivity bar.
 *
 * <p>Every craft a machine finishes adds its modules' productivity - a twenty-fifth, for one
 * first-tier module - to a bar, and when the bar fills the machine hands over one more craft's
 * worth of product without paying for it. That is the whole of Factorio's productivity, and it
 * is the same on every machine that has it, so it lives here once: {@link #earn} after a craft,
 * {@link #owed} to ask whether a free one is due, {@link #pay} once it has been handed over.
 *
 * <p>A free craft that will not fit stays owed rather than being lost: the bar keeps its value
 * and the machine tries again after its next craft, exactly as Factorio's does when the output is
 * full. Saved with the machine, so a bar nine tenths full survives a reload.
 */
public final class Productivity {

    private double banked;

    /** Adds one craft's worth of bonus. Nothing happens for a machine with no productivity. */
    public void earn(double bonus) {
        if (bonus > 0) {
            banked += bonus;
        }
    }

    /** Whether a free craft is due. */
    public boolean owed() {
        return banked >= 1 - 1e-9;
    }

    /** Records that a free craft was handed over. */
    public void pay() {
        banked = Math.max(0, banked - 1);
    }

    /** How far towards the next free craft, 0 to 1 and over when one is owed. For a bar. */
    public double banked() {
        return banked;
    }

    public void save(ValueOutput output) {
        output.putDouble("Productivity", banked);
    }

    public void load(ValueInput input) {
        banked = input.getDoubleOr("Productivity", 0);
    }
}
