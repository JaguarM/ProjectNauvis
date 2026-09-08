package com.jaguarm.nauvis;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Factorio's stack sizes on the vanilla items that stand in for Factorio's. */
final class StandInStacks {

    private static final Map<Item, Integer> STACKS = new LinkedHashMap<>();

    static {
        stack(Items.IRON_INGOT, 100);
        stack(Items.COPPER_INGOT, 100);
        stack(Items.RAW_IRON, 50);
        stack(Items.RAW_COPPER, 50);
        stack(Items.COAL, 50);
        stack(Items.COBBLESTONE, 50);
        stack(Items.STONE_BRICKS, 100);
        stack(Items.COBBLESTONE_WALL, 100);
        stack(Items.OAK_PLANKS, 100);
        stack(Items.CHEST, 50);
        stack(Items.IRON_DOOR, 50);
        stack(Items.CONCRETE.pick(DyeColor.GRAY), 100);
        stack(Items.CONCRETE.pick(DyeColor.YELLOW), 100);
        stack(Items.DIRT, 100);
        stack(Items.COD, 100);
        stack(Items.RAIL, 100);
    }

    private StandInStacks() {}

    private static void stack(Item item, int factorioStackSize) {
        STACKS.put(item, factorioStackSize);
    }

    static void modify(ModifyDefaultComponentsEvent event) {
        STACKS.forEach((item, size) -> event.modify(item,
                (components, context, modified) -> components.set(DataComponents.MAX_STACK_SIZE, size)));
    }
}
