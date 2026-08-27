package com.jaguarm.nauvislogistics.registry;

import java.util.List;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisLogistics.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisLogistics.MODID);

    public static final DeferredItem<BlockItem> BURNER_INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_INSERTER);

    public static final DeferredItem<BlockItem> INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.INSERTER);

    public static final DeferredItem<BlockItem> IRON_CHEST =
            ITEMS.registerSimpleBlockItem(ModBlocks.IRON_CHEST);

    /** Its own tab. A subsystem mod has to be usable without the rest of the pack installed. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_logistics",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_logistics"))
                    .icon(() -> new ItemStack(BURNER_INSERTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BURNER_INSERTER.get());
                        output.accept(INSERTER.get());
                        output.accept(IRON_CHEST.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
