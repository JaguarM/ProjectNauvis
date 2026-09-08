package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/** Two tiles by two, five blocks tall, reaching eighteen and covering eighteen by eighteen. */
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
