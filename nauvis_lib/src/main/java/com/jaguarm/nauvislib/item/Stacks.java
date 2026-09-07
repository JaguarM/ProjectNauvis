package com.jaguarm.nauvislib.item;

import net.minecraft.world.item.Item;

/**
 * Factorio's stack sizes, as far as Minecraft allows them.
 *
 * <p>A stack size is a fact a Factorio player carries in their head - plates come in hundreds,
 * circuits in two hundreds, machines in fifties, a satellite alone - and it decides how a chest
 * or a belt reads. So every item the pack registers says Factorio's number, here, and the number
 * is written down beside the item in {@code data/mapping.json} as {@code stack} and held to the
 * code by {@code tools/check_models.py}. What a stack of a vanilla stand-in holds - an iron ingot
 * for a plate - is vanilla's sixty-four, since a vanilla item's size is not the pack's to change.
 *
 * <p>Minecraft caps a stack at {@value Item#ABSOLUTE_MAX_STACK_SIZE}: the count is written as one
 * to ninety-nine by every codec that saves or sends a stack, and no mod lifts that. So a Factorio
 * hundred is ninety-nine here and a two hundred is ninety-nine too; everything under a hundred is
 * exact. The code says Factorio's number and this clamps it, so the day the ceiling moves nothing
 * has to be retyped.
 */
public final class Stacks {

    private Stacks() {}

    /** Item properties with Factorio's stack size, clamped to what Minecraft can hold. */
    public static Item.Properties of(int factorioStackSize) {
        return new Item.Properties().stacksTo(Math.clamp(factorioStackSize, 1, Item.ABSOLUTE_MAX_STACK_SIZE));
    }
}
