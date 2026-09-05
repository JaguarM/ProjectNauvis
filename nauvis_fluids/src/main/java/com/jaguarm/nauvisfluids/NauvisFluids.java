package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvisfluids.compat.facrafting.FacraftingProgress;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFeatures;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import com.jaguarm.nauvisfluids.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/**
 * Pipes, oil, chemistry and nuclear.
 *
 * <p>This mod is here three milestones early for one reason: the boiler and the steam engine are
 * both paid for in pipes, so milestone 1's power needs one item out of a mod whose real work is
 * milestone 4. Registering the pipe with the right id and the right recipe cost nothing and kept
 * non-negotiable #1 honest.
 *
 * <p>The pipe carries steam, and now crude oil. Oil starts here: oil wells in the ground, placed by
 * worldgen in fields and never moved, and the pumpjack that stands over one and draws from it at
 * Factorio's rate. PLAN.md's shortcut for the rest of milestone 4 is barrels as items; the pipe
 * network that steam brought forward means oil can already flow, and what barrels still defer is
 * the refinery and its tanks.
 */
@Mod(NauvisFluids.MODID)
public class NauvisFluids {

    public static final String MODID = "nauvis_fluids";

    public NauvisFluids(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        modEventBus.addListener(ModPacks::addPackFinders);
        NauvisFluidsGameTests.register(modEventBus);

        // Behind a branch, so the class naming Facrafting's types is never loaded without it.
        // See FacraftingProgress.
        if (ModList.get().isLoaded("facrafting")) {
            FacraftingProgress.install();
        }
    }
}
