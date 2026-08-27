package com.jaguarm.nauvispower.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;

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
            dropSelf(ModBlocks.SMALL_ELECTRIC_POLE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
