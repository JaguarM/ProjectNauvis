package com.jaguarm.nauvisresearch.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvisresearch.multiblock.MachineShape;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
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
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Generates the resources that follow from the block list: models, language and loot.
 *
 * <p>Recipes are not here. They come from {@code tools/gen_recipes.py} and Factorio's own dump,
 * and {@code checkRecipes} fails the build if one on disk disagrees.
 *
 * <p>Run with {@code ./gradlew :nauvis_research:runClientData} and {@code :runServerData}.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class NauvisResearchData {

    private NauvisResearchData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisResearchModels::new);
        event.createProvider((PackOutput output) -> new Lang(output));
    }

    @SubscribeEvent
    static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider((PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) ->
                new LootTableProvider(
                        output,
                        Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new,
                                LootContextParamSets.BLOCK)),
                        lookup));
    }

    /** Display names and the lab's messages. One place, so a rename leaves nothing stale. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisResearch.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_research", "Project Nauvis: Research");
            // Facrafting's crafting-panel tabs. These are Factorio's four crafting-menu
            // categories, generated into every recipe's `group` by tools/gen_recipes.py, and
            // each mod ships the keys for the groups it actually uses - so the strip reads the
            // same whichever subset of the pack is installed.
            add("tab.facrafting.group.intermediate", "Intermediate products");
            add("tab.facrafting.group.production", "Production");
            addBlock(ModBlocks.LAB, "Lab");

            // The id comes from the recipe dump and is permanent; the display name is the one a
            // player will be looking for.
            addItem(ModItems.SCIENCE_PACK_1, "Automation science pack");

                        // "skips research" is not a caveat, it is the point of the pack and has to be on the
            // label. The technology tree gates crafting through Facrafting's panel, which is the
            // only place a timed craft happens; a vanilla bench recipe goes nowhere near it and
            // there is no hook that would let it. A player who turns this on has turned the tech
            // tree off for everything it re-enables, and the one line they read before doing so
            // is this one.
            add("pack.nauvis_research.crafting_table", "Nauvis Research: bench recipes (skips research)");

            // Four states a lab can be in and four different things to do about each, which is
            // the whole reason they are four strings rather than "Idle".
            add("screen.nauvis_research.lab.idle", "Idle - give it science packs");
            add("screen.nauvis_research.lab.no_power", "No power - run a wire to it");
            add("screen.nauvis_research.lab.no_research", "Nothing being researched - pick one");
            add("screen.nauvis_research.lab.researching", "%s - %s units from this lab");
            add("screen.nauvis_research.lab.open_research", "Tech");

            // The research screen. Technology names themselves are not here: the generator ships
            // an English fallback in every technology file, so a name only needs a key when
            // somebody wants to override it - `technology.nauvis_research.<id>`.
            add("screen.nauvis_research.research", "Technology");
            add("screen.nauvis_research.research.current", "Researching %s - %s of %s");
            add("screen.nauvis_research.research.nothing_selected",
                    "Technology - nothing being researched");
            add("screen.nauvis_research.research.none", "Nothing left to research.");
            add("screen.nauvis_research.research.cost", "%s x %ss - %s");
            add("screen.nauvis_research.research.trigger", "Craft %s x %s  -  %s done");
            add("screen.nauvis_research.research.needs", "Needs %s");
            add("screen.nauvis_research.research.unavailable_packs",
                    "Needs a science pack this pack does not have yet");
            add("screen.nauvis_research.research.show_completed", "Completed >");
            add("screen.nauvis_research.research.show_available", "< Available");

            add("message.nauvis_research.research_complete", "Research complete: %s");

            // The key, and the category it lives under in the controls screen.
            add("key.nauvis_research.open_research", "Open technology screen");
            add("key.categories.nauvis_research.nauvis", "Project Nauvis");
        }
    }

    /**
     * One lab, not ten.
     *
     * <p>Breaking any block of a lab takes all ten down with drops enabled, which is what hands
     * the player their lab back whichever block they hit. Without the condition that would be ten
     * labs. Only the anchor drops; the rest are structure.
     */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            add(ModBlocks.LAB.get(), anchorOnly(ModBlocks.LAB.get(), LabShape.SHAPE));
        }

        /**
         * Vanilla's {@code createSinglePropConditionTable}, for a property that is a number.
         *
         * <p>That method wants a {@code StringRepresentable}, which an {@code IntegerProperty} is
         * not, so the body is reproduced here against the {@code Property<Integer>} overload of
         * {@code hasProperty}. Everything else about it is vanilla's, explosion condition and all.
         */
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
