package com.jaguarm.nauvisfluids.refinery;

import com.jaguarm.nauvisfluids.processing.ProcessingInventory;
import com.jaguarm.nauvisfluids.processing.ProcessingMenu;
import com.jaguarm.nauvisfluids.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The refinery's menu: five tanks and no slots. Everything else is {@link ProcessingMenu}'s. */
public class OilRefineryMenu extends ProcessingMenu {

    private static final int[][] NO_SLOTS = new int[0][];

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public OilRefineryMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new ProcessingInventory(0, () -> {}),
                new SimpleContainerData(dataCount(OilRefineryBlockEntity.LAYOUT)), machinePos);
    }

    public OilRefineryMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler items,
            ContainerData data, BlockPos machinePos) {
        super(ModMenus.OIL_REFINERY.get(), containerId, playerInventory, OilRefineryBlockEntity.LAYOUT,
                items, NO_SLOTS, NO_SLOTS, data, machinePos);
    }
}
