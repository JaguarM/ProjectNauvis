package com.jaguarm.nauvismachines;

import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.facrafting.machine.MachineCategories;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
import com.jaguarm.nauvismachines.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Factorio's machines: the assemblers and the furnaces, then modules, the beacon and the radar.
 *
 * <p>Ids come from {@code data/mapping.json} and are permanent — {@code assembling_machine_1}
 * is what Factorio calls it, this mod is what the mapping says owns it, and both live in every
 * world save from the first placement onwards. What is behind an id may be crude and rewritten;
 * the id may not.
 *
 * <p>This mod compiles against Facrafting, as {@code nauvis_fluids} does for its refinery: an
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
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisMachinesGameTests.register(modEventBus);
        NauvisMachinesFurnaceGameTests.register(modEventBus);

        // Factorio's "Made in:" under a smelting recipe in the crafting panel. The panel shows the
        // recipe dimmed and this is how it knows which machines to name; the three furnaces, in
        // the order Factorio lists them.
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.STONE_FURNACE.get().getName());
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.STEEL_FURNACE.get().getName());
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.ELECTRIC_FURNACE.get().getName());
    }
}
