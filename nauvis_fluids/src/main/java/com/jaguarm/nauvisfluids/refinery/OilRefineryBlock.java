package com.jaguarm.nauvisfluids.refinery;

import com.jaguarm.nauvisfluids.processing.ProcessingBlock;
import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Five by five, facing the way it was placed. See {@link OilRefineryShape} and {@link ProcessingBlock}. */
public class OilRefineryBlock extends ProcessingBlock {

    public static final MapCodec<OilRefineryBlock> CODEC = simpleCodec(OilRefineryBlock::new);

    public OilRefineryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineShape shape() {
        return OilRefineryShape.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ProcessingBlockEntity newMachine(BlockPos pos, BlockState state) {
        return new OilRefineryBlockEntity(pos, state);
    }
}
