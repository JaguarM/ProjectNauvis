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
    public static final DeferredItem<BlockItem> STORAGE_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.STORAGE_TANK);
    public static final DeferredItem<BlockItem> OIL_REFINERY = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_REFINERY);
    public static final DeferredItem<BlockItem> CHEMICAL_PLANT = ITEMS.registerSimpleBlockItem(ModBlocks.CHEMICAL_PLANT);

    /**
     * The map editor's oil well. No recipe and no drop, so survival never sees one; it exists so
     * that a test world can have oil where somebody wants it, exactly as Factorio's editor allows.
     */
    public static final DeferredItem<BlockItem> CRUDE_OIL = ITEMS.registerSimpleBlockItem(ModBlocks.CRUDE_OIL);

    /**
     * Sulfur, coal and water in a chemical plant. An item of this mod's because
     * {@code data/mapping.json} says so; what it is for - cliff explosives, artillery - is
     * milestones away, so today it is a thing a chemical plant makes and a chest holds.
     */
    public static final DeferredItem<Item> EXPLOSIVES = ITEMS.registerSimpleItem("explosives");

    /**
     * Factorio's barrels: a steel drum, and the same drum full of each of the seven fluids.
     *
     * <p>An assembling machine 2 fills one from its fluid box - an empty barrel and fifty of the
     * fluid, a fifth of a second - and empties it again, which is how a fluid crosses a base by
     * belt or by hand. The dump carries the filled ones as raw items, so their recipes are
     * {@code data/fluid_recipes.json}'s, one pair a fluid.
     */
    public static final DeferredItem<Item> EMPTY_BARREL = ITEMS.registerSimpleItem("empty_barrel");
    public static final DeferredItem<Item> WATER_BARREL = ITEMS.registerSimpleItem("water_barrel");
    public static final DeferredItem<Item> CRUDE_OIL_BARREL = ITEMS.registerSimpleItem("crude_oil_barrel");
    public static final DeferredItem<Item> HEAVY_OIL_BARREL = ITEMS.registerSimpleItem("heavy_oil_barrel");
    public static final DeferredItem<Item> LIGHT_OIL_BARREL = ITEMS.registerSimpleItem("light_oil_barrel");
    public static final DeferredItem<Item> LUBRICANT_BARREL = ITEMS.registerSimpleItem("lubricant_barrel");
    public static final DeferredItem<Item> PETROLEUM_GAS_BARREL = ITEMS.registerSimpleItem("petroleum_gas_barrel");
    public static final DeferredItem<Item> SULFURIC_ACID_BARREL = ITEMS.registerSimpleItem("sulfuric_acid_barrel");

    /** The filled barrels, in the fluids' order. */
    public static final List<DeferredItem<Item>> FILLED_BARRELS = List.of(WATER_BARREL, CRUDE_OIL_BARREL,
            HEAVY_OIL_BARREL, LIGHT_OIL_BARREL, LUBRICANT_BARREL, PETROLEUM_GAS_BARREL, SULFURIC_ACID_BARREL);

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
                        output.accept(STORAGE_TANK.get());
                        output.accept(OIL_REFINERY.get());
                        output.accept(CHEMICAL_PLANT.get());
                        output.accept(CRUDE_OIL.get());
                        output.accept(EXPLOSIVES.get());
                        output.accept(EMPTY_BARREL.get());
                        FILLED_BARRELS.forEach(barrel -> output.accept(barrel.get()));
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
