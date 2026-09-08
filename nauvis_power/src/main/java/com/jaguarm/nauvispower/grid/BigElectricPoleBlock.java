package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/** Five copper plates and five steel plates, two tiles by two and six blocks tall. */
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
