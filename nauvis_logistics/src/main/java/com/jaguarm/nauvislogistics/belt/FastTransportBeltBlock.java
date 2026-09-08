package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The red belt: 3.75 tiles a second, 30 items a second over two lanes.
 *
 * @see BeltBlock#useItemOn for what holding one of these against a yellow belt does
 */
public class FastTransportBeltBlock extends BeltBlock {

    public static final MapCodec<FastTransportBeltBlock> CODEC = simpleCodec(FastTransportBeltBlock::new);

    /**
     * 3.75 tiles a second, which at twenty ticks a second and sixty-four units a block is exactly
     * twelve. See {@link Belts} for why the units are what they are.
     */
    public static final int SPEED = 12;

    public static final String FACTORIO_ID = "fast-transport-belt";

    public FastTransportBeltBlock(Properties properties) {
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
