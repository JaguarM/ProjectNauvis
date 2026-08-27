package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The burner inserter. Right-click it to open it.
 *
 * <p>It used to be fuelled by right-clicking with coal in hand and questioned by right-clicking
 * empty-handed. Both were stand-ins for a screen: you could not see what was in the slot, could
 * not take it back out, and could not tell an inserter that was out of coal from one that simply
 * had nothing to move. {@link BurnerInserterMenu} replaces both.
 */
public class BurnerInserterBlock extends InserterBlock {

    public static final MapCodec<BurnerInserterBlock> CODEC = simpleCodec(BurnerInserterBlock::new);

    public BurnerInserterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerInserterBlockEntity(pos, state);
    }


    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BurnerInserterBlockEntity inserter)) {
            return InteractionResult.PASS;
        }

        player.openMenu(inserter);
        return InteractionResult.SUCCESS;
    }
}
