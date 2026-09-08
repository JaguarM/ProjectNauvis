package com.jaguarm.nauvismilitary.armor;

import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Factorio's two plain armours, as chestplates. */
public final class Armors {

    private Armors() {}

    public static final ArmorMaterial LIGHT = new ArmorMaterial(
            15, Map.of(ArmorType.CHESTPLATE, 5), 9, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F,
            ItemTags.REPAIRS_IRON_ARMOR, EquipmentAssets.CHAINMAIL);

    public static final ArmorMaterial HEAVY = new ArmorMaterial(
            33, Map.of(ArmorType.CHESTPLATE, 8), 10, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F,
            ItemTags.REPAIRS_NETHERITE_ARMOR, EquipmentAssets.NETHERITE);
}
