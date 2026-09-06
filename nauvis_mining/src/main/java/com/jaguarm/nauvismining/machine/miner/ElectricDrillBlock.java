package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The electric mining drill: three tiles by three, and half a block high so a field of
 * them can be walked over.
 *
 * <p>A subclass rather than a tier field, for the reason {@link MinerBlock#shape()} gives: the
 * blockstate is built inside {@code Block}'s constructor, and a constant on a class is the only
 * thing that exists that early.
 */
public class ElectricDrillBlock extends MinerBlock {

    public static final MapCodec<ElectricDrillBlock> CODEC = simpleCodec(ElectricDrillBlock::new);

    public ElectricDrillBlock(Properties properties) {
        super(properties, MachineTier.ELECTRIC);
    }

    @Override
    public MachineShape shape() {
        return ElectricDrillShape.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
