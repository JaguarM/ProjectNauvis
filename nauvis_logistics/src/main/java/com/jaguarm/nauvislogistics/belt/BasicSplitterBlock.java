package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/** The yellow splitter: 1.875 tiles a second, the speed of the belt it is built out of. */
public class BasicSplitterBlock extends SplitterBlock {

    public static final MapCodec<BasicSplitterBlock> CODEC = simpleCodec(BasicSplitterBlock::new);

    /** 6 units a tick = 1.875 tiles a second, matching the transport belt. */
    public static final int SPEED = 6;

    public static final String FACTORIO_ID = "splitter";

    public BasicSplitterBlock(Properties properties) {
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
