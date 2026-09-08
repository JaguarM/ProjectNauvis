package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The long-handed inserter: the same arm on a longer boom, taking from two blocks behind and
 * giving two blocks in front.
 */
public class LongHandedInserterBlock extends ElectricInserterBlock {

    public static final MapCodec<LongHandedInserterBlock> CODEC =
            simpleCodec(LongHandedInserterBlock::new);

    /** Two blocks, both ways. Factorio's long-handed inserter reaches two tiles and so does this. */
    public static final int REACH = 2;

    /**
     * Ticks per item moved.
     *
     * <p>Factorio's long-handed inserter manages about 1.2 items a second, against the basic
     * arm's 0.83 and the burner's 0.6. Seventeen ticks is 1.18 a second, which is as near as a
     * whole tick gets. Behaviour rather than identity, like every other swing time here.
     */
    public static final int SWING_TICKS = 17;

    /**
     * FE per tick of a swing.
     *
     * <p>Derived from the basic arm rather than from a kilowatt figure: it swings 24/17 as fast
     * for twice the distance, so two FE a tick becomes three. Power numbers in this pack keep
     * Factorio's ratios rather than its units and none of them is identity - the id, the
     * ingredients and the craft time are, and those are generated.
     */
    public static final int ENERGY_PER_TICK = 3;

    public LongHandedInserterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public int reach() {
        return REACH;
    }

    @Override
    public int swingTicks() {
        return SWING_TICKS;
    }

    @Override
    public int energyPerTick() {
        return ENERGY_PER_TICK;
    }
}
