package com.jaguarm.nauvisfluids;

import com.jaguarm.facrafting.machine.MachineCategories;
import com.jaguarm.facrafting.progress.MiningListeners;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantBlockEntity;
import com.jaguarm.nauvisfluids.oil.OilProgress;
import com.jaguarm.nauvisfluids.refinery.OilRefineryBlockEntity;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFeatures;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.jaguarm.nauvisfluids.registry.ModMenus;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Pipes, oil, chemistry and nuclear.
 *
 * <p>This mod is here three milestones early for one reason: the boiler and the steam engine are
 * both paid for in pipes, so milestone 1's power needs one item out of a mod whose real work is
 * milestone 4. Registering the pipe with the right id and the right recipe cost nothing and kept
 * non-negotiable #1 honest.
 *
 * <p>The pipe carries steam, crude oil, water, and now the oils. Oil starts here: oil wells in the
 * ground, placed by worldgen in fields and never moved, and the pumpjack that stands over one and
 * draws from it at Factorio's rate. So does water: every lake and sea the world generates is
 * {@code nauvis_fluids:water}, Factorio's water tile - scooped as a water bucket, poured back as
 * vanilla's, never making a new source - and the offshore pump is the one thing that draws from
 * it. Then the oil is processed: the refinery, the chemical plant and the storage tank are here,
 * running Facrafting's fluid recipes, which is why this mod compiles against Facrafting and
 * declares it required - a machine that runs a {@code FacraftRecipe} has to be able to name it.
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
        ModMenus.MENUS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        NauvisFluidsGameTests.register(modEventBus);
        // Oil processing is finished by pumping oil once, and research is another subsystem mod,
        // which this one may not depend on. Facrafting's MiningListeners is the meeting point:
        // the pumpjack reports, research listens, and neither names the other. The well's
        // position is this mod's business; what crosses the seam is the resource and the count.
        OilProgress.add((level, well, resource, cycles) -> MiningListeners.fire(level, resource, cycles));
        // Factorio's "Made in:" under an oil recipe in the crafting panel: the panel shows the
        // recipe dimmed and this is how it knows which machine to name.
        MachineCategories.add(OilRefineryBlockEntity.CATEGORY, () -> ModBlocks.OIL_REFINERY.get().getName());
        MachineCategories.add(ChemicalPlantBlockEntity.CATEGORY, () -> ModBlocks.CHEMICAL_PLANT.get().getName());
    }
}
