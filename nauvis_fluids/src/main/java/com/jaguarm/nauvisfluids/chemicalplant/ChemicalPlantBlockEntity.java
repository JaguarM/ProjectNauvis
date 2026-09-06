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
 *
 * <p>Factorio's numbers: two fluids in and two out, two item slots in and one out, crafting speed
 * one, and 210 kW - at the pack's ratio, {@value #ENERGY_PER_TICK} FE a tick. Its recipes are
 * {@code chemistry}, which {@code data/mapping.json} says of each of them.
 *
 * <p>Water keeps the left input, because every chemical plant recipe in Factorio that takes water
 * takes it first; the other fluid takes the right. The outputs are in recipe order, and no recipe
 * here makes two.
 */
public class ChemicalPlantBlockEntity extends ProcessingBlockEntity {

    /** Facrafting's category for the chemical plant's recipes; the panel's "Made in:" names this machine for it. */
    public static final String CATEGORY = "chemistry";
    /** 210 kW at the pack's ratio. The ratio is what is kept. */
    public static final int ENERGY_PER_TICK = 28;
    public static final int ITEM_INPUTS = 2;
    public static final int ITEM_OUTPUTS = 1;
    public static final ProcessingLayout LAYOUT =
            new ProcessingLayout(CATEGORY, 2, 2, ITEM_INPUTS, ITEM_OUTPUTS, ENERGY_PER_TICK);

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
        return new ChemicalPlantMenu(containerId, playerInventory, items(), menuData(), worldPosition);
    }
}
