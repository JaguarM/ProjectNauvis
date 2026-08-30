package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The red belt: 3.75 tiles a second, 30 items a second over two lanes.
 *
 * <p>Exactly twice the yellow belt, which is Factorio's ratio and the reason the tier reads at a
 * glance - two yellow lines feed one red one, and a player who has built the first knows what the
 * second is worth without being told.
 *
 * <p><b>The whole tier is this file.</b> A belt block holds no behaviour, {@link BeltRun} does the
 * work, and one block entity type covers every tier there will ever be - so a second belt is a
 * speed, an id and a palette. The three things that made that true are worth naming, because they
 * are what a third tier will cost as well: speed is a constant on a subclass rather than a field
 * (see {@link BeltBlock}), the run is keyed on the block so two tiers are two runs without anything
 * being written, and {@code texture-workshop/make_belt_textures.py} renders a tier from a palette
 * and derives the tread's animation from the speed in {@code data/mapping.json}.
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
