package com.jaguarm.nauvispower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two copper cable and two oak planks, and the first grid a player builds.
 *
 * <p>Reaches 7.5 blocks, which is Factorio's number and the reason the pole index buckets into
 * cells at all - see {@code PowerNetworkManager}.
 */
public class SmallElectricPoleBlock extends ElectricPoleBlock {

    public static final MapCodec<SmallElectricPoleBlock> CODEC = simpleCodec(SmallElectricPoleBlock::new);

    /** Factorio's small pole. Behaviour, so it is tunable; see {@link #wireReach()}. */
    public static final double WIRE_REACH = 7.5;

    public SmallElectricPoleBlock(Properties properties) {
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
