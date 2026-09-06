package com.jaguarm.nauvispower;

import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvispower.registry.ModBlockEntities;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;
import com.jaguarm.nauvispower.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Boiler, steam engine, solar, accumulator, poles.
 *
 * <p>Where coal stops being something an inserter eats and starts being something that runs a
 * factory. PLAN.md settled the model: Minecraft FE, buffered per machine, chosen over a
 * first-party grid with a global satisfaction ratio so that third-party cables keep working.
 *
 * <p>Two of the three milestone-1 items are here. The small electric pole is not: a pole is an FE
 * cable with a wide connection radius, and a radius is a graph problem rather than a block, so it
 * gets its own go rather than being rushed alongside two generators.
 */
@Mod(NauvisPower.MODID)
public class NauvisPower {

    public static final String MODID = "nauvis_power";

    public NauvisPower(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisPowerGameTests.register(modEventBus);
    }
}
