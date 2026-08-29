package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.jaguarm.nauvispower.multiblock.Multiblock;
import com.mojang.serialization.MapCodec;

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
 * The block half of the boiler. Right-click any of its seven blocks to open it.
 *
 * <p>It used to be fuelled by right-clicking with coal in hand and questioned by right-clicking
 * empty-handed, which was a stand-in for a screen and a bad one: you could not see what was in it,
 * could not take the coal back out, and had no way to tell a full boiler from an unfuelled one
 * without a line of text. {@link BoilerMenu} replaces both.
 *
 * <h2>Three tiles by two, with a facing</h2>
 *
 * <p>{@link BoilerShape} is the footprint and the geometry; {@link Multiblock} is everything about
 * being made of several blocks, and it is {@code ElectricPoleBlock}'s four rules with two more
 * axes. This is the first machine in the pack with <em>both</em> a footprint and a facing, so it is
 * the first to exercise the rotation: the cells turn, the geometry turns with them, and the steam
 * port turns with both.
 *
 * <p>The facing is the furnace's - the front looks back at whoever placed it - because a boiler has
 * a front worth seeing, and because the steam then leaves at the back, away from the player and
 * towards whatever the boiler is feeding.
 */
public class BoilerBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    /** Which way the boiler looks, and so which face its steam leaves by. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BoilerShape.SHAPE.part(), BoilerShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return BoilerShape.SHAPE;
    }

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BoilerShape.SHAPE.part());
    }

    /** Only the cell under the chimney has one; the other six are structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new BoilerBlockEntity(pos, state) : null;
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

    /**
     * Front towards the player, so the boiler grows away from them and the steam leaves at the
     * back. Null - and so no placement at all - unless all seven blocks fit.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()),
                context);
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

    /** Creative would otherwise hand back a free boiler. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately. See BoilerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            boiler.serverTick(level);
        }
    }

    /**
     * An engine or a pipe placed beside it changes whether there is any point burning coal.
     *
     * <p>Whichever of the seven blocks heard about it, the boiler that has to wake is the one with
     * the block entity - a cell that kept the news to itself would leave the machine asleep.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof BoilerBlockEntity boiler) {
            boiler.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!(level.getBlockEntity(anchor) instanceof BoilerBlockEntity boiler)) {
            return InteractionResult.PASS;
        }

        player.openMenu(boiler);
        return InteractionResult.SUCCESS;
    }

    // Fuel is spilled from BoilerBlockEntity#preRemoveSideEffects, not from here.
}
