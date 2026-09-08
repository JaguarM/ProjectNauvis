package com.jaguarm.nauvismaterials;

import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvismaterials.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Factorio's intermediate products, as items. */
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
