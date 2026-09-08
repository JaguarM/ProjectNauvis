package com.jaguarm.nauvis;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What the pack mod puts in the world: the raw resources the other mods build on. */
public final class ModContent {

    private ModContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nauvis.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nauvis.MODID);

    /** Factorio's solid fuel: a brick of it out of a chemical plant, burnt like coal, three times over. */
    public static final DeferredItem<Item> SOLID_FUEL = ITEMS.registerSimpleItem("solid_fuel", () -> new Item.Properties().stacksTo(50));

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
