package com.jaguarm.nauvismining.machine;

/**
 * Machine modules, in the Factorio sense: universal parts that trade one resource for
 * another rather than being straight upgrades.
 *
 * <p>Modules fit any tier. What tiers buy you is the number of module slots, so the
 * progression is about how many trade-offs you can stack, not about re-crafting upgrades
 * every tier.
 *
 * <p>Effects are per-module and compound multiplicatively, so three speed modules are
 * 0.8^3 of the base mining time rather than 40%. That keeps stacking useful without a
 * cliff where mining becomes instant.
 */
public enum ModuleType implements net.minecraft.util.StringRepresentable {

    /** Faster, hungrier. */
    SPEED("speed", 0.80f, 1.40f, 0),

    /** Cheaper to run, slower. */
    EFFICIENCY("efficiency", 1.15f, 0.60f, 0),

    /** Wider dig area, no running-cost change. */
    RANGE("range", 1.0f, 1.0f, 1);

    public static final com.mojang.serialization.Codec<ModuleType> CODEC =
            net.minecraft.util.StringRepresentable.fromEnum(ModuleType::values);

    private final String id;
    private final float miningTimeFactor;
    private final float fuelUseFactor;
    private final int bonusRadius;

    ModuleType(String id, float miningTimeFactor, float fuelUseFactor, int bonusRadius) {
        this.id = id;
        this.miningTimeFactor = miningTimeFactor;
        this.fuelUseFactor = fuelUseFactor;
        this.bonusRadius = bonusRadius;
    }

    public String id() {
        return id;
    }

    public float miningTimeFactor() {
        return miningTimeFactor;
    }

    public float fuelUseFactor() {
        return fuelUseFactor;
    }

    public int bonusRadius() {
        return bonusRadius;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
