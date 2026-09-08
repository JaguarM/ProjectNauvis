package com.jaguarm.nauvislogistics.storage;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block half of a chest: vanilla's {@link ChestBlock}, with a bigger box inside it.
 */
public abstract class MetalChestBlock extends ChestBlock {

    protected MetalChestBlock(Supplier<BlockEntityType<? extends ChestBlockEntity>> type,
            Properties properties) {
        // The copper chest's sounds rather than the wooden chest's, because these are metal. Both
        // are vanilla sound events; a mod that shipped its own would be shipping audio to say the
        // same thing.
        super(type, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, properties);
    }

    /** Rows of nine on the screen, which is also the container size divided by nine. */
    public abstract int rows();

    /** Never. See the class comment: a pair would want a screen that does not exist. */
    @Override
    public boolean chestCanConnectTo(BlockState state) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalChestBlockEntity(pos, state);
    }
}
