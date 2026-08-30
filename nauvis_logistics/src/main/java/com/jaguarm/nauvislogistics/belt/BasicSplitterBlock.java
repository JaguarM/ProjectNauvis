package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The yellow splitter: 1.875 tiles a second, the speed of the belt it is built out of.
 *
 * <p>Its item is {@code nauvis_logistics:splitter} and Factorio calls it simply a splitter - the
 * class is "basic" only because {@link SplitterBlock} is the family, exactly as Factorio's plain
 * inserter is {@link com.jaguarm.nauvislogistics.transport.ElectricInserterBlock} here. The id is
 * the identity and it is unchanged.
 *
 * <p>A splitter runs at its own tier's belt speed, which is what makes a line of one tier keep its
 * throughput across a split. {@code tools/check_models.py} holds {@link #SPEED} to the tiles a
 * second recorded for {@code splitter} in {@code data/mapping.json}, the way it holds a belt's.
 */
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
