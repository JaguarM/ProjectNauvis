package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The electric furnace: three by three, the steel furnace's speed, on the grid instead of coal.
 *
 * <p>Factorio's 180 kW at the pack's ratio of 120 FE/t to a 900 kW engine, so one steam engine
 * runs five of them. Ten steel plates, five advanced circuits and ten stone bricks, behind
 * {@code advanced-material-processing-2} - which is blue science, so the recipe ships and waits
 * on the advanced circuit the way the substation's does.
 */
public class ElectricFurnaceBlock extends FurnaceBlock {

    public static final MapCodec<ElectricFurnaceBlock> CODEC = simpleCodec(ElectricFurnaceBlock::new);

    /** Factorio's {@code crafting_speed = 2}. */
    public static final float CRAFTING_SPEED = 2.0F;

    /** 180 kW at the pack's ratio. */
    public static final int ENERGY_PER_TICK = 24;

    public ElectricFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return ElectricFurnaceShape.SHAPE;
    }

    @Override
    public float craftingSpeed() {
        return CRAFTING_SPEED;
    }

    @Override
    public boolean isBurner() {
        return false;
    }

    @Override
    public int energyPerTick() {
        return ENERGY_PER_TICK;
    }

    /** Factorio's electric furnace has two; the burner furnaces have none. */
    @Override
    public int moduleSlots() {
        return MODULE_SLOTS;
    }

    public static final int MODULE_SLOTS = 2;
}
