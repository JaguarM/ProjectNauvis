package com.jaguarm.nauvispower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The small electric pole. Two copper cable and two oak planks, and the reason the rest of this
 * package exists.
 *
 * <p><b>It has no ticker and no {@code tick} override</b>, and that is the point rather than an
 * omission. A pole does nothing on its own: it joins a {@link PowerNetwork} when it loads and
 * leaves when it goes, and the network is what ticks. Compare the boiler and the steam engine,
 * which schedule their own ticks because each can answer "have I got work?" by looking at itself.
 * A pole cannot - it is a node in a graph, and the graph is the thing with work to do.
 *
 * <p>Right-click reports what it is part of, which is the only way to see a network from inside
 * the game and is how the wire reach and the supply area were checked by hand.
 */
public class SmallElectricPoleBlock extends BaseEntityBlock {

    public static final MapCodec<SmallElectricPoleBlock> CODEC = simpleCodec(SmallElectricPoleBlock::new);

    /** A thin post. Factorio's poles are thin, and a full cube would swallow the wire reach. */
    private static final VoxelShape SHAPE = Block.column(4.0, 0.0, 16.0);

    public SmallElectricPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmallElectricPoleBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        PowerNetworkManager manager = PowerNetworkManager.of(serverLevel);
        PowerNetwork network = manager.networkAt(pos);
        if (network == null) {
            player.sendOverlayMessage(Component.translatable("nauvis_power.small_electric_pole.detached"));
            return InteractionResult.SUCCESS;
        }

        player.sendOverlayMessage(Component.translatable(
                manager.isActive(network)
                        ? "nauvis_power.small_electric_pole.status"
                        : "nauvis_power.small_electric_pole.status_idle",
                network.poleCount(),
                network.endpointCount()));
        return InteractionResult.SUCCESS;
    }
}
