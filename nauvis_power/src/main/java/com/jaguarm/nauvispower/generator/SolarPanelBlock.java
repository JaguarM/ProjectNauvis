package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.jaguarm.nauvispower.multiblock.Multiblock;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of the solar panel: nine blocks, one of which holds anything.
 *
 * <p>{@link SolarPanelShape} is the footprint and the geometry; {@link Multiblock} is everything
 * about being made of several blocks. No facing - a panel has no front - so the shape is only ever
 * asked for its north frame, exactly as the assembler is. Nothing opens: a panel has no slot and no
 * screen, and what it is doing is on the hover readout.
 */
public class SolarPanelBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    public SolarPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(SolarPanelShape.SHAPE.part(), SolarPanelShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return SolarPanelShape.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SolarPanelShape.SHAPE.part());
    }

    /** Only the middle has one; the other eight are structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new SolarPanelBlockEntity(pos, state) : null;
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

    /** Null, and so no placement at all, unless all nine blocks fit. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this, defaultBlockState(), context);
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

    /** Creative would otherwise hand back a free panel. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately. See SolarPanelBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            panel.serverTick(level);
        }
    }

    /**
     * Something beside or above the panel changed - a block placed over it, or taken off it.
     * Whichever of the nine blocks heard about it, the panel that has to wake is the one with the
     * block entity.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof SolarPanelBlockEntity panel) {
            panel.wake();
        }
    }
}
