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
 * The block half of an inserter: which way it points, how far it reaches, and - the part that
 * matters - how it hears that there is work. What runs it, and what right-clicking it does,
 * belong to the subclass: the burner has a fuel slot and so opens a screen, and the electric one
 * has nothing to hold.
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
 *
 * <h2>And it only travels one block, which is why {@link #reach()} lives here</h2>
 *
 * <p>{@code updateNeighbourForOutputSignal} walks the six blocks touching the one that changed and
 * stops. A chest <em>two</em> blocks away says nothing to this position at all, so an inserter
 * that reaches two cannot be woken by either of its own ends. That is the long-handed inserter's
 * real cost and {@link InserterBlockEntity} pays it with a slow re-check; the block's part is
 * only to say how far this tier reaches.
 *
 * <p>Reach is a fact about the block rather than about the block entity, and that is not a
 * preference: the filter below has to know how far this inserter reaches <em>before</em> it looks
 * a block entity up, and looking one up to find out would be the lookup the filter exists to
 * avoid. A method on a subclass returning a constant is also safe from the trap
 * {@code createBlockStateDefinition} sets - see the silent-failures list in docs/NEXT.md - in a
 * way a field would not be.
 */
public abstract class InserterBlock extends BaseEntityBlock {

    /**
     * Where the items go. It takes from the block directly behind and gives to the block directly
     * in front, which is Factorio's arrangement.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * How many blocks away this tier takes from and gives to, on both sides at once.
     *
     * <p>One for every inserter Factorio powers with coal or with the basic electric arm; two for
     * the long-handed one, which reaches straight <em>over</em> whatever is in between and does
     * not care what that is. Nothing here looks at the block between, because a capability query
     * at a position does not have to travel there.
     *
     * <p>Behaviour rather than identity - the id and the recipe are the identity, and those are
     * generated - so a tier is free to change this number. It is on the block rather than the
     * block entity for the reason the class comment gives.
     */
    public int reach() {
        return 1;
    }

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
     * <p>Filtered down to the two positions an inserter can possibly care about before anything
     * is looked up. This notification arrives for all six sides, and in a real base most of them
     * are other machines saving themselves for reasons that have nothing to do with this block -
     * an inserter sandwiched between working machines would otherwise wake several times a tick,
     * every tick, to discover each time that it has nothing to do. Two position comparisons
     * against a block state already in hand is much cheaper than the block entity lookup they
     * avoid.
     *
     * <p><b>The two positions move with {@link #reach()}, and for a reach of two they move out of
     * earshot.</b> Nothing ever notifies a position two blocks away, so this method rejects
     * everything it is handed for a long-handed inserter - which is right, and is why the
     * bystander test has a long-handed twin: a filter that widened to match the reach would be a
     * filter that had stopped filtering, waking the inserter for six neighbours it can neither
     * take from nor give to. What actually wakes a long-handed inserter is in
     * {@link InserterBlockEntity}.
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
