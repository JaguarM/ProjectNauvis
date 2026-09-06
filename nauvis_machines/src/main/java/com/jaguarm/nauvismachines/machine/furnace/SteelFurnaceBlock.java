package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The steel furnace: the same two by two, twice the speed, the same fuel.
 *
 * <p>Factorio's trade, and the reason to research it: a steel furnace burns exactly what a stone
 * furnace burns and smelts twice as much with it, so a column of them is half the length and half
 * the coal. Six steel plates and ten stone bricks, behind {@code advanced-material-processing}.
 */
public class SteelFurnaceBlock extends FurnaceBlock {

    public static final MapCodec<SteelFurnaceBlock> CODEC = simpleCodec(SteelFurnaceBlock::new);

    /** Factorio's {@code crafting_speed = 2}. */
    public static final float CRAFTING_SPEED = 2.0F;

    public SteelFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return FurnaceShape.SHAPE;
    }

    @Override
    public float craftingSpeed() {
        return CRAFTING_SPEED;
    }

    @Override
    public boolean isBurner() {
        return true;
    }

    @Override
    public int energyPerTick() {
        return 0;
    }
}
