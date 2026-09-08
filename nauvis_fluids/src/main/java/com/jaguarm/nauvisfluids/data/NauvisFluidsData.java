package com.jaguarm.nauvisfluids.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantShape;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;
import com.jaguarm.nauvisfluids.refinery.OilRefineryShape;
import com.jaguarm.nauvisfluids.tank.StorageTankShape;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.jaguarm.nauvisfluids.registry.ModTags;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
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

/** Models, language, loot and tags. Recipes come from tools/gen_recipes.py, never from here. */
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
        event.createProvider(BlockTagsData::new);
        event.createProvider(FluidTagsData::new);
    }

    /**
     * Which tool mines what.
     *
     * <p>The pipe, the pumps and the oil machines all require the correct tool for their drops, and the tool
     * that is correct is decided by this tag and nothing else - a block that requires a tool and
     * is in no {@code mineable/} tag has no correct tool, and never drops. The oil well is not
     * here: it is unbreakable, and has no drops to protect. Nor is water, for the reason water
     * never is.
     */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisFluids.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE)
                    .add(ModBlocks.PIPE.getKey())
                    .add(ModBlocks.OFFSHORE_PUMP.getKey())
                    .add(ModBlocks.PUMPJACK.getKey())
                    .add(ModBlocks.STORAGE_TANK.getKey())
                    .add(ModBlocks.OIL_REFINERY.getKey())
                    .add(ModBlocks.CHEMICAL_PLANT.getKey());
        }
    }

    /** What natural water is, as far as everything else is concerned. */
    private static class FluidTagsData extends FluidTagsProvider {

        FluidTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisFluids.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(FluidTags.WATER).add(ModFluids.WATER.getKey(), ModFluids.FLOWING_WATER.getKey());
            tag(FluidTags.SUPPORTS_LILY_PAD).add(ModFluids.WATER.getKey());
            tag(FluidTags.SUPPORTS_FROGSPAWN).add(ModFluids.WATER.getKey());
            tag(FluidTags.BUBBLE_COLUMN_CAN_OCCUPY).add(ModFluids.WATER.getKey());
            tag(ModTags.OFFSHORE_PUMPABLE).add(ModFluids.WATER.getKey());
        }
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
            add("tab.facrafting.group.production", "Production");
            addBlock(ModBlocks.PIPE, "Pipe");
            addBlock(ModBlocks.CRUDE_OIL, "Crude oil");
            addBlock(ModBlocks.PUMPJACK, "Pumpjack");
            addBlock(ModBlocks.OFFSHORE_PUMP, "Offshore pump");
            addBlock(ModBlocks.STORAGE_TANK, "Storage tank");
            addBlock(ModBlocks.OIL_REFINERY, "Oil refinery");
            addBlock(ModBlocks.CHEMICAL_PLANT, "Chemical plant");
            // "Water", exactly as a bucket's is. The world's water and a poured puddle look the
            // same and are scooped the same; the one thing that tells them apart is an offshore
            // pump, and the pump is what says so. The fluid type names itself by this key too.
            addBlock(ModBlocks.WATER, "Water");

            // Never in the world, but Jade, the crafting panel and any tank screen will name them.
            add("fluid.nauvis_fluids.steam", "Steam");
            add("fluid.nauvis_fluids.crude_oil", "Crude oil");
            add("fluid.nauvis_fluids.heavy_oil", "Heavy oil");
            add("fluid.nauvis_fluids.light_oil", "Light oil");
            add("fluid.nauvis_fluids.petroleum_gas", "Petroleum gas");
            add("fluid.nauvis_fluids.lubricant", "Lubricant");
            add("fluid.nauvis_fluids.sulfuric_acid", "Sulfuric acid");
            addItem(ModItems.EXPLOSIVES, "Explosives");
            addItem(ModItems.EMPTY_BARREL, "Empty barrel");
            addItem(ModItems.WATER_BARREL, "Water barrel");
            addItem(ModItems.CRUDE_OIL_BARREL, "Crude oil barrel");
            addItem(ModItems.HEAVY_OIL_BARREL, "Heavy oil barrel");
            addItem(ModItems.LIGHT_OIL_BARREL, "Light oil barrel");
            addItem(ModItems.LUBRICANT_BARREL, "Lubricant barrel");
            addItem(ModItems.PETROLEUM_GAS_BARREL, "Petroleum gas barrel");
            addItem(ModItems.SULFURIC_ACID_BARREL, "Sulfuric acid barrel");

            // Jade's settings screen lists every provider and asserts if one has no name, and
            // that assert fires from ScreenEvent.Init - a missing key here is not a blank line in
            // a config menu, it is a crash the moment any screen opens.
            add("config.jade.plugin_nauvis_fluids", "Project Nauvis: Fluids");
            add("config.jade.plugin_nauvis_fluids.pipe", "Pipe");
            add("config.jade.plugin_nauvis_fluids.crude_oil", "Oil well");
            add("config.jade.plugin_nauvis_fluids.pumpjack", "Pumpjack");
            add("config.jade.plugin_nauvis_fluids.offshore_pump", "Offshore pump");
            add("config.jade.plugin_nauvis_fluids.storage_tank", "Storage tank");
            add("config.jade.plugin_nauvis_fluids.processing", "Oil refinery and chemical plant");

            // Factorio's pipe tooltip, as near as is honest. It says "Pipeline extent: 6/320";
            // the 320 is its cap on one fluid segment and this pack has none, so ours stops at
            // the count rather than printing a rule nothing enforces.
            add("jade.nauvis_fluids.pipe.contents", "%s: %s of %s");
            add("jade.nauvis_fluids.pipe.empty", "Empty");
            add("jade.nauvis_fluids.pipe.extent", "Pipeline extent: %s pipes");
            add("jade.nauvis_fluids.pipe.flowing", "Working");

            // Factorio's oil well tooltip is one line, and this is it.
            add("jade.nauvis_fluids.crude_oil.yield", "Yield: %s%%");
            add("jade.nauvis_fluids.crude_oil.floor", "At its floor - pumps at this rate for ever");

            // /oil, the map editor's tool for a world that generated none. See OilCommand.
            add("commands.nauvis_fluids.oil.field", "Placed an oil field of %s wells around %s, %s");
            add("commands.nauvis_fluids.oil.field.none",
                    "No well would fit here - a field wants solid ground, not water");
            add("commands.nauvis_fluids.oil.well", "Placed an oil well at %s %s %s, yield %s%%");
            add("commands.nauvis_fluids.oil.well.none", "No ground here to put a well in");

            // Factorio's pumpjack window: contents, yield, and the status line.
            add("jade.nauvis_fluids.pumpjack.stored", "Crude oil: %s / %s");
            add("jade.nauvis_fluids.pumpjack.pumping", "Pumping");
            add("jade.nauvis_fluids.pumpjack.full", "Full - nothing is drawing the oil off");
            add("jade.nauvis_fluids.pumpjack.no_power", "No power");
            add("jade.nauvis_fluids.pumpjack.no_well", "Not on an oil well");

            // The offshore pump's: contents and the status line. The last one is the line a
            // player reads when a bucket's puddle turns out not to be a lake.
            add("jade.nauvis_fluids.offshore_pump.stored", "Water: %s / %s");
            add("jade.nauvis_fluids.offshore_pump.pumping", "Pumping");
            add("jade.nauvis_fluids.offshore_pump.full", "Full - nothing is drawing the water off");
            add("jade.nauvis_fluids.offshore_pump.no_water", "No water at the intake");
            add("jade.nauvis_fluids.offshore_pump.wrong_water",
                    "This water cannot be pumped - an offshore pump draws from the still water of a lake or the sea");

            // The action bar, when a pump will not go where it was clicked. Short, because the
            // action bar neither wraps nor scrolls and a long line is cut off at both ends at
            // any GUI scale above the smallest. The second is the one that teaches the rule.
            add(OffshorePumpBlock.NO_WATER_KEY, "No water here to pump");
            add(OffshorePumpBlock.WRONG_WATER_KEY, "Only a lake or the sea can be pumped");
            // The storage tank's readout: Factorio's tank window is the one line.
            add("jade.nauvis_fluids.storage_tank.contents", "%s: %s / %s");
            add("jade.nauvis_fluids.storage_tank.empty", "Empty - holds %s");
            // The refinery's and the chemical plant's: what it makes, the status line, the tanks.
            add("jade.nauvis_fluids.processing.making", "Making %s");
            add("jade.nauvis_fluids.processing.working", "Working");
            add("jade.nauvis_fluids.processing.idle", "No recipe set");
            add("jade.nauvis_fluids.processing.no_ingredients", "Waiting for ingredients");
            add("jade.nauvis_fluids.processing.output_full", "Output full - nothing is drawing it off");
            add("jade.nauvis_fluids.processing.no_power", "No power");
            add("jade.nauvis_fluids.processing.tank", "%s: %s / %s");
            // The same machines' screens. The idle line is the assembler's, because the answer
            // is the same: the recipe is chosen in the panel to the right.
            add("screen.nauvis_fluids.processing.idle", "Idle - pick a recipe on the right");
            add("screen.nauvis_fluids.processing.unknown", "Making something this client has not been told about");
            add("screen.nauvis_fluids.processing.making", "Making %s");
            add("screen.nauvis_fluids.processing.no_power", "No power - run a wire to it");
            add("screen.nauvis_fluids.processing.no_ingredients", "Waiting for ingredients");
            add("screen.nauvis_fluids.processing.output_full", "Output full - nothing is drawing it off");
            add("screen.nauvis_fluids.processing.tank", "%s: %s / %s");
            add("screen.nauvis_fluids.processing.tank_empty", "Empty - no recipe uses this port");

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
            // One machine, not two or ten. The other cells are torn down by the block itself,
            // with drops enabled - that is what hands the player their machine back whichever
            // cell they hit - so only the anchor may carry a drop.
            add(ModBlocks.PUMPJACK.get(), anchorOnly(ModBlocks.PUMPJACK.get(), PumpjackShape.SHAPE));
            add(ModBlocks.OFFSHORE_PUMP.get(), anchorOnly(ModBlocks.OFFSHORE_PUMP.get(), OffshorePumpShape.SHAPE));
            add(ModBlocks.STORAGE_TANK.get(), anchorOnly(ModBlocks.STORAGE_TANK.get(), StorageTankShape.SHAPE));
            add(ModBlocks.OIL_REFINERY.get(), anchorOnly(ModBlocks.OIL_REFINERY.get(), OilRefineryShape.SHAPE));
            add(ModBlocks.CHEMICAL_PLANT.get(), anchorOnly(ModBlocks.CHEMICAL_PLANT.get(), ChemicalPlantShape.SHAPE));
            // The oil well and the water have no table at all: noLootTable() in their properties,
            // so they are skipped here and drop nothing if anything ever manages to break one.
        }

        /**
         * Vanilla's {@code createSinglePropConditionTable}, for a property that is a number.
         * The same method {@code nauvis_power} has for its boiler.
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
