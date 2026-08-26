package com.jaguarm.nauvispower.generator;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of the steam engine. Right-click to ask how much charge it is holding.
 *
 * <p>It wakes on two things a boiler does not care about: a neighbouring block changing, which is
 * a boiler being placed or broken, and a neighbouring block <em>entity</em> changing, which is a
 * boiler gaining steam. The second is the same {@code onNeighborChange} trick the inserter uses -
 * every {@code setChanged} reaches all six neighbours - and without it an engine that ran dry
 * would sleep through the boiler beside it coming back to life.
 */
public class SteamEngineBlock extends BaseEntityBlock {

    public static final MapCodec<SteamEngineBlock> CODEC = simpleCodec(SteamEngineBlock::new);

    public SteamEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamEngineBlockEntity(pos, state);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine) {
            engine.serverTick(level);
        }
    }

    /** A neighbouring block entity changed - most usefully, a boiler that now has steam. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine) {
            engine.wake();
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine) {
            engine.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine)) {
            return InteractionResult.PASS;
        }

        player.sendOverlayMessage(Component.translatable("nauvis_power.steam_engine.status",
                engine.energyStored(), SteamEngineBlockEntity.ENERGY_CAPACITY));
        return InteractionResult.SUCCESS;
    }
}
