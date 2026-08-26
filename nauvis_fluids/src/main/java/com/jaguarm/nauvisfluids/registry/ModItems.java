package com.jaguarm.nauvisfluids.registry;

import java.util.List;

import com.jaguarm.nauvisfluids.NauvisFluids;

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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisFluids.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisFluids.MODID);

    public static final DeferredItem<BlockItem> PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.PIPE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_fluids",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_fluids"))
                    .icon(() -> new ItemStack(PIPE.get()))
                    .displayItems((parameters, output) -> output.accept(PIPE.get()))
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
