package com.jaguarm.nauvismachines.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerShape;
import com.jaguarm.nauvismachines.machine.furnace.ElectricFurnaceShape;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceShape;
import com.jaguarm.nauvismachines.machine.radar.RadarShape;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
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
 * Generates the resources that are mechanical rather than creative.
 *
 * <p>Models, language strings and loot tables all follow from the block list, so writing them
 * by hand only creates chances for the silent failures in {@code docs/API-26.2.md} - a plural
 * {@code loot_tables/} directory that makes a block drop nothing, an item icon missing one of
 * its two required files. Generated, they are right or they fail loudly.
 *
 * <p>Recipes are not here. They come from {@code tools/gen_recipes.py} and Factorio's own dump,
 * and {@code checkRecipes} fails the build if one on disk disagrees.
 *
 * <p>Run with {@code ./gradlew :nauvis_machines:runClientData} and {@code :runServerData}.
 */
@EventBusSubscriber(modid = NauvisMachines.MODID)
public final class NauvisMachinesData {

    private NauvisMachinesData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisMachinesModels::new);
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
            super(output, lookup, NauvisMachines.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
        }
    }

    /** Display names and the machine's messages. One place, so a rename leaves nothing stale. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisMachines.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_machines", "Project Nauvis: Machines");
            // Facrafting's crafting-panel tabs. These are Factorio's four crafting-menu
            // categories, generated into every recipe's `group` by tools/gen_recipes.py, and
            // each mod ships the keys for the groups it actually uses - so the strip reads the
            // same whichever subset of the pack is installed.
            add("tab.facrafting.group.production", "Production");
            addBlock(ModBlocks.ASSEMBLING_MACHINE_1, "Assembling machine 1");
            addBlock(ModBlocks.ASSEMBLING_MACHINE_2, "Assembling machine 2");
            addBlock(ModBlocks.STONE_FURNACE, "Stone furnace");
            addBlock(ModBlocks.STEEL_FURNACE, "Steel furnace");
            addBlock(ModBlocks.ELECTRIC_FURNACE, "Electric furnace");
            addBlock(ModBlocks.RADAR, "Radar");
            addItem(ModItems.SPEED_MODULE, "Speed module");
            addItem(ModItems.EFFECTIVITY_MODULE, "Efficiency module");
            addItem(ModItems.PRODUCTIVITY_MODULE, "Productivity module");
            addItem(ModItems.SPEED_MODULE_2, "Speed module 2");
            addItem(ModItems.SPEED_MODULE_3, "Speed module 3");
            addItem(ModItems.EFFECTIVITY_MODULE_2, "Efficiency module 2");
            addItem(ModItems.EFFECTIVITY_MODULE_3, "Efficiency module 3");
            addItem(ModItems.PRODUCTIVITY_MODULE_2, "Productivity module 2");
            addItem(ModItems.PRODUCTIVITY_MODULE_3, "Productivity module 3");
            // A module's tooltip: one line per effect that is not zero, in Factorio's words.
            add("tooltip.nauvis_machines.module.speed", "Speed %s");
            add("tooltip.nauvis_machines.module.energy", "Energy consumption %s");
            add("tooltip.nauvis_machines.module.productivity", "Productivity %s");

            // The screen. Its recipe list is Facrafting's panel, so there is very little here.
            // "skips research" is not a caveat, it is the point of the pack and has to be on the
            // label. The technology tree gates crafting through Facrafting's panel, which is the
            // only place a timed craft happens; a vanilla bench recipe goes nowhere near it and
            // there is no hook that would let it.
            add("pack.nauvis_machines.crafting_table", "Nauvis Machines: bench recipes (skips research)");

            add("screen.nauvis_machines.assembler.idle", "Idle - pick a recipe on the right");
            add("screen.nauvis_machines.assembler.making", "Making %s");
            add("screen.nauvis_machines.assembler.unknown", "Making something this client has not been told about");
            add("screen.nauvis_machines.assembler.no_power", "No power - run a wire to it");
            add("screen.nauvis_machines.assembler.wants", "Wants %s x %s");

            // The furnace's one line, on its screen and in the hover readout alike: what it is
            // doing, or what it is waiting for. Each is the thing to do about it.
            add("status.nauvis_machines.furnace.idle", "Nothing to smelt - put ore in");
            add("status.nauvis_machines.furnace.smelting", "Smelting %s");
            add("status.nauvis_machines.furnace.unknown", "Smelting");
            add("status.nauvis_machines.furnace.cannot_smelt", "Cannot smelt that - not a furnace recipe, or not researched yet");
            add("status.nauvis_machines.furnace.waiting", "Waiting for more of it - the recipe takes several at once");
            add("status.nauvis_machines.furnace.output_full", "Output full - nothing is taking it away");
            add("status.nauvis_machines.furnace.no_fuel", "Out of fuel");
            add("status.nauvis_machines.furnace.no_power", "No power - run a wire to it");

            // Jade. The plugin key is not optional decoration: Jade's settings screen asserts on
            // a plugin with no name, and it does it from ScreenEvent.Init - so a missing key is a
            // crash the moment any screen opens, not a blank line in a menu. Each provider needs
            // one too.
            add("config.jade.plugin_nauvis_machines", "Project Nauvis: Machines");
            add("config.jade.plugin_nauvis_machines.furnace", "Furnace");
            add("config.jade.plugin_nauvis_machines.radar", "Radar");
            add("jade.nauvis_machines.radar.charting", "Keeping %s x %s chunks loaded");
            add("jade.nauvis_machines.radar.no_power", "No power - run a wire to it");
        }
    }

    /**
     * Every block drops itself, once.
     *
     * <p>The condition is what makes "once" true. An assembler is ten blocks, and breaking any of
     * them takes all ten down through {@code Multiblock}'s teardown - with drops enabled, which is
     * what hands the player their machine back whichever cell they hit. Without a condition that
     * would be ten machines. Only the anchor drops; the other nine are structure.
     *
     * <p>This is {@code SmallElectricPoleBlock}'s arrangement, where only the foot has a drop.
     */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            add(ModBlocks.ASSEMBLING_MACHINE_1.get(), anchorOnly(ModBlocks.ASSEMBLING_MACHINE_1.get(),
                    AssemblerShape.SHAPE));
            add(ModBlocks.ASSEMBLING_MACHINE_2.get(), anchorOnly(ModBlocks.ASSEMBLING_MACHINE_2.get(),
                    AssemblerShape.SHAPE));
            add(ModBlocks.STONE_FURNACE.get(), anchorOnly(ModBlocks.STONE_FURNACE.get(), FurnaceShape.SHAPE));
            add(ModBlocks.STEEL_FURNACE.get(), anchorOnly(ModBlocks.STEEL_FURNACE.get(), FurnaceShape.SHAPE));
            add(ModBlocks.ELECTRIC_FURNACE.get(), anchorOnly(ModBlocks.ELECTRIC_FURNACE.get(),
                    ElectricFurnaceShape.SHAPE));
            add(ModBlocks.RADAR.get(), anchorOnly(ModBlocks.RADAR.get(), RadarShape.SHAPE));
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
