package com.jaguarm.nauvisfluids.offshorepump;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.registry.ModTags;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
 * <p>{@link #intake} looks at the four blocks the intake reaches - under it and to its three open
 * sides - for a fluid in {@code #nauvis_fluids:offshore_pumpable}, which is the still water of a
 * lake or the sea and nothing a bucket poured. The still part matters: the flowing skirt where a
 * lake spills into a dug channel is water on the move, and a channel does not bring the sea
 * inland. The other water the search finds - a puddle, a flow - is remembered, so the readout
 * can say <em>this water cannot be pumped</em> rather than <em>no water</em>, which is the
 * difference between a player learning the rule and a player filing a bug.
 *
 * <h2>Facing</h2>
 *
 * <p>The player faces the water and places; the body lands on the block they clicked and the
 * intake one block ahead, over the water, with the outlet at the body's back. Turn round to turn
 * the machine.
 */
public class OffshorePumpBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<OffshorePumpBlock> CODEC = simpleCodec(OffshorePumpBlock::new);

    /** Which way the intake points: towards the water. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

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
     * Body on the clicked block, intake ahead of the player and at natural water - or null, and
     * so no placement at all, when the machine does not fit or the intake would find no water it
     * can draw.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockState placed = Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, facing), context);
        if (placed == null) {
            return null;
        }
        BlockPos anchor = shape().anchorPos(context.getClickedPos(), Multiblock.part(this, placed), facing);
        return intake(context.getLevel(), anchor, facing) == Intake.NATURAL ? placed : null;
    }

    /**
     * What the intake of a pump anchored at {@code anchor} and turned this way would draw from.
     *
     * <p>Under the intake first, then ahead and to either side: a pump on a beach reaches down
     * into the shallows, and one standing in the water reaches sideways into it. Never back
     * towards the body, which is where the shore is.
     */
    public static Intake intake(LevelReader level, BlockPos anchor, Direction facing) {
        BlockPos inlet = OffshorePumpShape.SHAPE.cellPos(anchor, OffshorePumpShape.INLET_CELL, facing);
        boolean other = false;
        for (BlockPos reach : List.of(inlet.below(), inlet.relative(facing),
                inlet.relative(facing.getClockWise()), inlet.relative(facing.getCounterClockWise()))) {
            FluidState fluid = level.getFluidState(reach);
            if (fluid.is(ModTags.OFFSHORE_PUMPABLE)) {
                return Intake.NATURAL;
            }
            if (fluid.is(FluidTags.WATER)) {
                other = true;
            }
        }
        return other ? Intake.OTHER : Intake.NONE;
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
