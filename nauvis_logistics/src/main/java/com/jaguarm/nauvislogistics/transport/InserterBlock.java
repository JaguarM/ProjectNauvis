package com.jaguarm.nauvislogistics.transport;

import java.util.EnumMap;
import java.util.Map;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.multiblock.Boxes;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of an inserter: which way it points, how far it reaches, and - the part that
 * matters - how it hears that there is work. What runs it, and what right-clicking it does,
 * belong to the subclass: the burner has a fuel slot and so opens a screen, and the electric
 * one has nothing to hold.
 */
public abstract class InserterBlock extends BaseEntityBlock {

    /**
     * Where the items go. It takes from the block directly behind and gives to the block directly
     * in front, which is Factorio's arrangement.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How many blocks away this tier takes from and gives to, on both sides at once. */
    public int reach() {
        return 1;
    }

    /**
     * Factorio's modifier for how many more items an ordinary inserter's hand holds.
     *
     * <p>Granted by the second level of {@code inserter-capacity-bonus} and nowhere else the pack
     * reaches, so an inserter moves one item at a time until then and two afterwards. The stack
     * inserter has a bonus of its own - see {@link StackInserterBlock}.
     */
    public static final String STACK_SIZE_BONUS = "inserter-stack-size-bonus";

    /**
     * How many items this tier's hand holds at once: one, plus what the world has researched.
     *
     * <p>Asked of the world through {@link Bonuses} rather than read off a field, because the
     * answer changes under a placed inserter the moment a technology finishes, and an inserter
     * that had to be rebuilt to grow its hand would be a bug a player could only find by rebuilding
     * one. Asked once a swing, at the moment the swing begins, which is when Factorio decides too.
     */
    public int handSize(ServerLevel level) {
        return 1 + Bonuses.count(level, STACK_SIZE_BONUS);
    }

    /**
     * What you bump into: the plate and post as one box, and the arm out to the front edge.
     *
     * <p>The same numbers {@code NauvisLogisticsModels} draws, turned the same way, so the thing
     * you see and the thing you hit are one object. In the model's own north-facing frame; a box
     * per facing is built once below.
     */
    private static final float[][] BOXES = {
        {2, 0, 2, 14, 9, 14},
        {5, 8, 0, 11, 12, 10},
    };

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (float[] box : Boxes.rotate(BOXES, Boxes.quarterTurns(facing))) {
                shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
            }
            SHAPES.put(facing, shape);
        }
    }

    protected InserterBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Faces the way the player is looking, so it throws away from you and takes from behind you.
     *
     * <p>The opposite of a furnace, which turns its face towards you. An inserter is not something
     * you look at, it is something you point.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    // No getTicker override, deliberately. See InserterBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof InserterBlockEntity inserter) {
            inserter.serverTick(level);
        }
    }

    /**
     * A neighbouring block entity changed - most usefully, a container's contents. This is the
     * signal that replaces polling; see the class comment.
     */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);

        Direction facing = state.getValue(FACING);
        int reach = reach();
        if (!neighbor.equals(pos.relative(facing, reach))
                && !neighbor.equals(pos.relative(facing.getOpposite(), reach))) {
            return;
        }
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof InserterBlockEntity inserter) {
            inserter.wake();
        }
    }

    /** A neighbouring <em>block</em> changed: a chest placed or broken beside it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.getBlockEntity(pos) instanceof InserterBlockEntity inserter) {
            inserter.wake();
        }
    }

    // Fuel is spilled from BurnerInserterBlockEntity#preRemoveSideEffects, not from here. See the
    // note there and in docs/API-26.2.md - the hook that looks right drops nothing.
}
