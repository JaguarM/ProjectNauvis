package com.jaguarm.nauvislogistics.belt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A belt block: which way it carries, and how fast.
 *
 * <p>It holds nothing and does nothing. <b>The run holds the items and the run ticks</b> - see
 * {@link BeltRun} - and a belt block is a fact about where the run goes. This is the third time
 * this pack has drawn that line, after {@code PowerNetwork} and {@code FluidNetwork}, and it
 * matters most here: a belt that were a block entity passing items to the next block would cost a
 * tick per block per second and take a tick per block to move anything, which is the difference
 * between a belt and a bucket chain.
 *
 * <h2>Speed is a subclass, not a field</h2>
 *
 * <p>Every tier is its own class with its own constant, rather than one class with a speed field.
 * That is not taste: {@code createBlockStateDefinition} runs inside {@code Block}'s constructor,
 * before any field of a subclass exists, and the drills already shipped one bug from reading a
 * field there. A constant on a subclass exists long before any block does. See
 * {@code docs/NEXT.md}.
 *
 * <p>It also settles what happens where two tiers meet: a run only continues through belts of the
 * same block, so a fast belt after a normal one is a second run that the first hands off into,
 * which is what Factorio does with its transport lines.
 *
 * <h2>Half a block high, and you walk over it</h2>
 *
 * <p>{@link Belts#HEIGHT} is 0.5, under vanilla's 0.6 step height, so crossing a belt is walking
 * rather than jumping. Collision and silhouette agree exactly here, which for something meant to
 * be walked across is the whole point.
 */
public abstract class BeltBlock extends BaseEntityBlock {

    /**
     * Which way items travel. A belt faces the way the player is looking when it is placed, like
     * an inserter and unlike a furnace: a belt is something you point, not something you look at.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE =
            Block.box(0, 0, 0, 16, Belts.HEIGHT * 16, 16);

    protected BeltBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    /** How far an item on this belt moves in one tick, in {@link Belts#UNITS_PER_BLOCK}ths. */
    public abstract int speed();

    /** The Factorio entity this is, so {@code check_models.py} can hold the speed to the wiki. */
    public abstract String factorioId();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    // No getTicker override, deliberately. The run ticks; see BeltRun.

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // Nothing wakes a belt from a neighbour, and nothing needs to. A run is awake exactly while it
    // has something on it: an inserter putting an item on wakes it through the capability, and a
    // run that empties drops out of the active set by itself. A jammed run stays awake and costs
    // almost nothing, because BeltLane makes a jam free rather than expensive.
    //
    // Nor is there a hook for a neighbouring belt appearing: that belt's own block entity joins
    // the graph when it loads, and joining rebuilds the runs around it. See BeltLines.
}
