package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The electric inserter. Nothing to fuel it with by hand - it wants a pole in range.
 *
 * <p>Right-clicking says which of the two things is wrong, because "no power" and "pointing the
 * wrong way" are the two reasons an inserter stands still and they look identical from outside.
 */
public class ElectricInserterBlock extends InserterBlock {

    public static final MapCodec<ElectricInserterBlock> CODEC = simpleCodec(ElectricInserterBlock::new);

    public ElectricInserterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricInserterBlockEntity(pos, state);
    }

    @Override
    protected Component status(BlockState state, InserterBlockEntity inserter) {
        if (!inserter.running()) {
            return Component.translatable("nauvis_logistics.inserter.no_power");
        }
        return Component.translatable("nauvis_logistics.inserter.running",
                Component.literal(state.getValue(FACING).getName()));
    }
}
