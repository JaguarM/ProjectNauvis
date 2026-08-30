package com.jaguarm.nauvislogistics.belt;

import com.jaguarm.nauvislogistics.multiblock.MachineShape;
import com.jaguarm.nauvislogistics.multiblock.Multiblock;

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
 *
 * <h2>Abstract, for the same reason {@link BeltBlock} is</h2>
 *
 * <p>A splitter has a tier and the tier is a speed, so speed is {@link #speed()} on the block and
 * never a constant anybody reads through a class name. It was one for a while, and that is a trap
 * worth naming: {@code SplitterBlockEntity} read {@code SplitterBlock.SPEED} statically, so a fast
 * splitter would have carried items at the yellow one's speed and passed every test in the file
 * except the one that measures it. A method on a subclass is also safe from the
 * {@code createBlockStateDefinition} trap a field would fall into - see docs/PITFALLS.md.
 */
public abstract class SplitterBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape COLLISION_BOX = Block.box(0, 0, 0, 16, Belts.HEIGHT * 16, 16);

    protected SplitterBlock(Properties properties) {
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

    /** How far an item on this splitter's deck moves in one tick, in {@link Belts#UNITS_PER_BLOCK}ths. */
    public abstract int speed();

    /** The Factorio entity this is, so {@code check_models.py} can hold the speed to the wiki. */
    public abstract String factorioId();

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
        double step = speed() / (double) Belts.UNITS_PER_BLOCK;

        boolean standing = entity.onGround();
        entity.move(MoverType.SELF, new Vec3(travel.getStepX() * step, 0.0, travel.getStepZ() * step));
        entity.setOnGround(standing);
    }
}
