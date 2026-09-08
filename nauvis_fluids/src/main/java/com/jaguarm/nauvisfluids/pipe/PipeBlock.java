package com.jaguarm.nauvisfluids.pipe;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/** A length of pipe, which now actually carries something. */
public class PipeBlock extends BaseEntityBlock {

    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public static final Map<Direction, BooleanProperty> CONNECTIONS = createConnections();

    /** The middle of the pipe, always there. Four pixels, so an arm can be seen leaving it. */
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    private static final Map<Direction, VoxelShape> ARMS = createArms();

    public PipeBlock(Properties properties) {
        super(properties);
        BlockState state = getStateDefinition().any();
        for (BooleanProperty connection : CONNECTIONS.values()) {
            state = state.setValue(connection, false);
        }
        registerDefaultState(state);
    }

    private static Map<Direction, BooleanProperty> createConnections() {
        Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, NORTH);
        map.put(Direction.EAST, EAST);
        map.put(Direction.SOUTH, SOUTH);
        map.put(Direction.WEST, WEST);
        map.put(Direction.UP, UP);
        map.put(Direction.DOWN, DOWN);
        return Map.copyOf(map);
    }

    private static Map<Direction, VoxelShape> createArms() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
        map.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
        map.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
        map.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
        map.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
        map.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
        return Map.copyOf(map);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    // No getTicker override, deliberately. The run ticks; see FluidNetwork.

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    /**
     * Re-reads the connection on one face when the block there changes.
     *
     * <p>Cheaper than rebuilding all six, and it is the only face that can have changed.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        return state.setValue(CONNECTIONS.get(direction), connects(level, neighbourPos, direction));
    }

    /**
     * A neighbouring block changed, which may mean a machine to draw from appeared or went.
     *
     * <p>This is the whole of what the electric grid needs a level-wide hook for. A pipe connects
     * to the six blocks it touches, and this notification covers exactly those six.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            FluidNetworkManager.of(serverLevel).neighbourChanged(pos);
        }
    }

    /** A neighbouring block <em>entity</em> changed - a boiler that now has steam to give. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level instanceof ServerLevel serverLevel) {
            FluidNetworkManager.of(serverLevel).neighbourChanged(pos);
        }
    }

    private static BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        BlockState result = state;
        for (Direction side : Direction.values()) {
            result = result.setValue(CONNECTIONS.get(side),
                    connects(level, pos.relative(side), side));
        }
        return result;
    }

    /**
     * Whether a pipe should reach towards what is at {@code neighbourPos}.
     *
     * <p>Another pipe always. Anything else only if it actually offers fluid on the face being
     * touched - which is what makes a steam engine's flank stay unconnected while its two ends
     * connect.
     */
    private static boolean connects(LevelReader level, BlockPos neighbourPos, Direction side) {
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof PipeBlock) {
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            // The client is told the connection through the block state, so it never has to ask.
            return false;
        }
        return serverLevel.getCapability(Capabilities.Fluid.BLOCK, neighbourPos, side.getOpposite()) != null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(side))) {
                shape = Shapes.or(shape, ARMS.get(side));
            }
        }
        return shape;
    }
}
