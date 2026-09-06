package com.jaguarm.nauvisfluids.chemicalplant;

import com.jaguarm.nauvisfluids.processing.ProcessingBlock;
import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Three by three, facing the way it was placed. See {@link ChemicalPlantShape} and {@link ProcessingBlock}. */
public class ChemicalPlantBlock extends ProcessingBlock {

    public static final MapCodec<ChemicalPlantBlock> CODEC = simpleCodec(ChemicalPlantBlock::new);

    public ChemicalPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineShape shape() {
        return ChemicalPlantShape.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ProcessingBlockEntity newMachine(BlockPos pos, BlockState state) {
        return new ChemicalPlantBlockEntity(pos, state);
    }
}
