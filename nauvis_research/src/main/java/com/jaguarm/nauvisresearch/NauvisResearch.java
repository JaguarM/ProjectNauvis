package com.jaguarm.nauvisresearch;

import com.jaguarm.nauvisresearch.registry.ModBlockEntities;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Factorio's research: the lab, and the science packs it eats.
 *
 * <p>The lab is the machine; <b>what it is researching is not here yet</b>, and that is a
 * deliberate line rather than an oversight. Factorio's lab does not know what it is working on
 * either - it consumes one of each pack a technology asks for and reports a cycle done, and the
 * technology, the tree and the unlocks live somewhere else entirely. Building the machine first
 * means the tree can arrive later without the lab changing.
 *
 * <p>So today a lab turns science packs into a count of completed research cycles, which it will
 * hand to a tech tree when there is one. See {@code LabBlockEntity}.
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
        modEventBus.addListener(ModPacks::addPackFinders);
        NauvisResearchGameTests.register(modEventBus);
    }
}
