package com.jaguarm.nauvisrocket;

import com.jaguarm.facrafting.machine.MachineCategories;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvisrocket.registry.ModBlockEntities;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.registry.ModMenus;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Factorio's rocket: the silo, the rocket part, the satellite and space science. */
@Mod(NauvisRocket.MODID)
public class NauvisRocket {

    public static final String MODID = "nauvis_rocket";

    public NauvisRocket(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisRocketGameTests.register(modEventBus);

        // "Made in:" under the rocket part and the space science pack in the crafting panel,
        // which shows both dimmed: neither is a thing a hand makes.
        MachineCategories.add(RocketSiloBlockEntity.ROCKET_BUILDING, () -> ModBlocks.ROCKET_SILO.get().getName());
    }
}
