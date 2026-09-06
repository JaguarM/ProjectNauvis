package com.jaguarm.nauvislib.module;

/**
 * What a module does to the machine it sits in, as fractions of the machine's own numbers.
 *
 * <p>Factorio's three effects and Factorio's arithmetic: the effects of every module in a machine
 * are <b>added</b>, not multiplied, so two speed modules at a fifth each make a machine two fifths
 * faster, and the sum is then applied to the machine's speed, its draw and its output. A speed of
 * {@code +0.2} is a fifth faster; an energy of {@code -0.3} is three tenths cheaper to run; a
 * productivity of {@code 0.04} banks a free craft every twenty-five.
 *
 * <p>Two floors, which are Factorio's too: a machine is never slower than a fifth of itself and
 * never draws less than a fifth of its power, however many modules say otherwise. Productivity
 * never goes below nothing.
 *
 * @param speed        added to the machine's speed, as a fraction of it
 * @param energy       added to the machine's draw, as a fraction of it
 * @param productivity a fraction of a free craft earned per craft
 */
public record ModuleEffect(double speed, double energy, double productivity) {

    public static final ModuleEffect NONE = new ModuleEffect(0, 0, 0);

    /** Factorio's floor on speed and on power: a fifth of the machine's own. */
    public static final double FLOOR = 0.2;

    public ModuleEffect plus(ModuleEffect other) {
        return new ModuleEffect(speed + other.speed, energy + other.energy,
                productivity + other.productivity);
    }

    /** What to multiply the machine's crafting speed by. */
    public double speedFactor() {
        return Math.max(FLOOR, 1 + speed);
    }

    /** What to multiply the machine's draw by. */
    public double energyFactor() {
        return Math.max(FLOOR, 1 + energy);
    }

    /** The fraction of a free craft each craft earns. */
    public double productivityBonus() {
        return Math.max(0, productivity);
    }

    /** A machine's draw under these modules, never less than one. */
    public int scaleEnergy(int perTick) {
        return Math.max(1, (int) Math.round(perTick * energyFactor()));
    }
}
