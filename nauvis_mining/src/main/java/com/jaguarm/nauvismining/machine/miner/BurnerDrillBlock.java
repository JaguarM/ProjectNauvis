package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The burner mining drill: two tiles by two, burning fuel to dig.
 *
 * <p>A subclass rather than a tier field, for the reason {@link MinerBlock#shape()} gives: the
 * blockstate is built inside {@code Block}'s constructor, and a constant on a class is the only
 * thing that exists that early.
 */
public class BurnerDrillBlock extends MinerBlock {

    public static final MapCodec<BurnerDrillBlock> CODEC = simpleCodec(BurnerDrillBlock::new);

    public BurnerDrillBlock(Properties properties) {
        super(properties, MachineTier.BURNER);
    }

    @Override
    public MachineShape shape() {
        return BurnerDrillShape.SHAPE;
    }

    @Override
    public int outputCell() {
        return BurnerDrillShape.OUTPUT;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
