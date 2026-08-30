package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import com.jaguarm.nauvismining.multiblock.MachineShape;
import com.jaguarm.nauvismining.multiblock.Multiblock;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public abstract class MinerBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    /** Lit while the machine is actually working, so the model can show an active face. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Which way the drill's face points. Cosmetic: it digs straight down regardless. */
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
     *
     * <p><b>This is why there are two subclasses rather than one block with a tier field.</b> The
     * shape carries the {@code part} property, and {@link #createBlockStateDefinition} needs that
     * property - but it runs inside {@code Block}'s own constructor, before any field of this
     * class has been assigned. A tier read there is always null. So each drill answers with a
     * constant that belongs to its own class, which is set up long before any block exists.
     *
     * <p>The first attempt at this did use a field, guessed burner when it was null, and gave the
     * electric drill a five-value property and a nine-value default state. It failed at
     * registration with "cannot set property part", which is the good outcome; the same mistake
     * one property size later would simply have dropped states on the floor.
     */
    @Override
    public abstract MachineShape shape();

    @Override
    public Direction facing(BlockState state) {
        return state.getValue(FACING);
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
                defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()),
                context);
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

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MINER.get(), MinerBlockEntity::serverTick);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            // Whichever block of the drill was clicked, the machine is at the anchor - and the
            // anchor is the position the dig-area preview is drawn around, which is why the
            // electric drill keeps its block entity in the middle of its nine.
            BlockPos anchor = Multiblock.anchorPos(this, state, pos);
            if (level.getBlockEntity(anchor) instanceof MinerBlockEntity miner) {
                // Position variant: the client needs it to draw the dig-area preview.
                player.openMenu(miner, anchor);
            }
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Spill the machine's contents when it is broken, so tools and ore are not lost. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MinerBlockEntity miner) {
            Containers.dropContents(level, pos, miner.items());
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
