package com.jaguarm.nauvismachines.machine.assembler;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Assembling machine 2: crafting speed 0.75, 150 kW.
 *
 * <p>The same ten blocks, the same screen, the same block entity; a tier is two numbers on the
 * block, exactly as a belt tier is a speed on a subclass. Half again as fast as the first machine
 * and twice as hungry, which is Factorio's trade. Behind {@code automation-2}, which wants steel
 * and green science. Module slots are the part of the tier this pack does not have yet.
 */
public class AssemblingMachine2Block extends AssemblerBlock {

    public static final MapCodec<AssemblingMachine2Block> CODEC = simpleCodec(AssemblingMachine2Block::new);

    /** Factorio's {@code crafting_speed = 0.75}. */
    public static final float CRAFTING_SPEED = 0.75F;

    /** 150 kW at the pack's ratio: twice the first machine. */
    public static final int ENERGY_PER_TICK = 20;

    public AssemblingMachine2Block(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public float craftingSpeed() {
        return CRAFTING_SPEED;
    }

    @Override
    public int energyPerTick() {
        return ENERGY_PER_TICK;
    }
}
