package com.jaguarm.nauvisfluids.registry;

import java.util.List;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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

    /** A block item of its own, so a click on a lake puts the pump on the water rather than under it. */
    public static final DeferredItem<OffshorePumpItem> OFFSHORE_PUMP = ITEMS.registerItem("offshore_pump",
            properties -> new OffshorePumpItem(ModBlocks.OFFSHORE_PUMP.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> PUMPJACK = ITEMS.registerSimpleBlockItem(ModBlocks.PUMPJACK);

    /**
     * The map editor's oil well. No recipe and no drop, so survival never sees one; it exists so
     * that a test world can have oil where somebody wants it, exactly as Factorio's editor allows.
     */
    public static final DeferredItem<BlockItem> CRUDE_OIL = ITEMS.registerSimpleBlockItem(ModBlocks.CRUDE_OIL);

    // No item for natural water, deliberately. A liquid's item is a bucket, and a bucket of it is
    // vanilla's water bucket - which is the rule that keeps a lake where the world put it. See
    // the water block in ModBlocks. (tools/check_models.py reads this file to learn which blocks
    // have items, so the block's constant is not named here.)

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_fluids",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_fluids"))
                    .icon(() -> new ItemStack(PUMPJACK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PIPE.get());
                        output.accept(OFFSHORE_PUMP.get());
                        output.accept(PUMPJACK.get());
                        output.accept(CRUDE_OIL.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
