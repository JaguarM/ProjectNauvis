package com.jaguarm.nauvis;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What the pack mod puts in the world: the raw resources the other mods build on.
 *
 * <p>Deliberately almost nothing. The assembling machine started here and has moved to
 * {@code nauvis_machines}, because {@code data/mapping.json} names it
 * {@code nauvis_machines:assembling_machine_1} and non-negotiable #1 makes an id permanent from
 * the first commit - a block registered under the wrong namespace is exactly the kind of
 * mistake that survives into world saves. What belongs here is what PLAN.md gives the pack mod
 * and nothing else: raw resources, and terrain.
 *
 * <p>Solid fuel is the first. The mapping has always owned it here - {@code nauvis:solid_fuel} -
 * because Factorio counts it among the raw resources of its dump: three recipes make it, one
 * from each oil, so no product is <em>the</em> recipe and the dump lists it with the ores. It is
 * a fuel, worth three coal exactly as Factorio's 12 MJ is three of coal's 4, and that number is
 * {@code data/neoforge/data_maps/item/furnace_fuels.json}.
 */
public final class ModContent {

    private ModContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nauvis.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nauvis.MODID);

    /** Factorio's solid fuel: a brick of it out of a chemical plant, burnt like coal, three times over. */
    public static final DeferredItem<Item> SOLID_FUEL = ITEMS.registerSimpleItem("solid_fuel");

    /** Every block this mod registers, for a loot table provider to walk. */
    public static java.util.List<Block> blocks() {
        return BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModContent::buildCreativeTabs);
    }

    /** Beside coal, where a player looks for a fuel. The pack mod has too little for a tab of its own. */
    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SOLID_FUEL.get());
        }
    }
}
