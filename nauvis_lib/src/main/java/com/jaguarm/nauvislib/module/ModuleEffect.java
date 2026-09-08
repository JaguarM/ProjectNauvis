package com.jaguarm.nauvislib.module;

/**
 * What a module does to the machine it sits in, as fractions of the machine's own numbers.
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
