package com.jaguarm.nauvismachines.registry;

import java.util.List;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.item.RepairPackItem;
import com.jaguarm.nauvismachines.module.ModuleItem;
import com.jaguarm.nauvislib.item.Stacks;

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
            ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLING_MACHINE_1, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> ASSEMBLING_MACHINE_2 =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLING_MACHINE_2, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> STONE_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STONE_FURNACE, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> STEEL_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_FURNACE, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_FURNACE, () -> Stacks.of(50));

    public static final DeferredItem<BlockItem> RADAR = ITEMS.registerSimpleBlockItem(ModBlocks.RADAR, () -> Stacks.of(50));

    /**
     * The three first-tier modules, Factorio's colours and Factorio 2.0's numbers. The ids are the
     * dump's - {@code effectivity_module}, which Factorio has since renamed to efficiency - and
     * the display names are the modern ones, the same rule as the science packs.
     */
    public static final DeferredItem<ModuleItem> SPEED_MODULE = ITEMS.registerItem("speed_module",
            properties -> new ModuleItem(properties, ModuleItem.SPEED),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> EFFECTIVITY_MODULE = ITEMS.registerItem("effectivity_module",
            properties -> new ModuleItem(properties, ModuleItem.EFFICIENCY),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> PRODUCTIVITY_MODULE = ITEMS.registerItem("productivity_module",
            properties -> new ModuleItem(properties, ModuleItem.PRODUCTIVITY),
            () -> Stacks.of(50));

    /**
     * The second and third tiers, the same chip with a stripe for each tier. Each costs five
     * advanced circuits, five processing units and modules of the tier below - four for a 2,
     * five for a 3 - and the third tier of each ladder is what the rocket silo's technology
     * needs, which is why they exist before the beacon does.
     */
    public static final DeferredItem<ModuleItem> SPEED_MODULE_2 = ITEMS.registerItem("speed_module_2",
            properties -> new ModuleItem(properties, ModuleItem.SPEED_2),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> SPEED_MODULE_3 = ITEMS.registerItem("speed_module_3",
            properties -> new ModuleItem(properties, ModuleItem.SPEED_3),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> EFFECTIVITY_MODULE_2 = ITEMS.registerItem("effectivity_module_2",
            properties -> new ModuleItem(properties, ModuleItem.EFFICIENCY_2),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> EFFECTIVITY_MODULE_3 = ITEMS.registerItem("effectivity_module_3",
            properties -> new ModuleItem(properties, ModuleItem.EFFICIENCY_3),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> PRODUCTIVITY_MODULE_2 = ITEMS.registerItem("productivity_module_2",
            properties -> new ModuleItem(properties, ModuleItem.PRODUCTIVITY_2),
            () -> Stacks.of(50));

    public static final DeferredItem<ModuleItem> PRODUCTIVITY_MODULE_3 = ITEMS.registerItem("productivity_module_3",
            properties -> new ModuleItem(properties, ModuleItem.PRODUCTIVITY_3),
            () -> Stacks.of(50));

    /**
     * Two circuits and two gears, behind its own technology: a charge of three hundred health for
     * whatever the hostiles chewed, spent by clicking the damage. See {@link RepairPackItem}.
     */
    public static final DeferredItem<RepairPackItem> REPAIR_PACK = ITEMS.registerItem("repair_pack",
            RepairPackItem::new,
            () -> Stacks.of(100));

    /** Every module, first tier to third, for the model provider and the tab. */
    public static List<DeferredItem<ModuleItem>> modules() {
        return List.of(SPEED_MODULE, EFFECTIVITY_MODULE, PRODUCTIVITY_MODULE,
                SPEED_MODULE_2, EFFECTIVITY_MODULE_2, PRODUCTIVITY_MODULE_2,
                SPEED_MODULE_3, EFFECTIVITY_MODULE_3, PRODUCTIVITY_MODULE_3);
    }

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
                        output.accept(RADAR.get());
                        output.accept(REPAIR_PACK.get());
                        modules().forEach(module -> output.accept(module.get()));
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
