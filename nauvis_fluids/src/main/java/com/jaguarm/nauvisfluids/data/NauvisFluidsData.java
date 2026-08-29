package com.jaguarm.nauvisfluids.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Models, language and loot. Recipes come from tools/gen_recipes.py, never from here. */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class NauvisFluidsData {

    private NauvisFluidsData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisFluidsModels::new);
        event.createProvider((PackOutput output) -> new Lang(output));
    }

    @SubscribeEvent
    static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider((PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) ->
                new LootTableProvider(
                        output,
                        Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                        lookup));
    }

    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisFluids.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_fluids", "Project Nauvis: Fluids");
            // Facrafting's crafting-panel tabs. These are Factorio's four crafting-menu
            // categories, generated into every recipe's `group` by tools/gen_recipes.py, and
            // each mod ships the keys for the groups it actually uses - so the strip reads the
            // same whichever subset of the pack is installed.
            add("tab.facrafting.group.logistics", "Logistics");
            addBlock(ModBlocks.PIPE, "Pipe");

            // Steam is never in the world, but Jade and any tank screen will name it.
            add("fluid.nauvis_fluids.steam", "Steam");

            // Jade's settings screen lists every provider and asserts if one has no name, and
            // that assert fires from ScreenEvent.Init - a missing key here is not a blank line in
            // a config menu, it is a crash the moment any screen opens.
            add("config.jade.plugin_nauvis_fluids", "Project Nauvis: Fluids");
            add("config.jade.plugin_nauvis_fluids.pipe", "Pipe");

            // Factorio's pipe tooltip, as near as is honest. It says "Pipeline extent: 6/320";
            // the 320 is its cap on one fluid segment and this pack has none, so ours stops at
            // the count rather than printing a rule nothing enforces.
            add("jade.nauvis_fluids.pipe.contents", "%s: %s of %s");
            add("jade.nauvis_fluids.pipe.empty", "Empty");
            add("jade.nauvis_fluids.pipe.extent", "Pipeline extent: %s pipes");
            add("jade.nauvis_fluids.pipe.flowing", "Working");
                        // "skips research" is not a caveat, it is the point of the pack and has to be on the
            // label. The technology tree gates crafting through Facrafting's panel, which is the
            // only place a timed craft happens; a vanilla bench recipe goes nowhere near it and
            // there is no hook that would let it. A player who turns this on has turned the tech
            // tree off for everything it re-enables, and the one line they read before doing so
            // is this one.
            add("pack.nauvis_fluids.crafting_table", "Nauvis Fluids: bench recipes (skips research)");
        }
    }

    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.PIPE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
