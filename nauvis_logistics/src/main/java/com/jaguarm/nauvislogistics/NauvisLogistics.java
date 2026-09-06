package com.jaguarm.nauvislogistics;

import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvislogistics.registry.ModBlockEntities;
import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.registry.ModItems;
import com.jaguarm.nauvislogistics.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Factorio's logistics: inserters first, then belts, splitters, chests and robots.
 *
 * <p>The inserter is the cheapest thing in the pack that changes what the game is. One block,
 * and every container in Minecraft becomes something a factory can feed. Belts are the harder
 * half of this mod and the one architectural decision that is expensive to reverse - see the
 * belt note in PLAN.md before starting them.
 *
 * <p>Ids come from {@code data/mapping.json} and are permanent.
 */
@Mod(NauvisLogistics.MODID)
public class NauvisLogistics {

    public static final String MODID = "nauvis_logistics";

    public NauvisLogistics(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisLogisticsGameTests.register(modEventBus);
        NauvisLogisticsBeltGameTests.register(modEventBus);
    }
}
