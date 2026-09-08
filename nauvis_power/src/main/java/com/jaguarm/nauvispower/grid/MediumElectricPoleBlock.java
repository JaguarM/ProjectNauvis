package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two copper plates and two steel plates, five blocks tall, and the pole that spans a gap the
 * small one cannot.
 */
public class MediumElectricPoleBlock extends ElectricPoleBlock {

    public static final MapCodec<MediumElectricPoleBlock> CODEC = simpleCodec(MediumElectricPoleBlock::new);

    /** Factorio's number. Behaviour, so it is tunable; see {@link #wireReach()}. */
    public static final double WIRE_REACH = 9.0;

    public MediumElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return MediumPoleShape.SHAPE;
    }

    @Override
    public double wireReach() {
        return WIRE_REACH;
    }
}
