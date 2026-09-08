package com.jaguarm.nauvislib.module;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The free craft a machine is working towards: Factorio's productivity bar. */
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
