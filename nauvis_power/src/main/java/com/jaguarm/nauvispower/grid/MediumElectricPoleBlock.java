package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two copper plates and two steel plates, five blocks tall, and the pole that spans a gap the
 * small one cannot.
 *
 * <p>Nine blocks against the small pole's seven and a half, which is Factorio's pair of numbers.
 * The supply area is deliberately <em>not</em> larger - Factorio's medium pole supplies the same
 * 5x5 as the small one - so what the tier buys is distance between poles and nothing else. That
 * is what makes it a bus part rather than an upgrade: fewer poles down a long run, the same
 * number around a field of machines.
 *
 * <p>One block taller than the small pole, which is the tier made visible. Reach is a number a
 * player has to be told; standing a head higher is a thing they can see.
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
