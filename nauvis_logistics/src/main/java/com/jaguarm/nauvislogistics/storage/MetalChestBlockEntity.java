package com.jaguarm.nauvislogistics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A box, in whichever size the block it belongs to asks for: the first containers in the pack that
 * are ours rather than vanilla's.
 *
 * <p>A plain {@link net.minecraft.world.Container}, unlike every other block in this pack, and
 * that is a deliberate choice rather than an oversight. A chest has no logic to speak of - it
 * holds things - and being a Container buys two whole features for nothing: vanilla's own chest
 * screens, and (through the capability registered in {@code ModCapabilities}) an item handler that
 * inserters and hoppers already know how to use. A {@code ResourceHandler} would have meant
 * writing a menu and a screen to gain nothing.
 *
 * <p>The size comes from the block rather than from this class, so a tier is a
 * {@link MetalChestBlock} subclass and nothing here changes. Both tiers land on a row count
 * vanilla already has a screen for - four and six - which is the whole reason the slot counts are
 * 36 and 54 rather than Factorio's 32 and 48.
 */
public class MetalChestBlockEntity extends RandomizableContainerBlockEntity {

    private final int rows;

    private NonNullList<ItemStack> items;

    /**
     * Both the type and the size come from the block, which is what lets one constructor serve
     * every tier: {@code BlockEntityType}'s supplier is handed the state, and the state names the
     * block that knows which of the two it is.
     */
    public MetalChestBlockEntity(BlockPos pos, BlockState state) {
        super(((MetalChestBlock) state.getBlock()).type(), pos, state);
        this.rows = ((MetalChestBlock) state.getBlock()).rows();
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return rows * 9;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
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

    /**
     * The loot-table dance is vanilla's, kept so a structure could one day place a pre-filled
     * chest without this class needing to change.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, items);
        }
    }
}
