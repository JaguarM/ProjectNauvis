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

            // The id is the dump's pre-1.0 name and is permanent; the display name is Factorio's
            // modern one, which is what a player will be looking for.
            addItem(ModItems.SCIENCE_PACK_1, "Automation science pack");

            add("pack.nauvis_research.crafting_table", "Nauvis Research: bench recipes");

            add("screen.nauvis_research.lab.idle", "Idle - give it science packs");
            add("screen.nauvis_research.lab.no_power", "No power - run a wire to it");
            add("screen.nauvis_research.lab.researching", "Researching - %s done");
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
