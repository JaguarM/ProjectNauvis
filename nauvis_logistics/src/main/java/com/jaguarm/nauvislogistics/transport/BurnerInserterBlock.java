package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The burner inserter. Right-click it with coal to fuel it, empty-handed to ask how it is doing.
 *
 * <p>No screen: one slot and one number, and the honest thing while there is no art is a status
 * line rather than a panel with a single well in it.
 */
public class BurnerInserterBlock extends InserterBlock {

    public static final MapCodec<BurnerInserterBlock> CODEC = simpleCodec(BurnerInserterBlock::new);

    public BurnerInserterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerInserterBlockEntity(pos, state);
    }

    @Override
    protected Component status(BlockState state, InserterBlockEntity inserter) {
        return inserter.running()
                ? Component.translatable("nauvis_logistics.inserter.running",
                        Component.literal(state.getValue(FACING).getName()))
                : Component.translatable("nauvis_logistics.inserter.no_fuel");
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
        if (!(level.getBlockEntity(pos) instanceof BurnerInserterBlockEntity inserter)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        Component name = stack.getHoverName();
        if (stack.getBurnTime(null, serverLevel.fuelValues()) <= 0) {
            player.sendOverlayMessage(Component.translatable("nauvis_logistics.inserter.not_fuel", name));
            return InteractionResult.SUCCESS;
        }

        int taken;
        try (Transaction transaction = Transaction.openRoot()) {
            taken = inserter.fuelAccess().insert(ItemResource.of(stack), stack.getCount(), transaction);
            if (taken > 0) {
                transaction.commit();
            }
        }
        if (taken > 0 && !player.hasInfiniteMaterials()) {
            stack.shrink(taken);
        }
        player.sendOverlayMessage(Component.translatable(
                taken > 0 ? "nauvis_logistics.inserter.fuelled" : "nauvis_logistics.inserter.full", name));
        return InteractionResult.SUCCESS;
    }
}
