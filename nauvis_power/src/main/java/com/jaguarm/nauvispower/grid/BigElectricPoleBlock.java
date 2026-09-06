package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Five copper plates and five steel plates, two tiles by two and six blocks tall.
 *
 * <p>Thirty blocks of reach, which is Factorio's number and four times the small pole's. It is the
 * only tier that does not fit the pole index's cells, and {@code PowerNetworkManager} holds a
 * second, small index for exactly that reason - see {@code longReach} there. A pole that reaches
 * further than a cell is wide cannot be found by a neighbour's cell scan, and the failure is a
 * wire that is missing in some positions and present in others.
 *
 * <p>Supplies a 4x4 area rather than the 5x5 of the two below it, which is also Factorio's and is
 * the trade that stops it being a strict upgrade: a big pole reaches four times as far and covers
 * less ground under itself, so it is what you run a bus on and not what you feed a field of
 * machines with.
 */
public class BigElectricPoleBlock extends ElectricPoleBlock {

    public static final MapCodec<BigElectricPoleBlock> CODEC = simpleCodec(BigElectricPoleBlock::new);

    /** Factorio's number. Longer than a cell of the pole index is wide, which is load-bearing. */
    public static final double WIRE_REACH = 30.0;

    /**
     * Factorio's 4x4, against the 5x5 of the smaller two.
     *
     * <p>An even-sided area has no middle, so it is not a radius: it is the two-tile footprint
     * plus one tile on each side. Kept as a cube in Y for the same reason the others are - a
     * machine stacked above another is a reasonable thing to build.
     */
    public static final int SUPPLY_REACH = 1;

    @Override
    public int supplyReach() {
        return SUPPLY_REACH;
    }

    public BigElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineShape shape() {
        return BigPoleShape.SHAPE;
    }

    @Override
    public double wireReach() {
        return WIRE_REACH;
    }
}
