package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlock;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of the pumpjack: ten blocks, one of which holds anything.
 *
 * <p>{@link PumpjackShape} is the footprint and the geometry; {@link Multiblock} is everything
 * about being made of several blocks. What is this machine's own is <b>where it may stand</b>:
 * centred on an oil well and nowhere else, which is Factorio's rule to the letter - a pumpjack
 * can only be placed on a crude oil resource.
 *
 * <h2>Snapping</h2>
 *
 * <p>Factorio's placement ghost snaps a pumpjack to the well under the cursor. Here the same
 * thing is done in {@link #getStateForPlacement}: whichever of the nine blocks over a well the
 * player clicks, the machine lands centred on the well. Click a block that is not over a well and
 * nothing is placed, exactly as Factorio refuses. {@link #snapPart} is the rule, and the client's
 * outline renderer asks it the same question so what is drawn is what will happen.
 *
 * <h2>Facing</h2>
 *
 * <p>The facing decides which corner the oil leaves by and which way the pipe points. A player
 * placing one gets the outlet on the far side from them, pointing away - the way a boiler's steam
 * leaves at its back - so the pipe runs off towards the refinery rather than back through their
 * legs. Turn round to turn the machine.
 */
public class PumpjackBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final MapCodec<PumpjackBlock> CODEC = simpleCodec(PumpjackBlock::new);

    /** Which way the outlet points. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PumpjackBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PumpjackShape.SHAPE.part(), PumpjackShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return PumpjackShape.SHAPE;
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
        builder.add(FACING, PumpjackShape.SHAPE.part());
    }

    /** Only the centre has one; the other nine are structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new PumpjackBlockEntity(pos, state) : null;
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
     * Centred over the well nearest the click, outlet pointing away from the player - or null,
     * and so no placement at all, when the click is not over a well or the machine does not fit.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        int part = snapPart(context.getLevel(), context.getClickedPos(), facing);
        if (part < 0) {
            return null;
        }
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, facing), context, part);
    }

    /**
     * Which cell of the machine should land on {@code clicked} so that its centre stands over a
     * well, or -1 if no cell can.
     *
     * <p>The centre is tried first, so a click exactly over a well is honoured as it stands; then
     * the other eight ground cells, which is what makes a click anywhere on the 3x3 over a well
     * land the machine on it. The pump cell above the centre is not a candidate: a click in the
     * air over a well is not how anyone places a machine, and answering it would put the ground
     * cells inside the ground.
     */
    public static int snapPart(LevelReader level, BlockPos clicked, Direction facing) {
        MachineShape shape = PumpjackShape.SHAPE;
        if (isWell(level, shape.anchorPos(clicked, shape.placement(), facing).below())) {
            return shape.placement();
        }
        for (int part = 0; part < shape.cellCount(); part++) {
            if (part == shape.placement() || shape.cell(part).y() != 0) {
                continue;
            }
            if (isWell(level, shape.anchorPos(clicked, part, facing).below())) {
                return part;
            }
        }
        return -1;
    }

    /** Whether there is an oil well at {@code pos}. */
    public static boolean isWell(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CrudeOilBlock;
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

    /** Creative would otherwise hand back a free pumpjack. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override, deliberately. See PumpjackBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof PumpjackBlockEntity pumpjack) {
            pumpjack.serverTick(level);
        }
    }

    /**
     * A pipe placed at the outlet, or the well under the middle going away, changes whether there
     * is any point running. Whichever of the ten blocks heard about it, the machine to wake is the
     * one with the block entity.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (level.getBlockEntity(anchor) instanceof PumpjackBlockEntity pumpjack) {
            pumpjack.wake();
        }
    }

    // No screen. Factorio's pumpjack has nothing to put in or take out; what it would show is
    // on the hover readout - yield, contents, and whether it is pumping.
}
