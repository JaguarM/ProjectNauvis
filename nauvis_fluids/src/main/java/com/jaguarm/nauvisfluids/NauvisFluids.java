package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Barrels, then pipes, oil, chemistry and nuclear.
 *
 * <p>Almost none of that exists yet, and this mod is here three milestones early for one reason:
 * the boiler and the steam engine are both paid for in pipes, so milestone 1's power needs one
 * item out of a mod whose real work is milestone 4. Registering the pipe now, with the right id
 * and the right recipe, costs nothing and keeps non-negotiable #1 honest - the alternative was to
 * invent a stand-in ingredient and have to break it later.
 *
 * <p>The pipe carries no fluid. PLAN.md's shortcut for milestone 4 is barrels-as-items and no
 * pipe network at all, so a pipe that moves something is a long way off; this one is a block that
 * exists to be crafted into a boiler.
 */
@Mod(NauvisFluids.MODID)
public class NauvisFluids {

    public static final String MODID = "nauvis_fluids";

    public NauvisFluids(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        modEventBus.addListener(ModPacks::addPackFinders);
    }
}
