package com.jaguarm.nauvislib.item;

import net.minecraft.world.item.Item;

/** Factorio's stack sizes, in full. */
public final class Stacks {

    /** What Minecraft stops a stack at on its own, and the number the mixins look for. */
    public static final int VANILLA_CEILING = Item.ABSOLUTE_MAX_STACK_SIZE;

    /** What the pack stops a stack at: five times Factorio's biggest, which is two thousand. */
    public static final int CEILING = 10_000;

    private Stacks() {}

    /** Item properties with Factorio's stack size. */
    public static Item.Properties of(int factorioStackSize) {
        return new Item.Properties().stacksTo(Math.clamp(factorioStackSize, 1, CEILING));
    }
}
