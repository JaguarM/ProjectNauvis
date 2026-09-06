package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.mojang.serialization.MapCodec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The stack inserter: the fast inserter's swing with a hand that holds several.
 *
 * <p>Factorio 2.0 calls it the bulk inserter; the pack speaks the dump's names. It turns at the
 * fast inserter's 864 degrees a second and moves a <em>handful</em> each swing rather than one
 * item, and the size of the hand is the point of the whole line of research behind it: the
 * technology that unlocks it grants the first extra item, and each level of
 * {@code inserter-capacity-bonus} another. That is why the hand size is a question to the world
 * rather than a number on this class - see {@link #handSize} and {@link Bonuses}.
 *
 * <p>It is the inserter a bus is loaded and unloaded with, and it costs what that is worth:
 * fifteen circuits, fifteen gears, an advanced circuit and a fast inserter.
 */
public class StackInserterBlock extends ElectricInserterBlock {

    public static final MapCodec<StackInserterBlock> CODEC = simpleCodec(StackInserterBlock::new);

    /**
     * Factorio's modifier for how many more items a stack inserter's hand holds.
     *
     * <p>2.0's name for it, since the tree is 2.0's: {@code bulk-inserter-capacity-bonus}. Both
     * the technology files and this string have to say the same thing, and the file is generated
     * from the tree, so this is the one place the name is typed.
     */
    public static final String CAPACITY_BONUS = "bulk-inserter-capacity-bonus";

    /** The fast inserter's swing: 864 degrees a second, nine ticks. */
    public static final int SWING_TICKS = FastInserterBlock.SWING_TICKS;

    /**
     * FE per tick of a swing.
     *
     * <p>Factorio 2.0's bulk inserter draws 169 kW at full tilt, against a 900 kW engine's 120 FE
     * a tick: 22.5, and twenty-two is the nearest whole number. A hungry machine, and meant to be -
     * it moves a handful for the price.
     */
    public static final int ENERGY_PER_TICK = 22;

    public StackInserterBlock(Properties properties) {
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

    /**
     * One item, plus every {@code bulk-inserter-capacity-bonus} the world has researched.
     *
     * <p>The technology that unlocks the stack inserter is the first of those, so a freshly built
     * one already holds two, which is Factorio's number; the three capacity levels the tree
     * reaches take it to five. The ordinary inserters' bonus does not apply to it - Factorio keeps
     * the two lines apart, and so does the tree.
     */
    @Override
    public int handSize(ServerLevel level) {
        return 1 + Bonuses.count(level, CAPACITY_BONUS);
    }
}
