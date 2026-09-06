package com.jaguarm.nauvispower.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.SolarPanelShape;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvispower.grid.BigPoleShape;
import com.jaguarm.nauvispower.grid.MediumPoleShape;
import com.jaguarm.nauvispower.grid.SmallPoleShape;
import com.jaguarm.nauvispower.grid.SubstationShape;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;
import com.jaguarm.nauvispower.storage.AccumulatorShape;

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

/** Models, language and loot. Recipes come from tools/gen_recipes.py, never from here. */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class NauvisPowerData {

    private NauvisPowerData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisPowerModels::new);
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
     * Tags, which so far means one: a pole is climbable.
     *
     * <p>Through the tag rather than by overriding {@code isLadder}, because NeoForge's default
     * implementation of that method <em>is</em> this tag - writing Java here would take the choice
     * away from datapacks rather than adding anything. It is also the tag mobs and the climbing
     * sound already read.
     */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisPower.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            // Every tier. A pole you cannot climb is scenery; a pole you can is somewhere to
            // stand while you wire the next one.
            tag(BlockTags.CLIMBABLE)
                    .add(ModBlocks.SMALL_ELECTRIC_POLE.getKey())
                    .add(ModBlocks.MEDIUM_ELECTRIC_POLE.getKey())
                    .add(ModBlocks.BIG_ELECTRIC_POLE.getKey())
                    .add(ModBlocks.SUBSTATION.getKey());

            // Every block here is pickaxe work. A block that requires the correct tool and is in
            // no mineable/ tag has no correct tool - the boiler and the engine never dropped -
            // and digs at bare-hand speed besides. Factorio mines a machine in a fraction of a
            // second; an iron pickaxe on hardness three is three quarters of one.
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
        }
    }

    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisPower.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_power", "Project Nauvis: Power");
            // Facrafting's crafting-panel tabs. These are Factorio's four crafting-menu
            // categories, generated into every recipe's `group` by tools/gen_recipes.py, and
            // each mod ships the keys for the groups it actually uses - so the strip reads the
            // same whichever subset of the pack is installed.
            add("tab.facrafting.group.logistics", "Logistics");
            add("tab.facrafting.group.production", "Production");
            addBlock(ModBlocks.BOILER, "Boiler");
            addBlock(ModBlocks.STEAM_ENGINE, "Steam engine");
            addBlock(ModBlocks.SMALL_ELECTRIC_POLE, "Small electric pole");
            addBlock(ModBlocks.MEDIUM_ELECTRIC_POLE, "Medium electric pole");
            addBlock(ModBlocks.BIG_ELECTRIC_POLE, "Big electric pole");
            addBlock(ModBlocks.SUBSTATION, "Substation");
            addBlock(ModBlocks.SOLAR_PANEL, "Solar panel");
            addBlock(ModBlocks.ACCUMULATOR, "Accumulator");
                        // "skips research" is not a caveat, it is the point of the pack and has to be on the
            // label. The technology tree gates crafting through Facrafting's panel, which is the
            // only place a timed craft happens; a vanilla bench recipe goes nowhere near it and
            // there is no hook that would let it. A player who turns this on has turned the tech
            // tree off for everything it re-enables, and the one line they read before doing so
            // is this one.
            add("pack.nauvis_power.crafting_table", "Nauvis Power: bench recipes (skips research)");

            // The boiler's screen. Its old right-click strings are gone with the right-click code.
            add("screen.nauvis_power.boiler.burning", "Burning");
            add("screen.nauvis_power.boiler.full", "Full of steam - nothing is drawing it off");
            add("screen.nauvis_power.boiler.no_water", "No water - pipe it in from an offshore pump");
            add("screen.nauvis_power.boiler.idle", "Out of fuel");

            // No screen: the engine has no slot. Its readout is Jade's, below.
            add("nauvis_power.steam_engine.status", "Charge: %s / %s FE");

            // Jade's own settings screen lists every provider, and asserts if one has no name.
            // That assert fires from ScreenEvent.Init - so a missing key here is not a blank line
            // in a config menu, it is a crash the moment any screen opens. There is no compile
            // error and no warning; the provider registers perfectly and the game dies later.
            add("config.jade.plugin_nauvis_power", "Project Nauvis: Power");
            add("config.jade.plugin_nauvis_power.boiler", "Boiler");
            add("config.jade.plugin_nauvis_power.steam_engine", "Steam engine");
            add("config.jade.plugin_nauvis_power.electric_pole", "Electric network");
            add("config.jade.plugin_nauvis_power.solar_panel", "Solar panel");
            add("config.jade.plugin_nauvis_power.accumulator", "Accumulator");

            // What Jade says about the block you are looking at. Present only when Jade is - the
            // strings are harmless without it, and a missing translation is worse than a spare one.
            add("jade.nauvis_power.network", "Network: %s poles, %s machines");
            add("jade.nauvis_power.network.live", "Carrying power");
            add("jade.nauvis_power.network.idle", "Idle - nothing is drawing");
            add("jade.nauvis_power.steam", "Steam: %s / %s");
            add("jade.nauvis_power.water", "Water: %s / %s");
            add("jade.nauvis_power.boiler.burning", "Burning");
            add("jade.nauvis_power.boiler.full", "Full - nothing is drawing the steam off");
            add("jade.nauvis_power.boiler.no_water", "No water");
            add("jade.nauvis_power.boiler.no_fuel", "Out of fuel");
            add("jade.nauvis_power.engine.running", "Making %s FE/t");
            add("jade.nauvis_power.engine.full", "Full - nothing is drawing the power off");
            add("jade.nauvis_power.engine.no_steam", "No steam");
            add("jade.nauvis_power.solar.making", "Making %s FE/t");
            add("jade.nauvis_power.solar.full", "Full - nothing is drawing the power off");
            add("jade.nauvis_power.solar.night", "Dark - waiting for the sun");
            add("jade.nauvis_power.solar.no_sky", "Under a roof - it needs open sky");
            // Direction first, because a battery at half looks the same filling as emptying.
            add("jade.nauvis_power.accumulator.charging", "Charging: +%s FE/t");
            add("jade.nauvis_power.accumulator.discharging", "Discharging: -%s FE/t");
            add("jade.nauvis_power.accumulator.full", "Full - waiting for the generators to fall short");
            add("jade.nauvis_power.accumulator.empty", "Empty - waiting for the generators to have spare");
            add("jade.nauvis_power.accumulator.idle", "Idle - %s%% charged");
            // On a pole, when the network has any: Factorio's power graph puts the accumulator
            // charge beside production, and this is the one line of it a tooltip has room for.
            add("jade.nauvis_power.network.accumulators", "Accumulators: %s, holding %s / %s FE");
            add("nauvis_power.electric_pole.status", "Network: %s poles, %s machines - live");
            add("nauvis_power.electric_pole.status_idle", "Network: %s poles, %s machines - idle");
            add("nauvis_power.electric_pole.detached", "Not part of a network");
        }
    }

    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            // One boiler and one engine, not seven and seventeen. The other cells are torn down
            // by the block itself, with drops enabled - that is what hands the player their
            // machine back whichever cell they hit - so only the anchor may carry a drop.
            add(ModBlocks.BOILER.get(), anchorOnly(ModBlocks.BOILER.get(), BoilerShape.SHAPE));
            add(ModBlocks.STEAM_ENGINE.get(),
                    anchorOnly(ModBlocks.STEAM_ENGINE.get(), SteamEngineShape.SHAPE));
            // One pole, not four - or twenty-four. The other cells are torn down by the block
            // itself and must drop nothing, or a big pole would be a way to make twenty-three
            // more. Same rule as the boiler and the engine, and now the same call.
            add(ModBlocks.SMALL_ELECTRIC_POLE.get(),
                    anchorOnly(ModBlocks.SMALL_ELECTRIC_POLE.get(), SmallPoleShape.SHAPE));
            add(ModBlocks.MEDIUM_ELECTRIC_POLE.get(),
                    anchorOnly(ModBlocks.MEDIUM_ELECTRIC_POLE.get(), MediumPoleShape.SHAPE));
            add(ModBlocks.BIG_ELECTRIC_POLE.get(),
                    anchorOnly(ModBlocks.BIG_ELECTRIC_POLE.get(), BigPoleShape.SHAPE));
            add(ModBlocks.SUBSTATION.get(),
                    anchorOnly(ModBlocks.SUBSTATION.get(), SubstationShape.SHAPE));
            add(ModBlocks.SOLAR_PANEL.get(),
                    anchorOnly(ModBlocks.SOLAR_PANEL.get(), SolarPanelShape.SHAPE));
            add(ModBlocks.ACCUMULATOR.get(),
                    anchorOnly(ModBlocks.ACCUMULATOR.get(), AccumulatorShape.SHAPE));
        }

        /**
         * Vanilla's {@code createSinglePropConditionTable}, for a property that is a number.
         *
         * <p>That method wants a {@code StringRepresentable}, which an {@code IntegerProperty} is
         * not - the pole's {@code PolePart} is an enum and can use it directly. The body is
         * vanilla's, explosion condition and all, against the {@code Property<Integer>}
         * overload of {@code hasProperty}.
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
