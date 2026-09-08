package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The fast inserter: the basic arm swinging nearly three times as fast, for three times the
 * power.
 */
public class FastInserterBlock extends ElectricInserterBlock {

    public static final MapCodec<FastInserterBlock> CODEC = simpleCodec(FastInserterBlock::new);

    /**
     * Ticks per item moved.
     *
     * <p>Factorio's fast inserter moves 2.31 items a second; twenty ticks over that is 8.7, and
     * nine is 2.22 a second. Its rotation speed is the identity - it is what tells the tiers apart
     * - and the tick count is the nearest whole number to it.
     */
    public static final int SWING_TICKS = 9;

    /**
     * FE per tick of a swing.
     *
     * <p>Factorio's fast inserter draws 46 kW against the basic arm's 13 and the steam engine's
     * 900; at the pack's 120 FE a tick per engine that is 6.1, and six is the nearest whole
     * number. Power numbers keep Factorio's ratios rather than its units, as everywhere here.
     */
    public static final int ENERGY_PER_TICK = 6;

    public FastInserterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
