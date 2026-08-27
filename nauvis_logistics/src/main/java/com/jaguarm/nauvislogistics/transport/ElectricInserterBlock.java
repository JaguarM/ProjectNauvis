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

    /**
     * No screen, and so still a line of text.
     *
     * <p>An electric inserter has no slot: a screen for it would be a panel containing one bar.
     * What it wants is the hover display - a Factorio-style readout of whatever you are looking
     * at - and until that exists this line is the only way to tell "no power" from "pointing the
     * wrong way". The pole says the same thing for the same reason.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof InserterBlockEntity inserter)) {
            return InteractionResult.PASS;
        }

        player.sendOverlayMessage(inserter.running()
                ? Component.translatable("nauvis_logistics.inserter.running",
                        Component.literal(state.getValue(FACING).getName()))
                : Component.translatable("nauvis_logistics.inserter.no_power"));
        return InteractionResult.SUCCESS;
    }
}
