package com.jaguarm.nauvismilitary.weapon;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** A magazine: so many rounds, each doing so much damage. */
public class MagazineItem extends Item {

    /** Factorio's magazines stack to two hundred; a Minecraft stack is sixty-four. */
    public static final int STACK = 64;

    private final int rounds;
    private final float damage;

    public MagazineItem(Properties properties, int rounds, float damage) {
        super(properties.stacksTo(STACK));
        this.rounds = rounds;
        this.damage = damage;
    }

    /** How many rounds a fresh magazine holds. */
    public int rounds() {
        return rounds;
    }

    /** What one round does, before research. */
    public float damage() {
        return damage;
    }

    /** The stack of magazines a player would load from, or null for none. */
    public static @Nullable ItemStack find(Player player) {
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offhand.getItem() instanceof MagazineItem) {
            return offhand;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof MagazineItem) {
                return stack;
            }
        }
        return null;
    }
}
