package com.jaguarm.nauvislogistics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of a chest. There is very little of it, which is the point.
 *
 * <p>No ticking - a chest has nothing to do - so no scheduled ticks and nothing to wake. Nothing
 * spills its contents either: {@code BlockEntity#preRemoveSideEffects} already drops the contents
 * of anything that is a {@link net.minecraft.world.Container}, which this is. That is the same
 * hook the assembler and the inserter had to override by hand, and the reason they had to is that
 * their inventories are capability handlers rather than Containers.
 *
 * <p>A tier is two numbers - how many rows, and which block entity type saves them - so the tiers
 * are subclasses of this rather than copies of it. The rows have to be a constant on a subclass
 * and not a constructor argument, because {@code createBlockStateDefinition} runs inside
 * {@link net.minecraft.world.level.block.Block}'s constructor and would read a field that does not
 * exist yet; see PITFALLS.md. Unlike the inserters, the two chests need a block entity type each
 * rather than sharing one, because what differs between them is the size of what is saved.
 */
public abstract class MetalChestBlock extends BaseEntityBlock {

    protected MetalChestBlock(Properties properties) {
        super(properties);
    }

    /** Rows of nine on the screen, which is also the container size divided by nine. */
    public abstract int rows();

    protected abstract BlockEntityType<MetalChestBlockEntity> type();

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }
}
