package com.jaguarm.nauvismachines.machine.assembler;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

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
 */
public class AssemblerBlock extends BaseEntityBlock {

    public static final MapCodec<AssemblerBlock> CODEC = simpleCodec(AssemblerBlock::new);

    public AssemblerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerBlockEntity(pos, state);
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
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler)) {
            return InteractionResult.PASS;
        }

        // The position travels with the menu: the screen reads the chosen recipe off the block
        // entity, because a recipe key cannot be a data slot.
        player.openMenu(assembler, pos);
        return InteractionResult.SUCCESS;
    }

    // Nothing spills its contents here. In 26.2 that is BlockEntity#preRemoveSideEffects, and
    // by the time affectNeighborsAfterRemoval runs the block entity is already gone - which
    // looks like working code and drops nothing. See AssemblerBlockEntity.
}
