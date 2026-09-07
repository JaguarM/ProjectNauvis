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

/**
 * Factorio's rocket: the silo, the rocket part, the satellite and space science.
 *
 * <p>Milestone 7, and the end of the game. The silo is nine tiles by nine and the biggest thing
 * in the pack; it builds a rocket out of a hundred rocket parts, a satellite in its slot sends
 * the rocket up, and what comes back is a thousand space science packs and the one advancement
 * that says the game is won. The ids are the dump's and are permanent, like every other id here.
 *
 * <p>This mod compiles against Facrafting, as the machines and fluids mods do: the silo runs
 * Facrafting's timed recipes - Factorio's {@code rocket-building} category, which only the silo
 * has - and has to name the type. The arrow points one way and there is no cycle.
 */
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
