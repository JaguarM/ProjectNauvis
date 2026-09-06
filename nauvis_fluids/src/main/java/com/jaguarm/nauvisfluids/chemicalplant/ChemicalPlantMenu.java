package com.jaguarm.nauvisfluids.chemicalplant;

import com.jaguarm.nauvisfluids.processing.ProcessingInventory;
import com.jaguarm.nauvisfluids.processing.ProcessingMenu;
import com.jaguarm.nauvisfluids.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The chemical plant's menu: two ingredient slots, one output slot, and four tanks.
 *
 * <p>Where the slots are is stated here, so the screen and {@code tools/check_gui_layout.py}
 * read the same numbers: the two inputs side by side at the top left, the output at the top
 * right, and the tank bars under them.
 */
public class ChemicalPlantMenu extends ProcessingMenu {

    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_X = 152;
    public static final int OUTPUT_Y = 17;

    private static final int[][] INPUT_SLOTS = {{INPUT_X, INPUT_Y}, {INPUT_X + 18, INPUT_Y}};
    private static final int[][] OUTPUT_SLOTS = {{OUTPUT_X, OUTPUT_Y}};

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public ChemicalPlantMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory,
                new ProcessingInventory(ChemicalPlantBlockEntity.LAYOUT.itemSlots(), () -> {}),
                new SimpleContainerData(dataCount(ChemicalPlantBlockEntity.LAYOUT)), machinePos);
    }

    public ChemicalPlantMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler items,
            ContainerData data, BlockPos machinePos) {
        super(ModMenus.CHEMICAL_PLANT.get(), containerId, playerInventory, ChemicalPlantBlockEntity.LAYOUT,
                items, INPUT_SLOTS, OUTPUT_SLOTS, data, machinePos);
    }
}
