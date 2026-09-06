package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The stone furnace: two by two, five stone, crafting speed 1, burns fuel.
 *
 * <p>The first machine a new world builds, and the one every plate in the opening comes out of.
 * It used to stand in as {@code minecraft:furnace}; it is a machine of the pack's own now, because
 * a furnace that smelted vanilla's recipes at vanilla's pace was not Factorio's furnace at all.
 */
public class StoneFurnaceBlock extends FurnaceBlock {

    public static final MapCodec<StoneFurnaceBlock> CODEC = simpleCodec(StoneFurnaceBlock::new);

    /** Factorio's {@code crafting_speed = 1}: a recipe takes exactly its own time. */
    public static final float CRAFTING_SPEED = 1.0F;

    public StoneFurnaceBlock(Properties properties) {
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
