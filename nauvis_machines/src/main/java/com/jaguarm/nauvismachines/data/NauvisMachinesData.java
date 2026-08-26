package com.jaguarm.nauvismachines.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;

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
    }

    /** Display names and the machine's messages. One place, so a rename leaves nothing stale. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisMachines.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_machines", "Project Nauvis: Machines");
            addBlock(ModBlocks.ASSEMBLING_MACHINE_1, "Assembling machine 1");

            // The screen. Its recipe list is Facrafting's panel, so there is very little here.
            add("screen.nauvis_machines.assembler.idle", "Idle - pick a recipe on the right");
            add("screen.nauvis_machines.assembler.making", "Making %s");
            add("screen.nauvis_machines.assembler.unknown", "Making something this client has not been told about");
            add("screen.nauvis_machines.assembler.wants", "Wants %s x %s");
        }
    }

    /** Every block drops itself. Machines are not a source of loot. */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.ASSEMBLING_MACHINE_1.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModItems.blocks();
        }
    }
}
