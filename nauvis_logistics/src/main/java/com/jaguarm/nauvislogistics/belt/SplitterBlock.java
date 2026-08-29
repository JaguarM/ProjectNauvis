package com.jaguarm.nauvislogistics.belt;

import com.jaguarm.nauvislogistics.multiblock.MachineShape;
import com.jaguarm.nauvislogistics.multiblock.Multiblock;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The 2x1 splitter block: splits and balances items across two belt lines.
 *
 * <p>Directional multiblock (2 wide, 1 deep). The left cell (part 0) carries the block entity.
 */
public class SplitterBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<SplitterBlock> CODEC = simpleCodec(SplitterBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 6 units a tick = 1.875 tiles a second, matching the transport belt. */
    public static final int SPEED = 6;

    public static final String FACTORIO_ID = "splitter";

    private static final VoxelShape COLLISION_BOX = Block.box(0, 0, 0, 16, Belts.HEIGHT * 16, 16);

    public SplitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SplitterShape.SHAPE.part(), SplitterShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return SplitterShape.SHAPE;
    }

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    public int speed() {
        return SPEED;
    }

    public String factorioId() {
        return FACTORIO_ID;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SplitterShape.SHAPE.part());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new SplitterBlockEntity(pos, state) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape().cell(Multiblock.part(this, state)).shape(facing(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return COLLISION_BOX;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, context.getHorizontalDirection()),
                context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by,
            ItemStack stack) {
        Multiblock.setPlacedBy(this, level, pos, state);
    }

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

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Wakes the splitter when something beside it changes.
     *
     * <p>There is no {@code tick} here to go with it. A splitter is ticked by {@link BeltLines},
     * along with the runs either side of it and on both sides of the wire - see
     * {@link SplitterBlockEntity}. A scheduled block tick would be the server only, and a splitter
     * the client never advances is one that swallows everything put into it.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof SplitterBlockEntity splitter) {
            splitter.wake();
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (entity.isPassenger() || entity.isShiftKeyDown()) {
            return;
        }
        Direction travel = state.getValue(FACING);
        double step = SPEED / (double) Belts.UNITS_PER_BLOCK;

        boolean standing = entity.onGround();
        entity.move(MoverType.SELF, new Vec3(travel.getStepX() * step, 0.0, travel.getStepZ() * step));
        entity.setOnGround(standing);
    }
}
