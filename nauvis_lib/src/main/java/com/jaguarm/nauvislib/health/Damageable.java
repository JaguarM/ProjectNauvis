package com.jaguarm.nauvislib.health;

/**
 * A machine that has health of its own: Factorio's number, written on the machine.
 *
 * <p>Every Factorio entity has health and can be destroyed, and the biters are what destroy them.
 * A block entity that implements this says how much it has, and {@link Health} finds it through
 * the machine's anchor whichever cell is hit, hurts it, and takes the machine down when it reaches
 * nothing. A machine that does not implement it is not invulnerable: {@link Health} gives every
 * other block a health from its hardness, kept in the level. Implementing this is for a machine
 * whose Factorio health is worth having exactly, and for a machine that wants to show it.
 *
 * <p>{@link MachineHealth} is the one implementation, held as a field and saved with the machine.
 */
public interface Damageable {

    /** Factorio's health for this machine. */
    float maxHealth();

    /** What is left. */
    float health();

    /** Sets what is left, clamped to the maximum. {@link Health} calls this; nothing else should. */
    void setHealth(float health);
}
