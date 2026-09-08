package com.jaguarm.nauvisfluids.chemicalplant;

import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvisfluids.processing.ProcessingLayout;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * A chemical plant: the machine for everything that is neither smelting nor assembling - the
 * crackings, solid fuel, lubricant, sulfur and sulfuric acid, plastic, batteries, explosives.
 */
public class ChemicalPlantBlockEntity extends ProcessingBlockEntity {

    /** Facrafting's category for the chemical plant's recipes; the panel's "Made in:" names this machine for it. */
    public static final String CATEGORY = "chemistry";
    /** 210 kW at the pack's ratio. The ratio is what is kept. */
    public static final int ENERGY_PER_TICK = 28;

    /** Four a minute, Factorio's figure. */
    public static final double POLLUTION_PER_MINUTE = 4;
    public static final int ITEM_INPUTS = 2;
    public static final int ITEM_OUTPUTS = 1;
    /** Factorio's chemical plant takes three. */
    public static final int MODULE_SLOTS = 3;
    public static final ProcessingLayout LAYOUT =
            new ProcessingLayout(CATEGORY, 2, 2, ITEM_INPUTS, ITEM_OUTPUTS, ENERGY_PER_TICK, MODULE_SLOTS,
                    POLLUTION_PER_MINUTE);

    public static final int WATER_PORT = 0;

    public ChemicalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_PLANT.get(), pos, state, LAYOUT);
    }

    @Override
    protected int preferredInput(@Nullable Fluid fluid) {
        return fluid == Fluids.WATER ? WATER_PORT : -1;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChemicalPlantMenu(containerId, playerInventory, items(), modules(), menuData(), worldPosition);
    }
}
