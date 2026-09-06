package com.jaguarm.nauvislogistics.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.SplitterShape;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.registry.ModItems;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
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
        event.createProvider(BlockTagsData::new);
    }

    /**
     * Which tool mines what: every block this mod registers is pickaxe work.
     *
     * <p>A block that requires the correct tool for its drops and is in no {@code mineable/} tag
     * has no correct tool - it never drops - and any block in no such tag digs at bare-hand speed,
     * a boiler taking fifteen seconds. Every machine here was in that state until this provider
     * existed. With the tag an iron pickaxe takes a machine of hardness three down in three
     * quarters of a second, which is about what Factorio's mining time gives.
     */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisLogistics.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
        }
    }

    /** Display names and the inserter's messages. One place, so a rename leaves nothing stale. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisLogistics.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_logistics", "Project Nauvis: Logistics");
            // Facrafting's crafting-panel tabs. These are Factorio's four crafting-menu
            // categories, generated into every recipe's `group` by tools/gen_recipes.py, and
            // each mod ships the keys for the groups it actually uses - so the strip reads the
            // same whichever subset of the pack is installed.
            add("tab.facrafting.group.logistics", "Logistics");
            addBlock(ModBlocks.BURNER_INSERTER, "Burner inserter");
            addBlock(ModBlocks.INSERTER, "Inserter");
            addBlock(ModBlocks.LONG_HANDED_INSERTER, "Long handed inserter");
            addBlock(ModBlocks.FAST_INSERTER, "Fast inserter");
            addBlock(ModBlocks.STACK_INSERTER, "Stack inserter");
            addBlock(ModBlocks.IRON_CHEST, "Iron chest");
            addBlock(ModBlocks.STEEL_CHEST, "Steel chest");
            addBlock(ModBlocks.TRANSPORT_BELT, "Transport belt");
            addBlock(ModBlocks.FAST_TRANSPORT_BELT, "Fast transport belt");
            addBlock(ModBlocks.SPLITTER, "Splitter");
            addBlock(ModBlocks.FAST_SPLITTER, "Fast splitter");
            // "skips research" is not a caveat, it is the point of the pack and has to be on the
            // label. The technology tree gates crafting through Facrafting's panel, which is the
            // only place a timed craft happens; a vanilla bench recipe goes nowhere near it and
            // there is no hook that would let it. A player who turns this on has turned the tech
            // tree off for everything it re-enables, and the one line they read before doing so
            // is this one.
            add("pack.nauvis_logistics.crafting_table", "Nauvis Logistics: bench recipes (skips research)");

            // The whole interface, until there is a screen. See InserterBlock.
            // The burner inserter's screen. Its old right-click strings went with the code.
            add("screen.nauvis_logistics.burner_inserter.no_fuel", "Out of fuel");
            add("screen.nauvis_logistics.burner_inserter.working", "Moving an item");
            add("screen.nauvis_logistics.burner_inserter.waiting", "Nothing to move");

            // The electric inserter has no slot and so no screen: these wait for the hover display.
            add("nauvis_logistics.inserter.running", "Taking from behind, giving to the %s");
            add("nauvis_logistics.inserter.no_power", "No power. Put a pole within two blocks.");

            // Jade. The plugin key is not optional decoration: Jade's settings screen asserts on a
            // provider with no name, and it does it from ScreenEvent.Init - so a missing key is a
            // crash the moment any screen opens, not a blank line in a menu.
            add("config.jade.plugin_nauvis_logistics", "Project Nauvis: Logistics");
            add("config.jade.plugin_nauvis_logistics.belt", "Transport belt");
            add("jade.nauvis_logistics.belt.length", "Belt line: %s belts");
            add("jade.nauvis_logistics.belt.lanes", "Lanes: %s left, %s right, of %s each");
            add("jade.nauvis_logistics.belt.carrying", "Carrying");
            add("jade.nauvis_logistics.belt.empty", "Empty");
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
            dropSelf(ModBlocks.LONG_HANDED_INSERTER.get());
            dropSelf(ModBlocks.FAST_INSERTER.get());
            dropSelf(ModBlocks.STACK_INSERTER.get());
            dropSelf(ModBlocks.IRON_CHEST.get());
            dropSelf(ModBlocks.STEEL_CHEST.get());
            dropSelf(ModBlocks.TRANSPORT_BELT.get());
            dropSelf(ModBlocks.FAST_TRANSPORT_BELT.get());
            add(ModBlocks.SPLITTER.get(), anchorOnly(ModBlocks.SPLITTER.get(), SplitterShape.SHAPE));
            add(ModBlocks.FAST_SPLITTER.get(),
                    anchorOnly(ModBlocks.FAST_SPLITTER.get(), SplitterShape.SHAPE));
        }

        private LootTable.Builder anchorOnly(Block block, MachineShape shape) {
            return LootTable.lootTable().withPool(applyExplosionCondition(block,
                    LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .add(LootItem.lootTableItem(block).when(
                                    LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                                    .hasProperty(shape.part(), shape.anchor()))))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
