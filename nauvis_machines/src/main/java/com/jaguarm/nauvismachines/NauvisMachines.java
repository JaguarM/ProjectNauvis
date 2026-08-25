package com.jaguarm.nauvismachines;

import com.jaguarm.nauvismachines.registry.ModBlockEntities;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Factorio's machines: assemblers first, then furnaces, modules, the beacon and the radar.
 *
 * <p>Ids come from {@code data/mapping.json} and are permanent — {@code assembling_machine_1}
 * is what Factorio calls it, this mod is what the mapping says owns it, and both live in every
 * world save from the first placement onwards. What is behind an id may be crude and rewritten;
 * the id may not.
 *
 * <p>This is the one mod in the pack with a compile-time dependency, on Facrafting: an
 * assembler <em>is</em> a machine that runs a timed Facrafting recipe. The arrow points one way
 * and there is no cycle, which is what non-negotiable #3 asks for.
 */
@Mod(NauvisMachines.MODID)
public class NauvisMachines {

    public static final String MODID = "nauvis_machines";

    public NauvisMachines(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        NauvisMachinesGameTests.register(modEventBus);
    }
}
