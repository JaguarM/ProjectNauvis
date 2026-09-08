package com.jaguarm.nauvislogistics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A box, in whichever size the block it belongs to asks for. */
public class MetalChestBlockEntity extends ChestBlockEntity {

    private final int rows;

    /**
     * Both the type and the size come from the block, which is what lets one constructor serve
     * every tier: {@code BlockEntityType}'s supplier is handed the state, and the state names the
     * block that knows which of the two it is.
     */
    public MetalChestBlockEntity(BlockPos pos, BlockState state) {
        super(((MetalChestBlock) state.getBlock()).blockEntityType(), pos, state);
        this.rows = ((MetalChestBlock) state.getBlock()).rows();
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return rows * 9;
    }

    /** The block's own name, so a tier does not have to remember to add a translation key. */
    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        MenuType<ChestMenu> type = rows == SteelChestBlock.ROWS
                ? MenuType.GENERIC_9x6
                : MenuType.GENERIC_9x4;
        return new ChestMenu(type, containerId, inventory, this, rows);
    }
}
