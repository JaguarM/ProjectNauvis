package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismining.machine.MachineTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A mining drill's blocks: four of them under a burner, nine under an electric, one of which
 * holds the machine.
 *
 * <p>The facing is not decoration. A drill puts what it mines down in front of its output head,
 * and the head is on the side the drill faces - so a row of drills all pointed at the same belt
 * puts every output on that belt, which is the whole of how a mining line is laid out.
 */
public abstract class MinerBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    /** Lit while the machine is actually working, so the model can show an active face. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Which way the drill's face - and so its output - points. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final MachineTier tier;

    protected MinerBlock(Properties properties, MachineTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(getStateDefinition().any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(shape().part(), shape().anchor()));
    }

    public MachineTier tier() {
        return tier;
    }

    /**
     * How much room this drill takes: two tiles by two for a burner, three by three for an
     * electric, which is what Factorio made them.
     */
    @Override
    public abstract MachineShape shape();

    /** The cell the ore comes out of: the block in front of it is where the ore goes. */
    public abstract int outputCell();

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    /** The block in front of the output head, in world coordinates: a belt or a chest goes here. */
    public BlockPos outputPos(BlockState state, BlockPos anchor) {
        Direction facing = facing(state);
        return shape().cellPos(anchor, outputCell(), facing).relative(facing);
    }

    /**
     * The columns a drill in this state, anchored here, mines: its footprint and the tier's reach
     * around it. Pure, so the client draws the same area the server walks.
     */
    public static DigArea digArea(BlockState state, BlockPos anchor) {
        MinerBlock drill = (MinerBlock) state.getBlock();
        return DigArea.of(drill.shape(), anchor, drill.facing(state), drill.tier().reach());
    }

    /**
     * Runs from {@code Block}'s constructor, so it may only touch things that do not depend on
     * this instance being finished. {@link #shape()} is a constant on a subclass, which is why it
     * is safe here and why a field would not have been.
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING, shape().part());
    }

    /**
     * Faces the player who placed it, the way a furnace does. Null - and so no placement at all -
     * unless the whole drill fits and nobody is standing in it.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this,
                defaultBlockState().setValue(FACING, placementFacing(context)), context);
    }

    /**
     * The front looks back at whoever placed it, so the output lands at the player's feet: stand
     * where the belt goes, place the drill, and it faces the belt.
     */
    @Override
    public Direction placementFacing(BlockPlaceContext context) {
        return context.getHorizontalDirection().getOpposite();
    }

    /** Only the anchor has one; the rest of the drill is structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new MinerBlockEntity(pos, state) : null;
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

    /** Puts the rest of the drill in, and remembers the placer so it mines as them. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        Multiblock.setPlacedBy(this, level, pos, state);
        if (level.getBlockEntity(Multiblock.anchorPos(this, state, pos))
                instanceof MinerBlockEntity miner) {
            miner.setOwner(placer);
        }
    }

    /**
     * The whole teardown, in one rule: a block whose neighbours are not the drill's other blocks
     * stops existing. Break any of them and the rest follow, and the anchor's loot table is what
     * gives the player their drill back.
     */
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

    /** Creative would otherwise hand back a free drill. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override on purpose: the machine schedules its own ticks while it has work
    // and stops when it has none. See MinerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MinerBlockEntity miner) {
            miner.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up, scheduled on the anchor whichever cell heard about it:
     * a chest put down in front of the head, a pole reaching the edge of the deck.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!level.getBlockTicks().hasScheduledTick(anchor, this)) {
            level.scheduleTick(anchor, this, 1);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        // Whichever block of the drill was clicked, the machine is at the anchor - and the
        // anchor's position travels with the menu, for the area preview and for the screen to
        // read what is being mined off the block entity there.
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!(level.getBlockEntity(anchor) instanceof MinerBlockEntity miner)) {
            return InteractionResult.PASS;
        }
        player.openMenu(miner, anchor);
        return InteractionResult.SUCCESS;
    }

    // Contents spill from MinerBlockEntity#preRemoveSideEffects, not from here: by the time
    // affectNeighborsAfterRemoval runs the block entity is already gone.
}
