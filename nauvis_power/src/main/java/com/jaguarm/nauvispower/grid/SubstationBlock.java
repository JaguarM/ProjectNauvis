package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Two tiles by two, five blocks tall, reaching eighteen and covering eighteen by eighteen.
 *
 * <p>The odd one of the four, and Factorio's numbers say why: it reaches less far than the big
 * pole and covers nine times the ground. A big pole carries a bus across a base; a substation
 * stands in the middle of a field of machines and feeds all of it. Neither replaces the other,
 * which is the whole reason both exist.
 *
 * <p>Eighteen out-reaches a cell of the pole index, so this is the second block in
 * {@code PowerNetworkManager}'s {@code longReach} set, and the first that proves that set is not
 * about the big pole in particular.
 *
 * <p><b>It has no recipe and no technology yet</b>, so it is creative-only. Factorio's recipe is
 * five advanced circuits, and an advanced circuit is plastic, which is oil - milestone 5. The
 * block is here early because the four tiers are one piece of work: the shapes, the reaches and
 * the supply areas are the same mechanism four times, and building three of them and coming back
 * for the fourth would mean reading all of it again. The recipe is a
 * {@code neoforge:registered} condition away once the circuit exists; see
 * {@code tools/gen_recipes.py}.
 */
public class SubstationBlock extends ElectricPoleBlock {

    public static final MapCodec<SubstationBlock> CODEC = simpleCodec(SubstationBlock::new);

    /** Factorio's number. Longer than a cell of the pole index is wide - see the class comment. */
    public static final double WIRE_REACH = 18.0;

    /** Factorio's 18x18: the two-tile footprint plus eight tiles on every side. */
    public static final int SUPPLY_REACH = 8;

    public SubstationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return SubstationShape.SHAPE;
    }

    @Override
    public double wireReach() {
        return WIRE_REACH;
    }

    @Override
    public int supplyReach() {
        return SUPPLY_REACH;
    }
}
