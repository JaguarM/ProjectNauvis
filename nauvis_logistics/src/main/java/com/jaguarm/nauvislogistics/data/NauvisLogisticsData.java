package com.jaguarm.nauvislogistics.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.registry.ModItems;

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

/**
 * Generates the resources that are mechanical rather than creative: models, language and loot.
 *
 * <p>Recipes are not here. They come from {@code tools/gen_recipes.py} and Factorio's own dump,
 * and {@code checkRecipes} fails the build if one on disk disagrees.
 */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class NauvisLogisticsData {

    private NauvisLogisticsData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisLogisticsModels::new);
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

    /** Display names and the inserter's messages. One place, so a rename leaves nothing stale. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisLogistics.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_logistics", "Project Nauvis: Logistics");
            addBlock(ModBlocks.BURNER_INSERTER, "Burner inserter");
            addBlock(ModBlocks.INSERTER, "Inserter");
            addBlock(ModBlocks.IRON_CHEST, "Iron chest");
            add("pack.nauvis_logistics.crafting_table", "Nauvis Logistics: Crafting Table Recipes");

            // The whole interface, until there is a screen. See InserterBlock.
            // The burner inserter's screen. Its old right-click strings went with the code.
            add("screen.nauvis_logistics.burner_inserter.no_fuel", "Out of fuel");
            add("screen.nauvis_logistics.burner_inserter.working", "Moving an item");
            add("screen.nauvis_logistics.burner_inserter.waiting", "Nothing to move");

            // The electric inserter has no slot and so no screen: these wait for the hover display.
            add("nauvis_logistics.inserter.running", "Taking from behind, giving to the %s");
            add("nauvis_logistics.inserter.no_power", "No power. Put a pole within two blocks.");
        }
    }

    /** Every block drops itself. Machines are not a source of loot. */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.BURNER_INSERTER.get());
            dropSelf(ModBlocks.INSERTER.get());
            dropSelf(ModBlocks.IRON_CHEST.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
