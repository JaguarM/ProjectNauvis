package com.jaguarm.nauvisfluids.processing;

import com.jaguarm.nauvislib.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of a refinery or a chemical plant: placement, breaking, opening, and which way
 * it points.
 */
public abstract class ProcessingBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    /** Where the machine's own north points. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected ProcessingBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(shape().part(), shape().anchor()));
    }

    /** The block entity for this machine's anchor. */
    protected abstract ProcessingBlockEntity newMachine(BlockPos pos, BlockState state);

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, shape().part());
    }

    /** Only the anchor has one; the rest is structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? newMachine(pos, state) : null;
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

    /** The machine's north is the way the player looks. The ghost asks this too. */
    @Override
    public Direction placementFacing(BlockPlaceContext context) {
        return context.getHorizontalDirection();
    }

    /** Null, and so no placement at all, unless the whole machine fits and nobody is standing there. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, placementFacing(context)), context);
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

    /** Creative would otherwise hand back a free machine. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately. See ProcessingBlockEntity on sleeping.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ProcessingBlockEntity machine) {
            machine.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up: a pipe laid at a port, a wire reaching the machine.
     * Whichever cell heard about it, the machine to wake is the one with the block entity.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof ProcessingBlockEntity machine) {
            machine.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!(level.getBlockEntity(anchor) instanceof ProcessingBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        // The anchor's position travels with the menu: the screen reads the chosen recipe off
        // the block entity, because a recipe key cannot be a data slot.
        player.openMenu(machine, anchor);
        return InteractionResult.SUCCESS;
    }
}
