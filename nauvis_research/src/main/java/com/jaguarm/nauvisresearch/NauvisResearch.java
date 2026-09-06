package com.jaguarm.nauvisresearch;

import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvisresearch.compat.facrafting.FacraftingLock;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.registry.ModBlockEntities;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/**
 * Factorio's research: the lab, the science packs it eats, and the tree it works through.
 *
 * <p>Three pieces, and the seam between them is Factorio's own. The <b>lab</b> is a machine that
 * knows nothing about technologies: it is told which packs the world's current research wants,
 * consumes one of each, and reports a unit done. The <b>tree</b> is a datapack registry generated
 * from Wube's own prototype data by {@code tools/gen_technologies.py}, so a research cost is as
 * exact as an ingredient list and as impossible to type by hand. What has been researched is a
 * {@code SavedData} on the <b>world</b> and not on any player, because two people in one base with
 * different unlocks is a different game.
 *
 * <p>The gate itself is not here. Facrafting owns the crafting panel and must never learn what a
 * technology is, so it exposes a hook and {@code compat/facrafting} fills it in - see
 * {@link FacraftingLock}, and note that Facrafting stays an <em>optional</em> dependency because
 * of it.
 */
@Mod(NauvisResearch.MODID)
public class NauvisResearch {

    public static final String MODID = "nauvis_research";

    public NauvisResearch(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener((AddPackFindersEvent event) -> BenchRecipePacks.add(event, MODID));
        modEventBus.addListener(ModTechnologies::register);
        NauvisResearchGameTests.register(modEventBus);

        // Only touched when Facrafting is there. The class names Facrafting types, and the JVM
        // resolves that reference the first time this call runs - so with Facrafting absent it
        // is never loaded and this mod still stands alone. Same trick as the Jade plugins.
        if (ModList.get().isLoaded("facrafting")) {
            FacraftingLock.install();
        }
    }
}
