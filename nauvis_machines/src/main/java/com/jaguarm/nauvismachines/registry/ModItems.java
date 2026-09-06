package com.jaguarm.nauvismachines.registry;

import java.util.List;

import com.jaguarm.nauvismachines.NauvisMachines;

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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisMachines.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisMachines.MODID);

    public static final DeferredItem<BlockItem> ASSEMBLING_MACHINE_1 =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLING_MACHINE_1);

    public static final DeferredItem<BlockItem> ASSEMBLING_MACHINE_2 =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLING_MACHINE_2);

    public static final DeferredItem<BlockItem> STONE_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STONE_FURNACE);

    public static final DeferredItem<BlockItem> STEEL_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_FURNACE);

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_FURNACE);

    /**
     * Its own tab, rather than one shared with the pack mod. A subsystem mod has to be usable
     * on its own, and a tab that only exists when another mod is installed is not that.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_machines",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_machines"))
                    .icon(() -> new ItemStack(ASSEMBLING_MACHINE_1.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(STONE_FURNACE.get());
                        output.accept(STEEL_FURNACE.get());
                        output.accept(ELECTRIC_FURNACE.get());
                        output.accept(ASSEMBLING_MACHINE_1.get());
                        output.accept(ASSEMBLING_MACHINE_2.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
