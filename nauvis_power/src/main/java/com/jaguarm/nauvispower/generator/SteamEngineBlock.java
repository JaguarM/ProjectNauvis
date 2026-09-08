package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/** The block half of the steam engine. Right-click to ask how much charge it is holding. */
public class SteamEngineBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<SteamEngineBlock> CODEC = simpleCodec(SteamEngineBlock::new);

    /**
     * Which way the engine lies, and therefore which two faces steam goes in and out of.
     *
     * <p>Factorio's steam engines are built in a row off one boiler, and the row is the point:
     * steam runs along the line and every engine on it takes a share. An engine with connections
     * on all six sides would make that arrangement meaningless and would let you feed one from
     * below by accident.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SteamEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SteamEngineShape.SHAPE.part(), SteamEngineShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return SteamEngineShape.SHAPE;
    }

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SteamEngineShape.SHAPE.part());
    }

    /**
     * Laid along the way the player is looking, so a row of engines is built by walking the line.
     *
     * <p>Which of the two ends is "front" does not matter - the engine draws from both - so there
     * is no wrong way round, only a wrong axis.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, placementFacing(context)), context);
    }

    /** Along the line the player looks down, which is the axis its steam ends are on. */
    @Override
    public Direction placementFacing(BlockPlaceContext context) {
        return context.getHorizontalDirection();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by,
            ItemStack stack) {
        Multiblock.setPlacedBy(this, level, pos, state);
    }

    /** The whole teardown, in one rule. See {@link Multiblock#updateShape}. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        BlockState result = Multiblock.updateShape(
                this, state, level, pos, direction, neighbourPos, neighbourState);
        return result.isAir()
                ? result
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos,
                        neighbourState, random);
    }

    /** Creative would otherwise hand back a free engine. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape().cell(Multiblock.part(this, state)).shape(facing(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape().cell(Multiblock.part(this, state)).collisionShape(facing(state));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Only the middle of the spine has one; the other sixteen are structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new SteamEngineBlockEntity(pos, state) : null;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine) {
            engine.serverTick(level);
        }
    }

    /** A neighbouring block entity changed - most usefully, a pipe that now has steam. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level instanceof ServerLevel serverLevel) {
            wake(serverLevel, state, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        wake(level, state, pos);
    }

    /** Whichever of the seventeen heard it, the engine that has to act is the one with the tank. */
    private void wake(LevelReader level, BlockState state, BlockPos pos) {
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof SteamEngineBlockEntity engine) {
            engine.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!(level.getBlockEntity(anchor) instanceof SteamEngineBlockEntity engine)) {
            return InteractionResult.PASS;
        }

        player.sendOverlayMessage(Component.translatable("nauvis_power.steam_engine.status",
                engine.energyStored(), SteamEngineBlockEntity.ENERGY_CAPACITY));
        return InteractionResult.SUCCESS;
    }
}
