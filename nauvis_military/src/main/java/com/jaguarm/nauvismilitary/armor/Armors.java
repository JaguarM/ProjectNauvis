package com.jaguarm.nauvismilitary.armor;

import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Factorio's two plain armours, as chestplates.
 *
 * <p>Factorio's armour is one item for the whole body; Minecraft's is four, and a chestplate is
 * the one that reads as "armour" on its own. Light armour is forty iron plates and stops a fifth
 * of what hits you, which is chainmail's five points here; heavy armour is fifty steel and a
 * hundred copper and stops a good deal more, which is netherite's eight with its toughness and
 * its knockback resistance. Both are drawn with vanilla's own equipment models - chainmail and
 * netherite - since a texture of a worn armour is a great deal of art for two items.
 */
public final class Armors {

    private Armors() {}

    public static final ArmorMaterial LIGHT = new ArmorMaterial(
            15, Map.of(ArmorType.CHESTPLATE, 5), 9, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F,
            ItemTags.REPAIRS_IRON_ARMOR, EquipmentAssets.CHAINMAIL);

    public static final ArmorMaterial HEAVY = new ArmorMaterial(
            33, Map.of(ArmorType.CHESTPLATE, 8), 10, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F,
            ItemTags.REPAIRS_NETHERITE_ARMOR, EquipmentAssets.NETHERITE);
}
