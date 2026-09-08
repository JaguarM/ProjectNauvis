package com.jaguarm.nauvisfluids.oil;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** An oil well: the block a pumpjack stands on. */
public class CrudeOilBlock extends BaseEntityBlock {

    public static final MapCodec<CrudeOilBlock> CODEC = simpleCodec(CrudeOilBlock::new);

    public CrudeOilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrudeOilBlockEntity(pos, state);
    }

    // No ticker, deliberately. A well does nothing until a pumpjack asks it to.
}
