package com.jaguarm.nauvislogistics.transport;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * The block half of an inserter: which way it points and - the part that matters - how it hears
 * that there is work. What runs it, and what right-clicking it does, belong to the subclass: the
 * burner has a fuel slot and so opens a screen, and the electric one has nothing to hold.
 *
 * <h2>onNeighborChange is the whole trick</h2>
 *
 * <p>An inserter's work appears in a neighbour's inventory, and neighbours do not know it exists.
 * The obvious answers are both bad: tick forever like a hopper, or poll on a timer. Neither is
 * survivable at Factorio scale, where a base is thousands of inserters and nearly all of them are
 * waiting.
 *
 * <p>{@link #onNeighborChange} is the answer, and it is free. Every {@code BlockEntity.setChanged()}
 * calls {@code Level.updateNeighbourForOutputSignal}, which NeoForge widened from vanilla's
 * horizontal-comparator check to notify all six neighbours unconditionally. A chest gaining an
 * item, a furnace finishing, an assembler banking a craft - each already calls {@code setChanged},
 * so each already tells this block, exactly and immediately. All that is left is to turn the
 * notification into a scheduled tick.
 *
 * <p>It fires more often than is strictly needed: any block entity beside this one being saved
 * for any reason arrives here. That is fine, because waking costs one tick that finds nothing and
 * goes straight back to sleep, and because the alternative costs one tick <em>every</em> tick.
 */
public abstract class InserterBlock extends BaseEntityBlock {

    /**
     * Where the items go. It takes from the block directly behind and gives to the block directly
     * in front, which is Factorio's arrangement.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected InserterBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
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
     *
     * <p>Filtered down to the two neighbours an inserter can possibly care about before anything
     * is looked up. This notification arrives for all six sides, and in a real base most of them
     * are other machines saving themselves for reasons that have nothing to do with this block -
     * an inserter sandwiched between working machines would otherwise wake several times a tick,
     * every tick, to discover each time that it has nothing to do. Two position comparisons
     * against a block state already in hand is much cheaper than the block entity lookup they
     * avoid.
     */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);

        Direction facing = state.getValue(FACING);
        if (!neighbor.equals(pos.relative(facing)) && !neighbor.equals(pos.relative(facing.getOpposite()))) {
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
