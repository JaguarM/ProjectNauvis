package com.jaguarm.nauvispower.generator;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The block half of the boiler. Right-click it with coal to fuel it, empty-handed to ask how it
 * is doing.
 *
 * <p>No screen yet. A boiler has one slot and two numbers, and the honest thing while there is no
 * art is a status line rather than a panel with one well in it - the assembler earned a screen
 * because it has a recipe to choose and six slots to watch.
 */
public class BoilerBlock extends BaseEntityBlock {

    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    public BoilerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    // No getTicker override, deliberately. See BoilerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            boiler.serverTick(level);
        }
    }

    /** An engine placed or broken beside it changes whether there is any point burning coal. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            boiler.wake();
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        Component name = stack.getHoverName();
        if (stack.getBurnTime(null, serverLevel.fuelValues()) <= 0) {
            player.sendOverlayMessage(Component.translatable("nauvis_power.boiler.not_fuel", name));
            return InteractionResult.SUCCESS;
        }

        int taken;
        try (Transaction transaction = Transaction.openRoot()) {
            taken = boiler.fuelAccess().insert(ItemResource.of(stack), stack.getCount(), transaction);
            if (taken > 0) {
                transaction.commit();
            }
        }
        if (taken > 0 && !player.hasInfiniteMaterials()) {
            stack.shrink(taken);
        }
        player.sendOverlayMessage(Component.translatable(
                taken > 0 ? "nauvis_power.boiler.fuelled" : "nauvis_power.boiler.full", name));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler)) {
            return InteractionResult.PASS;
        }

        player.sendOverlayMessage(Component.translatable("nauvis_power.boiler.status",
                boiler.steam(), BoilerBlockEntity.STEAM_CAPACITY));
        return InteractionResult.SUCCESS;
    }

    // Fuel is spilled from BoilerBlockEntity#preRemoveSideEffects, not from here.
}
