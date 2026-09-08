package com.jaguarm.nauvisresearch;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.pack.BenchRecipePacks;
import com.jaguarm.nauvisresearch.compat.facrafting.FacraftingLock;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.registry.ModBlockEntities;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/** Factorio's research: the lab, the science packs it eats, and the tree it works through. */
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

        // The other kind of effect a technology has. A recipe unlock goes through Facrafting's
        // lock below; a modifier - a bigger inserter hand, a faster lab - goes through the
        // library's Bonuses, which any machine mod may ask without naming this one. The library
        // is required, so this needs no ModList check.
        Bonuses.install(new Bonuses.Source() {
            @Override
            public double bonus(ServerLevel level, String effect) {
                return Research.bonus(level.getServer(), effect);
            }

            @Override
            public double bonus(ServerLevel level, String effect, String target) {
                return Research.bonus(level.getServer(), effect, target);
            }
        });

        // Only touched when Facrafting is there. The class names Facrafting types, and the JVM
        // resolves that reference the first time this call runs - so with Facrafting absent it
        // is never loaded and this mod still stands alone. Same trick as the Jade plugins.
        if (ModList.get().isLoaded("facrafting")) {
            FacraftingLock.install();
        }
    }
}
