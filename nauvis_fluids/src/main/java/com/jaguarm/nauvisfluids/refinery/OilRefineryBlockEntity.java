package com.jaguarm.nauvisfluids.refinery;

import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvisfluids.processing.ProcessingLayout;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModFluids;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * An oil refinery: crude oil in, and depending on the recipe petroleum gas out, or heavy oil,
 * light oil and petroleum gas out of three separate ports.
 */
public class OilRefineryBlockEntity extends ProcessingBlockEntity {

    /** Facrafting's category for the two oil processing recipes; the panel's "Made in:" names this machine for it. */
    public static final String CATEGORY = "oil-processing";
    /** 420 kW at the pack's ratio. The ratio is what is kept. */
    public static final int ENERGY_PER_TICK = 56;

    /** Six a minute, Factorio's figure. */
    public static final double POLLUTION_PER_MINUTE = 6;
    /** Factorio's refinery takes three. */
    public static final int MODULE_SLOTS = 3;

    public static final ProcessingLayout LAYOUT =
            new ProcessingLayout(CATEGORY, 2, 3, 0, 0, ENERGY_PER_TICK, MODULE_SLOTS, POLLUTION_PER_MINUTE);

    /** Which input each fluid keeps: water left, crude right. */
    public static final int WATER_PORT = 0;
    public static final int CRUDE_PORT = 1;
    /** Which output each fluid keeps: heavy, light, petroleum, left to right. */
    public static final int HEAVY_PORT = 0;
    public static final int LIGHT_PORT = 1;
    public static final int PETROLEUM_PORT = 2;

    public OilRefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_REFINERY.get(), pos, state, LAYOUT);
    }

    @Override
    protected int preferredInput(@Nullable Fluid fluid) {
        if (fluid == Fluids.WATER) {
            return WATER_PORT;
        }
        if (fluid == ModFluids.CRUDE_OIL.get()) {
            return CRUDE_PORT;
        }
        return -1;
    }

    @Override
    protected int preferredOutput(@Nullable Fluid fluid) {
        if (fluid == ModFluids.HEAVY_OIL.get()) {
            return HEAVY_PORT;
        }
        if (fluid == ModFluids.LIGHT_OIL.get()) {
            return LIGHT_PORT;
        }
        if (fluid == ModFluids.PETROLEUM_GAS.get()) {
            return PETROLEUM_PORT;
        }
        return -1;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new OilRefineryMenu(containerId, playerInventory, items(), modules(), menuData(), worldPosition);
    }
}
