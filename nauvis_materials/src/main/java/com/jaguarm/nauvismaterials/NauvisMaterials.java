package com.jaguarm.nauvismaterials;

import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvismaterials.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Factorio's intermediate products, as items.
 *
 * <p>Its own mod rather than a corner of the machine mod: the intermediate list grows with
 * every tier - advanced circuits, steel, engine units - and an item's namespace is part of its
 * id, so these live in the mod the mapping gives them and nowhere else. Every one is a plain
 * item with Factorio's stack size; what it costs and how long it takes is a generated recipe.
 *
 * <p>Nauvis Materials, by name, until 2026-09-08: a sibling repo that was never
 * published, folded in here under the pack's own ids the way the drills were.
 */
@Mod(NauvisMaterials.MODID)
public class NauvisMaterials {

    public static final String MODID = "nauvis_materials";

    public NauvisMaterials(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisMaterialsGameTests.register(modEventBus);
    }
}
