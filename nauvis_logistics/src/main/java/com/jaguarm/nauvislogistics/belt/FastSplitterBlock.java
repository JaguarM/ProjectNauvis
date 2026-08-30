package com.jaguarm.nauvislogistics.belt;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The red splitter: 3.75 tiles a second, the speed of the red belt, and the other half of the
 * belt tier {@code logistics-2} unlocks.
 *
 * <p><b>The whole tier is this file, and it was not free.</b> The belt paid for its tiers in
 * advance and the splitter did not: {@code SPEED} was a constant on the one concrete splitter
 * class and both {@code SplitterBlockEntity.tick} and {@code SplitterBlock.stepOn} read it
 * statically, so this class would have carried items at the yellow splitter's speed and passed
 * every splitter test there was. Making {@link SplitterBlock} abstract with {@link
 * SplitterBlock#speed()} on it is what this cost; see that class.
 *
 * <p>One block entity type still covers both tiers, and it holds no speed of its own - it asks the
 * block, every tick, exactly as {@link BeltRun} asks the belt it was built from.
 *
 * <p>Fast-replace is deliberately not part of it. A belt in hand replaces the belt it is clicked
 * on, but a splitter is two blocks and a multiblock anchor, so swapping one in place is a
 * different problem from swapping a belt - see {@code docs/GAPS.md}.
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
