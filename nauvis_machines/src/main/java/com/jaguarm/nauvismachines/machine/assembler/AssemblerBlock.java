package com.jaguarm.nauvismachines.machine.assembler;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of the assembling machine: placement, breaking, and how a player tells it what
 * to make.
 *
 * <p>Right-click opens it, holding anything or nothing, exactly like a chest. Everything else is
 * done in the screen: ingredients go in its slots, and the recipe is chosen by clicking one in
 * Facrafting's panel beside it.
 *
 * <p>It used to be cleverer. Before there was a screen, clicking the block with an item pointed
 * the machine at that item's recipe, and clicking with an ingredient loaded it - the only way to
 * work a machine with no interface. With an interface those are two hidden rules that fire when a
 * player expects a container to open, so they are gone.
 *
 * <p>Building against the machine works the way it does for a chest or a furnace: sneak while
 * holding the block you are placing.
 *
 * <h2>It is ten blocks</h2>
 *
 * <p>Three tiles by three, the size Factorio made it - see {@link AssemblerShape} for the
 * footprint and for why the upper storey is mostly air. Everything about being a multi-block is
 * {@link Multiblock}'s, and it is {@code SmallElectricPoleBlock}'s four rules with two more axes:
 * refuse placement unless the whole machine fits, put the rest in from {@link #setPlacedBy}, let
 * one {@link #updateShape} rule be the entire teardown, and keep the block entity on one cell.
 *
 * <p>Which cell the player touched never matters. Clicking any of the ten opens the machine,
 * breaking any of the ten takes the machine down, and a hopper against any of the ten reaches the
 * same inventory - see {@code ModCapabilities}.
 */
public abstract class AssemblerBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    public AssemblerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(AssemblerShape.SHAPE.part(), AssemblerShape.SHAPE.anchor()));
    }

    @Override
    public MachineShape shape() {
        return AssemblerShape.SHAPE;
    }

    // No facing override. A Factorio assembler has no direction: what goes in and what comes out
    // is decided by the inserters around it, so the shape is only ever asked for its north frame
    // and the blockstate is a tenth the size it would otherwise be.

    /**
     * Factorio's {@code crafting_speed}: how many recipe-seconds this machine gets through in a
     * second. A recipe's craft time is the recipe's; what a machine makes of it is the machine's.
     *
     * <p>A constant on the subclass rather than a field, because {@code createBlockStateDefinition}
     * runs inside {@code Block}'s constructor before any field of a subclass exists - see
     * {@code docs/PITFALLS.md}. A tier is a class, exactly as a belt tier is.
     */
    public abstract float craftingSpeed();

    /** FE spent per tick of a craft, at the pack's ratio of 120 FE/t to Factorio's 900 kW engine. */
    public abstract int energyPerTick();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AssemblerShape.SHAPE.part());
    }

    /**
     * Only the middle has one. Nine block entities holding a pointer to a tenth would be nine
     * times the memory, in a base of thousands of machines, for a number the blockstate already
     * carries.
     */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new AssemblerBlockEntity(pos, state) : null;
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

    /** Null, and so no placement at all, unless all ten blocks fit and nobody is standing there. */
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

    /** Creative would otherwise hand back a free machine. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // No getTicker override on purpose. A ticker would run on every machine every tick whether
    // or not it has work; this block is driven by scheduled ticks it asks for itself, so an
    // idle machine costs nothing. See AssemblerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler) {
            assembler.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up. Nothing next door matters to the machine yet, but
     * power will, and a machine that only wakes on its own inventory would sleep through a
     * cable being connected.
     *
     * <p>The tick is scheduled on the <em>anchor</em>, whichever cell heard about the change.
     * There are twelve faces round a 3x3 machine and a wire may reach any of them; a cell that
     * kept the news to itself would schedule a tick on a block with no block entity, which does
     * nothing at all and leaves the machine asleep.
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
        if (!(level.getBlockEntity(anchor) instanceof AssemblerBlockEntity assembler)) {
            return InteractionResult.PASS;
        }

        // The position travels with the menu: the screen reads the chosen recipe off the block
        // entity, because a recipe key cannot be a data slot. It is the anchor's position, not
        // the corner the player happened to click.
        player.openMenu(assembler, anchor);
        return InteractionResult.SUCCESS;
    }

    // Nothing spills its contents here. In 26.2 that is BlockEntity#preRemoveSideEffects, and
    // by the time affectNeighborsAfterRemoval runs the block entity is already gone - which
    // looks like working code and drops nothing. See AssemblerBlockEntity.
}
