package com.jaguarm.nauvis;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What the pack mod puts in the world.
 *
 * <p>Ids come from the mapping table and are not negotiable — {@code assembling_machine_1} is
 * what Factorio calls it and what a world save will remember. Everything behind the id may be
 * crude for now.
 */
public final class ModContent {

    private ModContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nauvis.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nauvis.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, Nauvis.MODID);

    /**
     * The first machine. Milestone 1's whole point is a chest feeding this and a chest taking
     * from it.
     *
     * <p>Currently a block and nothing more: no inventory, no recipe selector, no ticking. The
     * shortcut in PLAN.md is a recipe selector over one input inventory running Facrafting's
     * timed recipes, and that is the next thing to land here.
     */
    public static final DeferredBlock<Block> ASSEMBLING_MACHINE_1 = BLOCKS.registerSimpleBlock(
            "assembling_machine_1",
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ASSEMBLING_MACHINE_1_ITEM =
            ITEMS.registerSimpleBlockItem(ASSEMBLING_MACHINE_1);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis"))
                    .icon(() -> new ItemStack(ASSEMBLING_MACHINE_1_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ASSEMBLING_MACHINE_1_ITEM.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static java.util.List<Block> blocks() {
        return BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
