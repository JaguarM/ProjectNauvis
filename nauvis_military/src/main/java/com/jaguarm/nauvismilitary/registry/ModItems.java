package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvislib.item.Stacks;
import com.jaguarm.nauvismilitary.armor.Armors;
import com.jaguarm.nauvismilitary.weapon.GrenadeItem;
import com.jaguarm.nauvismilitary.weapon.GunItem;
import com.jaguarm.nauvismilitary.weapon.MagazineItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Factorio's early military, at Factorio's numbers. The ids are the dump's and are permanent;
 * the numbers on each item are the entity's and are not configurable.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisMilitary.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisMilitary.MODID);

    /**
     * Twice Factorio's reach, on both guns: fifteen and eighteen tiles are a Factorio screen, and
     * in Minecraft's first person they are the far side of a small yard. Yannic's call after the
     * first playtest, 2026-09-07 - a range is balance, not identity, and this one is his to move.
     */
    public static final double RANGE_SCALE = 2.0;

    /** Four rounds a second, Factorio's fifteen blocks doubled. The gun you start with. */
    public static final DeferredItem<GunItem> PISTOL = ITEMS.registerItem("pistol",
            properties -> new GunItem(properties, 15, 15.0 * RANGE_SCALE, false),
            () -> Stacks.of(5));

    /** Ten rounds a second for as long as the button is held, Factorio's eighteen doubled. Behind {@code military}. */
    public static final DeferredItem<GunItem> SUBMACHINE_GUN = ITEMS.registerItem("submachine_gun",
            properties -> new GunItem(properties, 6, 18.0 * RANGE_SCALE, true),
            () -> Stacks.of(5));

    /** Ten rounds of five. Four iron plates. */
    public static final DeferredItem<MagazineItem> FIREARM_MAGAZINE = ITEMS.registerItem("firearm_magazine",
            properties -> new MagazineItem(properties, 10, 5.0F),
            () -> Stacks.of(100));

    /** Ten rounds of eight. Two firearm magazines, a steel plate and two copper make two. Behind {@code military-2}. */
    public static final DeferredItem<MagazineItem> PIERCING_ROUNDS_MAGAZINE = ITEMS.registerItem(
            "piercing_rounds_magazine", properties -> new MagazineItem(properties, 10, 8.0F),
            () -> Stacks.of(100));

    /** Ten coal and five iron plates, thrown. Behind {@code military-2}. */
    public static final DeferredItem<GrenadeItem> GRENADE = ITEMS.registerItem("grenade",
            properties -> new GrenadeItem(properties),
            () -> Stacks.of(100));

    /** Forty iron plates, worn on the chest. */
    public static final DeferredItem<Item> LIGHT_ARMOR = ITEMS.registerItem("light_armor",
            properties -> new Item(properties.humanoidArmor(Armors.LIGHT, ArmorType.CHESTPLATE)),
            () -> Stacks.of(1));

    /** A hundred copper and fifty steel. Behind {@code heavy-armor}. */
    public static final DeferredItem<Item> HEAVY_ARMOR = ITEMS.registerItem("heavy_armor",
            properties -> new Item(properties.humanoidArmor(Armors.HEAVY, ArmorType.CHESTPLATE)),
            () -> Stacks.of(1));

    public static final DeferredItem<BlockItem> GUN_TURRET = ITEMS.registerSimpleBlockItem(ModBlocks.GUN_TURRET, () -> Stacks.of(50));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_military"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> PISTOL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PISTOL.get());
                        output.accept(SUBMACHINE_GUN.get());
                        output.accept(FIREARM_MAGAZINE.get());
                        output.accept(PIERCING_ROUNDS_MAGAZINE.get());
                        output.accept(GRENADE.get());
                        output.accept(GUN_TURRET.get());
                        output.accept(LIGHT_ARMOR.get());
                        output.accept(HEAVY_ARMOR.get());
                    })
                    .build());

    private ModItems() {}
}
