package com.jaguarm.nauvispower.generator;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of the boiler. Right-click it to open it.
 *
 * <p>It used to be fuelled by right-clicking with coal in hand and questioned by right-clicking
 * empty-handed, which was a stand-in for a screen and a bad one: you could not see what was in it,
 * could not take the coal back out, and had no way to tell a full boiler from an unfuelled one
 * without a line of text. {@link BoilerMenu} replaces both.
 */
public class BoilerBlock extends BaseEntityBlock {

    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    public BoilerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    // No getTicker override, deliberately. See BoilerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            boiler.serverTick(level);
        }
    }

    /** An engine placed or broken beside it changes whether there is any point burning coal. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            boiler.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler)) {
            return InteractionResult.PASS;
        }

        player.openMenu(boiler);
        return InteractionResult.SUCCESS;
    }

    // Fuel is spilled from BoilerBlockEntity#preRemoveSideEffects, not from here.
}
