package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two copper cable and two oak planks, four blocks tall, and the first grid a player builds.
 *
 * <p>Reaches 7.5 blocks, which is Factorio's number.
 */
public class SmallElectricPoleBlock extends ElectricPoleBlock {

    public static final MapCodec<SmallElectricPoleBlock> CODEC = simpleCodec(SmallElectricPoleBlock::new);

    /** Factorio's number. Behaviour, so it is tunable; see {@link #wireReach()}. */
    public static final double WIRE_REACH = 7.5;

    public SmallElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return SmallPoleShape.SHAPE;
    }

    @Override
    public double wireReach() {
        return WIRE_REACH;
    }
}
