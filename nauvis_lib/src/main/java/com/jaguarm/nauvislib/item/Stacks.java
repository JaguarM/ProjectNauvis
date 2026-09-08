package com.jaguarm.nauvislib.item;

import net.minecraft.world.item.Item;

/**
 * Factorio's stack sizes, in full.
 *
 * <p>A stack size is a fact a Factorio player carries in their head - plates come in hundreds,
 * circuits in two hundreds, machines in fifties, a satellite alone - and it decides how a chest
 * or a belt reads. So every item the pack registers says Factorio's number, here, the number is
 * written beside the item in {@code data/mapping.json} as {@code stack} and held to the code by
 * {@code tools/check_models.py}, and a vanilla stand-in - the iron ingot that is a plate - gets
 * Factorio's number from the pack mod.
 *
 * <p>Minecraft stops a stack at ninety-nine: the count is written as one to ninety-nine by every
 * codec that saves or sends a stack, a container answers ninety-nine when asked how much a slot
 * holds, and NeoForge's handlers cap at the same constant. The pack lifts all of that to
 * {@value #CEILING} through the mixins in {@code com.jaguarm.nauvislib.mixin}, which is the one
 * place the pack reaches into the engine - see the package there for what each one touches. The
 * ceiling is ten thousand rather than an integer's worth so that nothing that adds to or
 * multiplies a stack size can overflow, and it is five times Factorio's biggest stack.
 */
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
