package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.pollution.PollutionClouds;
import com.jaguarm.nauvismilitary.registry.ModBlockEntities;
import com.jaguarm.nauvismilitary.registry.ModBlocks;
import com.jaguarm.nauvismilitary.registry.ModCapabilities;
import com.jaguarm.nauvismilitary.registry.ModEntities;
import com.jaguarm.nauvismilitary.registry.ModItems;
import com.jaguarm.nauvismilitary.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Weapons, armour, the gun turret, the stone wall, and the pollution that makes them necessary.
 *
 * <p>Milestone 5. Factorio's factory is not left alone: what it breathes out drifts across the
 * map and brings the biters, and the pistol, the turret and the wall are what you answer with.
 * Here the biters are Minecraft's own hostiles, drawn to a factory by its pollution rather than
 * spawned in the dark - {@code PLAN.md} has the model - and the machines that pollute are in five
 * other mods, none of which name this one: they hand their figures to {@code nauvis_lib}'s
 * {@link Pollution} and this mod is what listens.
 */
@Mod(NauvisMilitary.MODID)
public class NauvisMilitary {

    public static final String MODID = "nauvis_military";

    public NauvisMilitary(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
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
