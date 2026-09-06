package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvismachines.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of a furnace, whichever tier: placement, breaking, opening, and the fire.
 *
 * <p>Three furnaces share this - stone, steel and electric - and they are three classes because
 * a tier is a class: crafting speed, whether it burns fuel and what a tick of electricity costs
 * are constants on the subclass, read by the one block entity. The shape is a constant on the
 * subclass too, because {@code createBlockStateDefinition} runs inside {@code Block}'s
 * constructor and the two burner tiers are two by two where the electric one is three by three.
 *
 * <h2>Lit</h2>
 *
 * <p>Vanilla's {@code lit} property, on every cell, and the block entity sets it across the
 * machine when a smelt starts and clears it when the machine stops. Only the stack or the hood
 * draws it differently - its top turns to fire - but the property is on every cell because the
 * machine is one block id, and a furnace column that lights up is the thing that says which ones
 * are working from across a base. A lit furnace gives light, as vanilla's does.
 *
 * <p>Everything else is the assembler's arrangement: right-click any cell to open it, break any
 * cell to take it down, and an inserter against any cell reaches the same slots.
 */
public abstract class FurnaceBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Vanilla's furnace glow, for {@code Properties.lightLevel}. */
    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 13 : 0;
    }

    protected FurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(shape().part(), shape().anchor())
                .setValue(LIT, false));
    }

    // No facing. A Factorio furnace has no direction: what goes in and what comes out is decided
    // by the inserters around it.

    /** Factorio's {@code crafting_speed}: recipe-seconds this furnace gets through in a second. */
    public abstract float craftingSpeed();

    /** Whether this tier burns fuel. The other kind runs on electricity. */
    public abstract boolean isBurner();

    /** FE spent per tick of smelting by an electric tier; nothing for a burner. */
    public abstract int energyPerTick();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // shape() from inside Block's constructor is safe only because every subclass answers
        // with a static - see docs/PITFALLS.md on fields that do not exist yet.
        builder.add(shape().part(), LIT);
    }

    /** Only the anchor has one; the rest of the machine is structure. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new FurnaceBlockEntity(pos, state) : null;
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

    /** Null, and so no placement at all, unless the whole machine fits and nobody is standing there. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this, defaultBlockState(), context);
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

    /** Creative would otherwise hand back a free furnace. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override on purpose: the machine schedules its own ticks while it has work
    // and stops when it has none. See FurnaceBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace) {
            furnace.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up, scheduled on the anchor whichever cell heard about it.
     * A wire reaching an electric furnace may touch any of its twelve faces.
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
        BlockPos anchor = Multiblock.anchorPos(this, state, pos);
        if (!(level.getBlockEntity(anchor) instanceof FurnaceBlockEntity furnace)) {
            return InteractionResult.PASS;
        }
        // The anchor's position travels with the menu: the client reads which tier it is, and
        // what is being smelted, off the block and the block entity there.
        player.openMenu(furnace, anchor);
        return InteractionResult.SUCCESS;
    }

    // Contents spill from FurnaceBlockEntity#preRemoveSideEffects, not from here.
}
