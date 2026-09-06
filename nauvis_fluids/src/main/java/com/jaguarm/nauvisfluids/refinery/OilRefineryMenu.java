package com.jaguarm.nauvisfluids.refinery;

import com.jaguarm.nauvisfluids.processing.ProcessingInventory;
import com.jaguarm.nauvisfluids.processing.ProcessingMenu;
import com.jaguarm.nauvisfluids.registry.ModMenus;
import com.jaguarm.nauvislib.module.ModuleSlots;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The refinery's menu: five tanks, no item slots, and three module slots. Everything else is
 * {@link ProcessingMenu}'s.
 *
 * <p>The module slots sit in a row under the two input bars, where {@code tools/check_gui_layout.py}
 * reads them from.
 */
public class OilRefineryMenu extends ProcessingMenu {

    private static final int[][] NO_SLOTS = new int[0][];

    public static final int MODULE_X = 8;
    public static final int MODULE_Y = 40;

    private static final int[][] MODULE_SLOTS = {
        {MODULE_X, MODULE_Y}, {MODULE_X + 18, MODULE_Y}, {MODULE_X + 36, MODULE_Y},
    };

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public OilRefineryMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new ProcessingInventory(0, () -> {}),
                new ModuleSlots(OilRefineryBlockEntity.LAYOUT.moduleSlots(), () -> {}),
                new SimpleContainerData(dataCount(OilRefineryBlockEntity.LAYOUT)), machinePos);
    }

    public OilRefineryMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler items,
            ModuleSlots modules, ContainerData data, BlockPos machinePos) {
        super(ModMenus.OIL_REFINERY.get(), containerId, playerInventory, OilRefineryBlockEntity.LAYOUT,
                items, modules, NO_SLOTS, NO_SLOTS, MODULE_SLOTS, data, machinePos);
    }
}
