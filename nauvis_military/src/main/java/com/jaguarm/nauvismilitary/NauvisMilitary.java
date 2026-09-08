package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.pollution.PollutionClouds;
import com.jaguarm.nauvismilitary.registry.ModBlockEntities;
import com.jaguarm.nauvismilitary.registry.ModBlocks;
import com.jaguarm.nauvismilitary.registry.ModCapabilities;
import com.jaguarm.nauvismilitary.registry.ModComponents;
import com.jaguarm.nauvismilitary.registry.ModEntities;
import com.jaguarm.nauvismilitary.registry.ModItems;
import com.jaguarm.nauvismilitary.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Weapons, armour, the gun turret, the stone wall, and the pollution that makes them necessary. */
@Mod(NauvisMilitary.MODID)
public class NauvisMilitary {

    public static final String MODID = "nauvis_military";

    public NauvisMilitary(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisMilitaryGameTests.register(modEventBus);

        // The one place the machines' pollution goes. The library is required, so no ModList check.
        Pollution.install(PollutionClouds.SINK);
    }
}
