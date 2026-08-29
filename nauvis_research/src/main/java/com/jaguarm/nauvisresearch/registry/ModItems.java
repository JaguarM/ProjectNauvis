package com.jaguarm.nauvisresearch.registry;

import java.util.List;

import com.jaguarm.nauvisresearch.NauvisResearch;

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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisResearch.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisResearch.MODID);

    public static final DeferredItem<BlockItem> LAB = ITEMS.registerSimpleBlockItem(ModBlocks.LAB);

    /**
     * Red science.
     *
     * <p>The id is the dump's, not the modern name. Factorio renamed {@code science-pack-1} to
     * {@code automation-science-pack} after 1.0 and {@code reference/factorio/recipes.json} is
     * from before that, so the whole mapping speaks the old names - see {@code docs/MAPPING.md}.
     * An id is permanent; the display name is not, and says "Automation science pack".
     */
    public static final DeferredItem<Item> SCIENCE_PACK_1 = ITEMS.registerSimpleItem("science_pack_1");

    /**
     * Green science, and the gate in front of the whole of milestone 3 - every technology that
     * unlocks a steel furnace, a solar panel, a medium pole or the second assembler is paid for
     * partly in this.
     *
     * <p>An inserter and a transport belt, which is Factorio's recipe and the reason this pack
     * needs no research to unlock: both of its ingredients are already free, so it can be gated
     * behind {@code science-pack-2} without a new world being unable to reach it. Same id rule as
     * red science above - the dump's name, not the modern {@code logistic-science-pack}.
     */
    public static final DeferredItem<Item> SCIENCE_PACK_2 = ITEMS.registerSimpleItem("science_pack_2");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_research",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_research"))
                    .icon(() -> new ItemStack(LAB.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(LAB.get());
                        output.accept(SCIENCE_PACK_1.get());
                        output.accept(SCIENCE_PACK_2.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
