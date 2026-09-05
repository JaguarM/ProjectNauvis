package com.jaguarm.nauvispower.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The block half of a multi-block machine: how it goes in, how it comes out, and how any cell
 * finds the one that holds the machine.
 *
 * <p>Static helpers rather than a base class, because the blocks that need this already extend
 * {@code BaseEntityBlock} and a second hierarchy would be one more thing to keep in step. A block
 * implements {@link MachineBlock}, which is two methods, and forwards its overrides here.
 *
 * <p>All of it is {@code ElectricPoleBlock} with two more axes. Read that first if this is
 * unfamiliar: it is the same four rules, and it is four blocks rather than ten so the shape of
 * the idea is easier to see there.
 *
 * <h2>The whole teardown, in one rule</h2>
 *
 * <p>{@link #updateShape} turns a cell to air the moment a cell it touches is not the cell it
 * should be. Break any block of a machine and its neighbours notice, turn to air, and the cascade
 * crosses the footprint - which is why {@link MachineShape} refuses a set of cells that is not
 * connected. One rule covers every way a block can vanish, including the ones nobody thought to
 * handle: broken, exploded, {@code /setblock}, another mod.
 *
 * <p>Turning to air rather than calling {@code removeBlock} is the load-bearing detail. The
 * neighbour-update machinery routes an air result through {@code Block.updateOrDestroy}, which
 * <em>destroys</em> the block with drops enabled - so the anchor's loot table is what hands the
 * player their machine back, whichever cell they actually hit.
 *
 * <p>Placement does not trip over its own rule, and it is worth knowing why: {@code updateShape}
 * runs on a block's <em>neighbours</em> when it changes, never on the block being placed. So each
 * cell laid down asks the cells already there whether it is what they expected, and the cells not
 * placed yet are air that says nothing.
 */
public final class Multiblock {

    private Multiblock() {}

    /** What {@link Multiblock} needs to know about a block to do any of this. */
    public interface MachineBlock {

        MachineShape shape();

        /**
         * Which way this machine is turned. A machine with no facing property returns north and
         * never thinks about it again - which is most of them, because a Factorio assembler has
         * no direction: what goes in and what comes out is decided by the inserters around it.
         */
        default Direction facing(BlockState state) {
            return Direction.NORTH;
        }
    }

    /** Which cell of its machine this block is. */
    public static int part(MachineBlock block, BlockState state) {
        return state.getValue(block.shape().part());
    }

    /**
     * Where the machine this block belongs to keeps its block entity.
     *
     * <p>Arithmetic, not a search. Safe to call from anywhere, including a client thread and a
     * capability lookup, because it reads nothing.
     */
    public static BlockPos anchorPos(MachineBlock block, BlockState state, BlockPos pos) {
        MachineShape shape = block.shape();
        return shape.anchorPos(pos, part(block, state), block.facing(state));
    }

    /** True for the one cell that carries the block entity, the loot table and the menu. */
    public static boolean isAnchor(MachineBlock block, BlockState state) {
        return part(block, state) == block.shape().anchor();
    }

    /**
     * The state to put at the clicked block, or null - and so no placement at all - unless the
     * whole machine fits.
     *
     * <p>Three ways it does not fit, and the third is the one a one-block machine never had to
     * think about: a cell would land in something that will not give way, a cell would land
     * outside the world, or <b>the player is standing where the machine is about to be</b>. A
     * 3x3 placed at your feet would otherwise seal you inside it.
     */
    public static @Nullable BlockState getStateForPlacement(MachineBlock block,
            BlockState base, BlockPlaceContext context) {
        return getStateForPlacement(block, base, context, block.shape().placement());
    }

    /**
     * The same, with a chosen cell landing on the clicked block instead of the shape's usual one.
     *
     * <p>For a machine that snaps to something in the world. A pumpjack has to stand centred over
     * an oil well, and a player who clicks the block beside the well meant the well: the block
     * works out which of its cells the click should become so that the centre lands where it must,
     * and hands that cell in here. Everything about fitting is unchanged - only which cell is
     * pinned to the cursor.
     */
    public static @Nullable BlockState getStateForPlacement(MachineBlock block,
            BlockState base, BlockPlaceContext context, int partAtClick) {
        MachineShape shape = block.shape();
        Level level = context.getLevel();
        Direction facing = block.facing(base);

        BlockState placed = base.setValue(shape.part(), partAtClick);
        BlockPos anchor = shape.anchorPos(context.getClickedPos(), partAtClick, facing);

        for (int index = 0; index < shape.cellCount(); index++) {
            BlockPos pos = shape.cellPos(anchor, index, facing);
            if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
                return null;
            }
            // The clicked block is already known replaceable - the game would not be asking
            // otherwise - but every other cell has to be checked, and checking all of them is
            // simpler than checking all but one.
            if (!level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
            BlockState cellState = placed.setValue(shape.part(), index);
            if (!level.isUnobstructed(cellState, pos, CollisionContext.empty())) {
                return null;
            }
        }
        return placed;
    }

    /** Puts the rest of the machine in, once the game has placed the cell that was clicked. */
    public static void setPlacedBy(MachineBlock block, Level level, BlockPos pos, BlockState state) {
        MachineShape shape = block.shape();
        Direction facing = block.facing(state);
        int placed = part(block, state);
        BlockPos anchor = shape.anchorPos(pos, placed, facing);

        for (int index = 0; index < shape.cellCount(); index++) {
            if (index == placed) {
                continue;
            }
            level.setBlockAndUpdate(shape.cellPos(anchor, index, facing),
                    state.setValue(shape.part(), index));
        }
    }

    /**
     * The teardown. A cell whose neighbours are not its machine's other cells stops existing.
     *
     * <p>The neighbour has to be the same block, the same facing <em>and</em> part of the same
     * machine. That last clause is what keeps two assemblers placed side by side from holding
     * each other up: the cell across the seam is a real cell of a real machine, just not this
     * one, and comparing anchor positions is how that is told apart.
     */
    public static BlockState updateShape(MachineBlock block, BlockState state, LevelReader level,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState) {
        MachineShape shape = block.shape();
        Direction facing = block.facing(state);
        BlockPos anchor = anchorPos(block, state, pos);

        int expected = cellAt(shape, anchor, facing, neighbourPos);
        if (expected < 0) {
            return state;  // nothing of this machine belongs there
        }
        if (neighbourState.getBlock() != state.getBlock()
                || neighbourState.getValue(shape.part()) != expected
                || !anchorPos(block, neighbourState, neighbourPos).equals(anchor)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** Which cell of the machine anchored here sits at this position, or -1 for none. */
    private static int cellAt(MachineShape shape, BlockPos anchor, Direction facing, BlockPos pos) {
        for (int index = 0; index < shape.cellCount(); index++) {
            if (shape.cellPos(anchor, index, facing).equals(pos)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * In creative, take the anchor out silently before the player's own break is processed.
     *
     * <p>Without this a creative player breaking any other cell gets a free machine: the teardown
     * destroys the anchor with drops enabled, and creative only suppresses the drop from the
     * block the player actually hit. Flag 32 is {@code UPDATE_SUPPRESS_DROPS}. This is
     * {@code DoublePlantBlock#preventDropFromBottomPart}, over a footprint.
     */
    public static void preventDropFromAnchor(MachineBlock block, Level level, BlockPos pos,
            BlockState state, Player player) {
        if (level.isClientSide() || !player.isCreative() || isAnchor(block, state)) {
            return;
        }
        BlockPos anchor = anchorPos(block, state, pos);
        BlockState anchorState = level.getBlockState(anchor);
        if (anchorState.getBlock() == state.getBlock() && isAnchor(block, anchorState)) {
            level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, anchor, Block.getId(anchorState));
        }
    }

    /**
     * Places a whole machine at once, anchor first.
     *
     * <p>For a gametest, a structure, or anything else that is not a player with an item. The
     * anchor goes in first so that every other cell finds it already there, which is the same
     * order {@link #setPlacedBy} ends up using.
     */
    public static void place(MachineBlock block, LevelAccessor level, BlockPos anchor,
            BlockState state) {
        MachineShape shape = block.shape();
        Direction facing = block.facing(state);
        level.setBlock(shape.cellPos(anchor, shape.anchor(), facing),
                state.setValue(shape.part(), shape.anchor()), Block.UPDATE_ALL);
        for (int index = 0; index < shape.cellCount(); index++) {
            if (index == shape.anchor()) {
                continue;
            }
            level.setBlock(shape.cellPos(anchor, index, facing),
                    state.setValue(shape.part(), index), Block.UPDATE_ALL);
        }
    }
}
