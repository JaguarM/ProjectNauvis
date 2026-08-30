package com.jaguarm.nauvismining.machine;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The four progression tiers shared by every machine in the mod.
 *
 * <p>Two drills rather than a tier ladder. Four tiers were doing work the modules already
 * do better: with modules carrying the choices, the middle tiers were only a slower path
 * to the same place. What separates these two is what powers them and how many trade-offs
 * they can run, which is a real decision rather than a number going up.
 */
public enum MachineTier implements StringRepresentable {
    /**
     * The drill you start with. Burns solid fuel and takes no modules at all, so it digs
     * whatever is in front of it and floods you with stone. Crude on purpose: it is the
     * thing you want to replace.
     */
    BURNER("burner_mining_drill", 1, false),

    /**
     * The drill you graduate to. Runs on FE and takes three modules, which is where every
     * choice in the mod lives: speed against fuel cost, or a filter that leaves the
     * terrain alone. The step up is infrastructure, not just a bigger recipe.
     */
    ELECTRIC("electric_mining_drill", 4, true);

    /** Every drill reserves this many module slots; the burner unlocks only the first. */
    public static final int MAX_MODULE_SLOTS = 4;

    public static final Codec<MachineTier> CODEC = StringRepresentable.fromEnum(MachineTier::values);

    private final String id;
    private final int moduleSlots;
    private final boolean electric;

    MachineTier(String id, int moduleSlots, boolean electric) {
        this.id = id;
        this.moduleSlots = moduleSlots;
        this.electric = electric;
    }

    /** True if this tier runs on energy instead of burning fuel. */
    public boolean isElectric() {
        return electric;
    }

    public String id() {
        return id;
    }

    /** How many of the reserved module slots this tier can actually use. */
    public int moduleSlots() {
        return moduleSlots;
    }

    /** True if the module slot at {@code index} is unlocked at this tier. */
    public boolean hasModuleSlot(int index) {
        return index >= 0 && index < moduleSlots;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
