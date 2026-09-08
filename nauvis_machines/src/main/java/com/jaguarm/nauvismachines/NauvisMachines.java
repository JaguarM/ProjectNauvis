package com.jaguarm.nauvismachines;

import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.facrafting.machine.MachineCategories;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;
import com.jaguarm.nauvismachines.machine.radar.RadarChunks;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
import com.jaguarm.nauvismachines.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Factorio's machines: the assemblers and the furnaces, then modules, the beacon and the radar. */
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
        // The radar's chunk tickets. A controller nobody registers has every ticket it holds dropped.
        modEventBus.addListener(RadarChunks::register);
        NauvisMachinesGameTests.register(modEventBus);
        NauvisMachinesFurnaceGameTests.register(modEventBus);

        // Factorio's "Made in:" under a smelting recipe in the crafting panel. The panel shows the
        // recipe dimmed and this is how it knows which machines to name; the three furnaces, in
        // the order Factorio lists them.
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.STONE_FURNACE.get().getName());
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.STEEL_FURNACE.get().getName());
        MachineCategories.add(FurnaceBlockEntity.SMELTING, () -> ModBlocks.ELECTRIC_FURNACE.get().getName());
        // The recipes with a fluid in them - the electric engine unit, the barrels - are the
        // second machine's and not the first's: the panel says so under each of them.
        MachineCategories.add(AssemblerBlock.CRAFTING_WITH_FLUID, () -> ModBlocks.ASSEMBLING_MACHINE_2.get().getName());
    }
}
