package com.jaguarm.nauvis.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.jaguarm.nauvis.ModContent;
import com.jaguarm.nauvis.Nauvis;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
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
 * <p>Models, language strings and loot tables are derivable from the block list, so writing
 * them by hand only creates opportunities for the silent failures in {@code docs/API-26.2.md}
 * — a plural {@code loot_tables/} directory that makes a block drop nothing, an item icon
 * missing one of its two required files. Generated, they are right or they fail loudly.
 *
 * <p>Run with {@code ./gradlew :nauvis:runData}. Output lands in {@code src/generated/resources}
 * and is committed.
 */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class NauvisData {

    private NauvisData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(NauvisModels::new);
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

    /** Display names. One place, so a rename cannot leave a stale string behind. */
    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, Nauvis.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis", "Project Nauvis");
            addBlock(ModContent.ASSEMBLING_MACHINE_1, "Assembling machine 1");
        }
    }

    /** Every block drops itself. Machines are not a source of loot. */
    private static class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModContent.ASSEMBLING_MACHINE_1.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModContent.blocks();
        }
    }
}
