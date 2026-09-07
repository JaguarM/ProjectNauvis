package com.jaguarm.nauvisrocket.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.silo.RocketSiloShape;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.tags.BlockTags;
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
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Generates the resources that follow from the block list: models, language, loot and tags.
 * Recipes are not here; they come from {@code tools/gen_recipes.py} and Factorio's own dump.
 *
 * <p>Run with {@code ./gradlew :nauvis_rocket:runClientData} and {@code :runServerData}.
 */
@EventBusSubscriber(modid = NauvisRocket.MODID)
public final class NauvisRocketData {

    private NauvisRocketData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisRocketModels::new);
        event.createProvider((PackOutput output) -> new Lang(output));
    }

    @SubscribeEvent
    static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider((PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) ->
                new LootTableProvider(output, Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                        lookup));
        event.createProvider(BlockTagsData::new);
    }

    /** Every block this mod registers is pickaxe work. */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisRocket.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
        }
    }

    /** Display names, the silo's lines, and the words on the game's last advancement. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisRocket.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_rocket", "Project Nauvis: Rocket");
            add("tab.facrafting.group.intermediate", "Intermediate products");
            add("tab.facrafting.group.combat", "Combat");
            add("pack.nauvis_rocket.crafting_table", "Nauvis Rocket: bench recipes (skips research)");

            addBlock(ModBlocks.ROCKET_SILO, "Rocket silo");
            addItem(ModItems.ROCKET_PART, "Rocket part");
            addItem(ModItems.SATELLITE, "Satellite");
            addItem(ModItems.SPACE_SCIENCE_PACK, "Space science pack");

            // Six states, and each is the thing to do about it.
            add("status.nauvis_rocket.silo.no_recipe", "No rocket part recipe - this pack has none");
            add("status.nauvis_rocket.silo.no_ingredients", "Rocket %s/%s - waiting for rocket part ingredients");
            add("status.nauvis_rocket.silo.no_power", "No power - run a wire to it");
            add("status.nauvis_rocket.silo.building", "Rocket %s/%s - building rocket parts");
            add("status.nauvis_rocket.silo.ready", "Rocket complete - put a satellite in to launch");
            add("status.nauvis_rocket.silo.launching", "Launching");
            add("status.nauvis_rocket.silo.owed", "%s space science packs waiting for the output to clear");

            add("message.nauvis_rocket.launched", "The rocket has launched");
            add("message.nauvis_rocket.launched.where", "From the silo at %s, %s, %s");
            add("message.nauvis_rocket.launched.chat", "A rocket has launched from the silo at %s, %s, %s.");

            add("advancements.nauvis_rocket.launch.title", "Launch a rocket");
            add("advancements.nauvis_rocket.launch.description",
                    "Build a hundred rocket parts, load a satellite, and send it up. The factory did this.");

            // Jade. The plugin key is not decoration: Jade's settings screen asserts on a plugin
            // with no name, from ScreenEvent.Init, so a missing key crashes the moment any
            // screen opens.
            add("config.jade.plugin_nauvis_rocket", "Project Nauvis: Rocket");
            add("config.jade.plugin_nauvis_rocket.rocket_silo", "Rocket silo");
            add("jade.nauvis_rocket.silo.launches", "%s rockets launched");
        }
    }

    /** One silo, not a hundred and thirty-five. Only the anchor drops; the rest is structure. */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            add(ModBlocks.ROCKET_SILO.get(), anchorOnly(ModBlocks.ROCKET_SILO.get(), RocketSiloShape.SHAPE));
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
