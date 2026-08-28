package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The yellow belt: 1.875 tiles a second, 15 items a second over two lanes.
 *
 * <p>Those two numbers are the same number. Four items to a tile a lane, two lanes, 1.875 tiles a
 * second - so the throughput follows from the speed and there is only one figure to get right.
 * It is identity, it lives in {@code data/mapping.json} beside the id, and
 * {@code tools/check_models.py} fails the build if {@link #SPEED} here stops agreeing with it.
 */
public class TransportBeltBlock extends BeltBlock {

    public static final MapCodec<TransportBeltBlock> CODEC = simpleCodec(TransportBeltBlock::new);

    /**
     * 1.875 tiles a second, which at twenty ticks a second and sixty-four units a block is
     * exactly six. See {@link Belts} for why the units are what they are.
     */
    public static final int SPEED = 6;

    public static final String FACTORIO_ID = "transport-belt";

    public TransportBeltBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public int speed() {
        return SPEED;
    }

    @Override
    public String factorioId() {
        return FACTORIO_ID;
    }
}
