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

/** Boiler, steam engine, solar, accumulator, poles. */
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
