package com.jaguarm.nauvismilitary.weapon;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A magazine: so many rounds, each doing so much damage.
 *
 * <p>Factorio's numbers. A firearm magazine is ten rounds of five physical damage, a piercing
 * rounds magazine ten of eight. The rounds are the item's durability, so a half-used magazine
 * looks half used and two of them never merge into one full one; the last round breaks it.
 *
 * <p>A gun takes its ammunition from wherever the player keeps it - the off hand first, then the
 * inventory, the way a bow finds arrows - so there is no ammo slot to load.
 */
public class MagazineItem extends Item {

    private final float damage;

    public MagazineItem(Properties properties, int rounds, float damage) {
        super(properties.durability(rounds).setNoCombineRepair());
        this.damage = damage;
    }

    /** What one round does, before research. */
    public float damage() {
        return damage;
    }

    /** The magazine a player would fire from, or null for none. */
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

    /** Spends one round. The last one empties the magazine. Creative players spend nothing. */
    public static void spend(ItemStack magazine, Player player) {
        if (player.isCreative()) {
            return;
        }
        int used = magazine.getDamageValue() + 1;
        if (used >= magazine.getMaxDamage()) {
            magazine.shrink(1);
        } else {
            magazine.setDamageValue(used);
        }
    }
}
