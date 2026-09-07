package com.jaguarm.nauvisrocket.registry;

import java.util.List;

import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvislib.item.Stacks;

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

/**
 * The four items of the rocket, at the dump's ids and the dump's recipes.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisRocket.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisRocket.MODID);

    public static final DeferredItem<BlockItem> ROCKET_SILO = ITEMS.registerSimpleBlockItem(ModBlocks.ROCKET_SILO, () -> Stacks.of(1));

    /**
     * Ten low density structures, ten rocket control units and ten rocket fuel, three seconds,
     * only in the silo. It exists as an item because the dump makes it one and the mapping owns
     * it, and so that a creative player can hand a silo a rocket; a silo never puts one out -
     * the parts it builds go into the rocket standing on it.
     */
    public static final DeferredItem<Item> ROCKET_PART = ITEMS.registerSimpleItem("rocket_part", () -> Stacks.of(5));

    /**
     * A hundred each of low density structures, processing units, solar panels and
     * accumulators, five radars and fifty rocket fuel, five seconds: the thing the rocket
     * carries, and the reason it comes back with space science. One at a time in the silo.
     */
    public static final DeferredItem<Item> SATELLITE = ITEMS.registerSimpleItem("satellite", () -> Stacks.of(1));

    /**
     * What a launched satellite sends back: a thousand of these, out of the silo's output. The
     * dump's recipe - a hundred rocket parts and a satellite, five minutes - is the launch,
     * written as a recipe, and the silo reads its numbers off it.
     */
    public static final DeferredItem<Item> SPACE_SCIENCE_PACK = ITEMS.registerSimpleItem("space_science_pack", () -> Stacks.of(2000));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_rocket",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_rocket"))
                    .icon(() -> new ItemStack(SATELLITE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ROCKET_SILO.get());
                        output.accept(ROCKET_PART.get());
                        output.accept(SATELLITE.get());
                        output.accept(SPACE_SCIENCE_PACK.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
