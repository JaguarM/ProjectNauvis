package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of the lab: three tiles by three, and ten blocks of it.
 *
 * <p>Right-click any of the ten to open it, holding anything or nothing, exactly like a chest.
 * Science packs go in its slots and it works its way through them.
 *
 * <p>No facing. A Factorio lab has no direction - it is fed from wherever the belt runs and has
 * nothing to point anywhere - so the blockstate is a tenth the size it would otherwise be, and
 * {@link LabShape} is only ever asked for its north frame.
 *
 * <p>Everything about being made of several blocks is {@link Multiblock}'s, and it is
 * {@code SmallElectricPoleBlock}'s four rules with two more axes.
 */
public class LabBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<LabBlock> CODEC = simpleCodec(LabBlock::new);

    public LabBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(LabShape.SHAPE.part(), LabShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return LabShape.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LabShape.SHAPE.part());
    }

    /** Only the middle has one; the other nine are structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new LabBlockEntity(pos, state) : null;
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

    /** Null, and so no placement at all, unless all ten blocks fit and nobody is standing there. */
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

    /** Creative would otherwise hand back a free lab. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately: a registered ticker runs whether or not there is work.
    // A lab schedules its own ticks while it has packs and power. See LabBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof LabBlockEntity lab) {
            lab.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up, most usefully a pole being connected.
     *
     * <p>Forwarded to the anchor, whichever of the ten heard it: there are twelve faces round a
     * 3x3 machine and a wire may reach any of them, and a cell that kept the news to itself would
     * schedule a tick on a block with no block entity, which does nothing at all.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(Multiblock.anchorPos(this, state, pos))
                instanceof LabBlockEntity lab) {
            lab.wakeFromNeighbour();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(Multiblock.anchorPos(this, state, pos))
                instanceof LabBlockEntity lab)) {
            return InteractionResult.PASS;
        }

        player.openMenu(lab);
        return InteractionResult.SUCCESS;
    }

    // Packs are spilled from LabBlockEntity#preRemoveSideEffects, not from here.
}
