package com.jaguarm.nauvisfluids.offshorepump;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.registry.ModTags;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of the offshore pump: two blocks, one of which holds anything.
 *
 * <p>{@link OffshorePumpShape} is the footprint and the geometry; {@link Multiblock} is everything
 * about being made of several blocks. What is this machine's own is <b>where it may stand</b>:
 * with its intake at natural water and nowhere else, which is Factorio's rule to the letter - an
 * offshore pump can only be placed at water, and Factorio has no other kind.
 *
 * <h2>What counts as water</h2>
 *
 * <p>{@link #waterAt} looks at the blocks the intake reaches - under it, one and two down, so a
 * bank a block above the water still counts, and to its three open sides, so a pump standing in
 * the shallows reaches sideways - for a fluid in {@code #nauvis_fluids:offshore_pumpable}, which
 * is the still water of a lake or the sea and nothing a bucket poured. The still part matters:
 * the flowing skirt where a lake spills into a dug channel is water on the move, and a channel
 * does not bring the sea inland.
 *
 * <h2>It turns to the water, and it floats</h2>
 *
 * <p>Factorio's placement ghost snaps an offshore pump to the shoreline under the cursor, and a
 * pump here does the nearest thing a block can. {@link #aim} tries the way the player faces first
 * and then the other three, and the first whose intake finds natural water is the way the pump
 * goes - so a player walking along a beach places pumps that all look out to sea whichever way
 * they were looking. And a click on the lake itself, which lands on the lake bed because a block
 * in hand looks through water, is lifted by {@link OffshorePumpItem} to the air just over the
 * surface: the pump floats there, intake over the water, as Factorio 2.0's does.
 * {@code client/OffshorePumpGhost} draws the answer to the same two questions under the crosshair
 * while the pump is in hand, so what is shown is what will happen.
 *
 * <p>When no facing finds water the placement is refused, and the player is told why on the
 * action bar: that there is no water here, or - the case worth a sentence - that the water here is
 * a bucket's, which the pump does not draw from. That message is the whole of how a player learns
 * the rule.
 */
public class OffshorePumpBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<OffshorePumpBlock> CODEC = simpleCodec(OffshorePumpBlock::new);

    /** Which way the intake points: towards the water. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The two things a refused placement can say. */
    public static final String NO_WATER_KEY = "nauvis_fluids.offshore_pump.no_water";
    public static final String WRONG_WATER_KEY = "nauvis_fluids.offshore_pump.wrong_water";

    /** What the intake found. */
    public enum Intake {
        /** Still natural water: the machine may stand here and pump. */
        NATURAL,
        /** Water that is not pumpable - poured from a bucket, or flowing. */
        OTHER,
        /** Nothing wet at all. */
        NONE
    }

    public OffshorePumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OffshorePumpShape.SHAPE.part(), OffshorePumpShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return OffshorePumpShape.SHAPE;
    }

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OffshorePumpShape.SHAPE.part());
    }

    /** Only the body has one; the intake is structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new OffshorePumpBlockEntity(pos, state) : null;
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

    /**
     * Body on the clicked block - lifted to the surface already, if the click was in a lake -
     * turned so that its intake finds natural water, or null and a word on the action bar when
     * no turn does. Null too, and silently, when the machine simply does not fit.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos anchor = context.getClickedPos();
        Direction facing = aim(context.getLevel(), anchor, context.getHorizontalDirection());
        if (facing == null) {
            explain(context, anchor);
            return null;
        }
        return Multiblock.getStateForPlacement(this, defaultBlockState().setValue(FACING, facing), context);
    }

    /** Why a pump would not go here, to the player who tried, on the server that decided. */
    private static void explain(BlockPlaceContext context, BlockPos anchor) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            Intake best = bestIntake(context.getLevel(), anchor);
            player.sendOverlayMessage(Component.translatable(
                    best == Intake.OTHER ? WRONG_WATER_KEY : NO_WATER_KEY));
        }
    }

    /**
     * The way a pump anchored at {@code anchor} should face for its intake to find natural water:
     * the way the player faces if that works, then a quarter turn either way, then right round -
     * the nearest turn wins - or null if no way does.
     */
    public static @Nullable Direction aim(LevelReader level, BlockPos anchor, Direction preferred) {
        for (Direction facing : List.of(preferred, preferred.getClockWise(),
                preferred.getCounterClockWise(), preferred.getOpposite())) {
            if (waterAt(level, anchor, facing) != null) {
                return facing;
            }
        }
        return null;
    }

    /** The best any facing finds here: what a refusal should say. */
    public static Intake bestIntake(LevelReader level, BlockPos anchor) {
        Intake best = Intake.NONE;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Intake found = intake(level, anchor, facing);
            if (found == Intake.NATURAL) {
                return found;
            }
            if (found == Intake.OTHER) {
                best = found;
            }
        }
        return best;
    }

    /**
     * The block just above the surface of the natural water {@code pos} is in, or {@code pos}
     * itself when it is in none.
     *
     * <p>A block in hand looks through water, so a click on a lake lands on its bed, and a pump
     * placed there would sit on the bottom. Factorio 2.0's floats, and so does this one: the body
     * goes in the air over the top water block, and its intake hangs over the water beside it.
     */
    public static BlockPos afloat(LevelReader level, BlockPos pos) {
        if (!level.getFluidState(pos).is(ModTags.OFFSHORE_PUMPABLE)) {
            return pos;
        }
        BlockPos top = pos;
        while (level.getFluidState(top.above()).is(ModTags.OFFSHORE_PUMPABLE)) {
            top = top.above();
        }
        return top.above();
    }

    /**
     * The natural water the intake of a pump anchored at {@code anchor} and turned this way would
     * draw from, or null if it would find none.
     */
    public static @Nullable BlockPos waterAt(LevelReader level, BlockPos anchor, Direction facing) {
        for (BlockPos reached : reach(anchor, facing)) {
            if (level.getFluidState(reached).is(ModTags.OFFSHORE_PUMPABLE)) {
                return reached;
            }
        }
        return null;
    }

    /**
     * What the intake of a pump anchored at {@code anchor} and turned this way finds: natural
     * water, some other water, or nothing.
     */
    public static Intake intake(LevelReader level, BlockPos anchor, Direction facing) {
        if (waterAt(level, anchor, facing) != null) {
            return Intake.NATURAL;
        }
        for (BlockPos reached : reach(anchor, facing)) {
            FluidState fluid = level.getFluidState(reached);
            if (fluid.is(FluidTags.WATER)) {
                return Intake.OTHER;
            }
        }
        return Intake.NONE;
    }

    /**
     * The blocks an intake reaches: under it, one and two down, then ahead and to either side.
     * Never back towards the body, which is where the shore is.
     */
    private static List<BlockPos> reach(BlockPos anchor, Direction facing) {
        BlockPos inlet = OffshorePumpShape.SHAPE.cellPos(anchor, OffshorePumpShape.INLET_CELL, facing);
        return List.of(inlet.below(), inlet.below(2), inlet.relative(facing),
                inlet.relative(facing.getClockWise()), inlet.relative(facing.getCounterClockWise()));
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

    /** Creative would otherwise hand back a free pump. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately. See OffshorePumpBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof OffshorePumpBlockEntity pump) {
            pump.serverTick(level);
        }
    }

    /**
     * A pipe placed at the outlet, or the water at the intake going away or coming back, changes
     * whether there is any point running. Whichever of the two blocks heard about it, the machine
     * to wake is the one with the block entity.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof OffshorePumpBlockEntity pump) {
            pump.wake();
        }
    }

    // No screen. Factorio's offshore pump has nothing to put in or take out; what it would show
    // is on the hover readout - contents, and whether it is pumping.
}
