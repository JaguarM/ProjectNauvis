package com.jaguarm.nauvispower.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.grid.PolePart;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
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
            tag(BlockTags.CLIMBABLE).add(ModBlocks.SMALL_ELECTRIC_POLE.getKey());
        }
    }

    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisPower.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_power", "Project Nauvis: Power");
            addBlock(ModBlocks.BOILER, "Boiler");
            addBlock(ModBlocks.STEAM_ENGINE, "Steam engine");
            addBlock(ModBlocks.SMALL_ELECTRIC_POLE, "Small electric pole");
            add("pack.nauvis_power.crafting_table", "Nauvis Power: Crafting Table Recipes");

            add("nauvis_power.boiler.fuelled", "Burning %s");
            add("nauvis_power.boiler.full", "Its fuel slot is full of %s");
            add("nauvis_power.boiler.not_fuel", "%s does not burn");
            add("nauvis_power.boiler.status", "Steam: %s / %s");
            add("nauvis_power.steam_engine.status", "Charge: %s / %s FE");
            add("nauvis_power.small_electric_pole.status", "Network: %s poles, %s machines - live");
            add("nauvis_power.small_electric_pole.status_idle", "Network: %s poles, %s machines - idle");
            add("nauvis_power.small_electric_pole.detached", "Not part of a network");
        }
    }

    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.BOILER.get());
            dropSelf(ModBlocks.STEAM_ENGINE.get());
            // One pole, not four. The other three parts are torn down by the block itself and
            // must drop nothing, or a pole would be a way to make three more.
            add(ModBlocks.SMALL_ELECTRIC_POLE.get(), createSinglePropConditionTable(
                    ModBlocks.SMALL_ELECTRIC_POLE.get(), SmallElectricPoleBlock.PART, PolePart.FOOT));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
