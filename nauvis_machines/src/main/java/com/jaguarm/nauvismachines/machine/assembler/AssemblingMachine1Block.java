package com.jaguarm.nauvismachines.machine.assembler;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Assembling machine 1: crafting speed 0.5, 75 kW.
 *
 * <p>Factorio's first assembler works at half speed - a half-second recipe takes a second in it -
 * and that is identity in the sense a footprint is: the ratio between what a recipe says and what
 * the machine does is what every build guide in the player's head is written against. The FE is
 * the pack's ratio, twelve machines to a steam engine.
 */
public class AssemblingMachine1Block extends AssemblerBlock {

    public static final MapCodec<AssemblingMachine1Block> CODEC = simpleCodec(AssemblingMachine1Block::new);

    /** Factorio's {@code crafting_speed = 0.5}. */
    public static final float CRAFTING_SPEED = 0.5F;

    /** 75 kW at the pack's ratio of 120 FE/t to a 900 kW engine. */
    public static final int ENERGY_PER_TICK = 10;

    /** Four a minute, which is Factorio's figure for the first machine. */
    public static final double POLLUTION_PER_MINUTE = 4;

    public AssemblingMachine1Block(Properties properties) {
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

    @Override
    public double pollutionPerMinute() {
        return POLLUTION_PER_MINUTE;
    }
}
