package com.jaguarm.nauvislib.health;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A machine's health as a field: full when built, saved with the machine, and the one thing a
 * block entity needs to be {@link Damageable}.
 *
 * <p>A block entity holds one of these and forwards the interface to it, the way it holds a
 * {@code MachinePower} for its buffer. The number it is built with is Factorio's for that entity.
 */
public final class MachineHealth implements Damageable {

    private final float max;
    private float health;

    public MachineHealth(float max) {
        this.max = max;
        this.health = max;
    }

    @Override
    public float maxHealth() {
        return max;
    }

    @Override
    public float health() {
        return health;
    }

    @Override
    public void setHealth(float health) {
        this.health = Math.clamp(health, 0, max);
    }

    /** Whether it has taken any damage at all, which is what a repair pack asks. */
    public boolean isDamaged() {
        return health < max;
    }

    public void save(ValueOutput output) {
        output.putFloat("Health", health);
    }

    public void load(ValueInput input) {
        health = Math.clamp(input.getFloatOr("Health", max), 0, max);
    }
}
