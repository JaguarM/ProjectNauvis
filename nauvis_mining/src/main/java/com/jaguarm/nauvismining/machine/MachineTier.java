package com.jaguarm.nauvismining.machine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The two drills, and the numbers Factorio gives them.
 *
 * <p>What separates them is what powers them, how far they reach and how fast they mine, which
 * are Factorio's figures for the burner mining drill and the electric mining drill: a burner
 * takes the two by two it stands on at a quarter of an ore a second, an electric takes a five by
 * five - its own three by three and a ring around it - at half an ore a second, and only the
 * electric one takes modules, three of them. Those numbers are identity, the way the footprint
 * is, so they are written here once and nothing in a config file moves them.
 */
public enum MachineTier implements StringRepresentable {
    /**
     * The drill you start with. Burns solid fuel, covers exactly what it stands on, and has no
     * module slots - Factorio's burner mining drill, 150 kW and a mining speed of 0.25.
     */
    BURNER("burner_mining_drill", false, 0, 0, 0.25, 0),

    /**
     * The drill you graduate to. Runs on electricity, reaches one tile past its own edge on every
     * side, and takes three modules - Factorio's electric mining drill, 90 kW and a mining speed
     * of 0.5. The step up is infrastructure, not just a bigger recipe.
     */
    ELECTRIC("electric_mining_drill", true, 3, 1, 0.5, 12);

    public static final Codec<MachineTier> CODEC = StringRepresentable.fromEnum(MachineTier::values);

    private final String id;
    private final boolean electric;
    private final int moduleSlots;
    private final int reach;
    private final double miningSpeed;
    private final int energyPerTick;

    MachineTier(String id, boolean electric, int moduleSlots, int reach, double miningSpeed,
            int energyPerTick) {
        this.id = id;
        this.electric = electric;
        this.moduleSlots = moduleSlots;
        this.reach = reach;
        this.miningSpeed = miningSpeed;
        this.energyPerTick = energyPerTick;
    }

    public String id() {
        return id;
    }

    /** True if this tier runs on energy instead of burning fuel. */
    public boolean isElectric() {
        return electric;
    }

    /** How many modules this drill takes: none in a burner, three in an electric. */
    public int moduleSlots() {
        return moduleSlots;
    }

    /**
     * How many tiles past its own footprint this drill mines, on every side. Factorio's electric
     * drill covers a five by five from a three by three body; the burner covers only itself.
     */
    public int reach() {
        return reach;
    }

    /** Factorio's mining speed: ores mined per second, on an ore with a mining time of one. */
    public double miningSpeed() {
        return miningSpeed;
    }

    /**
     * FE spent per tick of mining by the electric drill, and nothing for the burner. Ninety
     * kilowatts at the pack's ratio of 120 FE/t to Factorio's 900 kW steam engine.
     */
    public int energyPerTick() {
        return energyPerTick;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
