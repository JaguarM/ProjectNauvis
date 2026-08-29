package com.jaguarm.nauvispower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two copper plates and two steel plates, and the pole that spans a gap the small one cannot.
 *
 * <p>Nine blocks against the small pole's seven and a half, which is Factorio's pair of numbers.
 * The supply area is deliberately <em>not</em> larger - Factorio's medium pole supplies the same
 * 5x5 as the small one, so what the tier buys is distance between poles and nothing else. That is
 * what makes it a bus part rather than an upgrade: fewer poles down a long run, the same number
 * around a field of machines.
 *
 * <p>Same four blocks tall as the small pole. Height is ours rather than Factorio's - see
 * ARCHITECTURE.md - and a taller pole would need its own {@code PolePart} enum, because the part
 * count is the enum's length and the teardown rule chains through its ordinals. That is a copy of
 * the one mechanism the multi-block's correctness rests on, bought for one block of height, so the
 * tiers are told apart by their metal instead.
 */
public class MediumElectricPoleBlock extends ElectricPoleBlock {

    public static final MapCodec<MediumElectricPoleBlock> CODEC = simpleCodec(MediumElectricPoleBlock::new);

    /** Factorio's medium pole. The gap to {@link SmallElectricPoleBlock#WIRE_REACH} is the point. */
    public static final double WIRE_REACH = 9.0;

    public MediumElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public double wireReach() {
        return WIRE_REACH;
    }
}
