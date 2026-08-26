package com.jaguarm.nauvislogistics.storage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of the iron chest. There is very little of it, which is the point.
 *
 * <p>No ticking - a chest has nothing to do - so no scheduled ticks and nothing to wake. Nothing
 * spills its contents either: {@code BlockEntity#preRemoveSideEffects} already drops the contents
 * of anything that is a {@link net.minecraft.world.Container}, which this is. That is the same
 * hook the assembler and the inserter had to override by hand, and the reason they had to is that
 * their inventories are capability handlers rather than Containers.
 */
public class IronChestBlock extends BaseEntityBlock {

    public static final MapCodec<IronChestBlock> CODEC = simpleCodec(IronChestBlock::new);

    public IronChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IronChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }
}
