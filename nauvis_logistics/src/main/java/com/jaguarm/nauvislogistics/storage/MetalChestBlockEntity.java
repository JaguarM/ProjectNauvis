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

/**
 * A box, in whichever size the block it belongs to asks for.
 *
 * <p>Vanilla's {@link ChestBlockEntity} with three things changed: the size, the screen that size
 * needs, and the name. Everything else a chest does - the lid animation and the openers counter
 * that drives it, the loot table dance, the sounds, saving and loading - is inherited, which is
 * the whole reason this extends it rather than {@code RandomizableContainerBlockEntity} directly.
 * Being a plain {@link net.minecraft.world.Container} also buys an item handler that inserters
 * already know how to use, through the capability registered in {@code ModCapabilities}.
 *
 * <p><b>The item list has to be replaced in the constructor.</b> {@code ChestBlockEntity}'s field
 * initialiser makes 27 slots, and only {@code loadAdditional} resizes it to whatever
 * {@code getContainerSize} says - so a chest that was placed rather than loaded would report
 * thirty-six slots while holding twenty-seven, and the screen would read past the end of the list
 * the moment a player opened it.
 *
 * <p>The size comes from the block rather than from a field on this class, so a tier is a
 * {@link MetalChestBlock} subclass and nothing here changes. Both tiers land on a row count
 * vanilla already has a screen for - four and six - which is the whole reason the slot counts are
 * 36 and 54 rather than Factorio's 32 and 48.
 */
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
