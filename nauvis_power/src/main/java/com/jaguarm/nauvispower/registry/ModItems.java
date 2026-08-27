package com.jaguarm.nauvispower.registry;

import java.util.List;

import com.jaguarm.nauvispower.NauvisPower;

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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisPower.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisPower.MODID);

    public static final DeferredItem<BlockItem> BOILER = ITEMS.registerSimpleBlockItem(ModBlocks.BOILER);
    public static final DeferredItem<BlockItem> STEAM_ENGINE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_ENGINE);
    public static final DeferredItem<BlockItem> SMALL_ELECTRIC_POLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.SMALL_ELECTRIC_POLE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_power",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_power"))
                    .icon(() -> new ItemStack(STEAM_ENGINE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BOILER.get());
                        output.accept(STEAM_ENGINE.get());
                        output.accept(SMALL_ELECTRIC_POLE.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
