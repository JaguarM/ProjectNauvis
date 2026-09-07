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
import com.jaguarm.nauvislib.item.Stacks;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisLogistics.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisLogistics.MODID);

    public static final DeferredItem<BlockItem> BURNER_INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_INSERTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.INSERTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> LONG_HANDED_INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.LONG_HANDED_INSERTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> FAST_INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.FAST_INSERTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> STACK_INSERTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.STACK_INSERTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> IRON_CHEST =
            ITEMS.registerSimpleBlockItem(ModBlocks.IRON_CHEST, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> STEEL_CHEST =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_CHEST, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> TRANSPORT_BELT =
            ITEMS.registerSimpleBlockItem(ModBlocks.TRANSPORT_BELT, () -> Stacks.of(100));

    public static final DeferredItem<BlockItem> FAST_TRANSPORT_BELT =
            ITEMS.registerSimpleBlockItem(ModBlocks.FAST_TRANSPORT_BELT, () -> Stacks.of(100));

    public static final DeferredItem<BlockItem> SPLITTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.SPLITTER, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> FAST_SPLITTER =
            ITEMS.registerSimpleBlockItem(ModBlocks.FAST_SPLITTER, () -> Stacks.of(50));

    /** Its own tab. A subsystem mod has to be usable without the rest of the pack installed. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_logistics",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_logistics"))
                    .icon(() -> new ItemStack(BURNER_INSERTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(TRANSPORT_BELT.get());
                        output.accept(FAST_TRANSPORT_BELT.get());
                        output.accept(SPLITTER.get());
                        output.accept(FAST_SPLITTER.get());
                        output.accept(BURNER_INSERTER.get());
                        output.accept(INSERTER.get());
                        output.accept(LONG_HANDED_INSERTER.get());
                        output.accept(FAST_INSERTER.get());
                        output.accept(STACK_INSERTER.get());
                        output.accept(IRON_CHEST.get());
                        output.accept(STEEL_CHEST.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
