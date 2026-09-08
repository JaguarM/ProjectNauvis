package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The red splitter: 3.75 tiles a second, the speed of the red belt, and the other half of the
 * belt tier {@code logistics-2} unlocks.
 */
public class FastSplitterBlock extends SplitterBlock {

    public static final MapCodec<FastSplitterBlock> CODEC = simpleCodec(FastSplitterBlock::new);

    /**
     * 12 units a tick = 3.75 tiles a second, matching the fast transport belt. Exactly twice the
     * yellow splitter, which is Factorio's ratio.
     */
    public static final int SPEED = 12;

    public static final String FACTORIO_ID = "fast-splitter";

    public FastSplitterBlock(Properties properties) {
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
