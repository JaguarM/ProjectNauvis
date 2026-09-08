package com.jaguarm.nauvislib.health;

/** A machine that has health of its own: Factorio's number, written on the machine. */
public interface Damageable {

    /** Factorio's health for this machine. */
    float maxHealth();

    /** What is left. */
    float health();

    /** Sets what is left, clamped to the maximum. {@link Health} calls this; nothing else should. */
    void setHealth(float health);
}
