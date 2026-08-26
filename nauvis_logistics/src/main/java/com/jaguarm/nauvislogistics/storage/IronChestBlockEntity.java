package com.jaguarm.nauvislogistics.storage;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

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
 * An iron chest: a bigger box, and the first container in the pack that is ours rather than
 * vanilla's.
 *
 * <p>A plain {@link net.minecraft.world.Container}, unlike every other block in this pack, and
 * that is a deliberate choice rather than an oversight. A chest has no logic to speak of - it
 * holds things - and being a Container buys two whole features for nothing: vanilla's own
 * four-row screen, and (through the capability registered in
 * {@code ModCapabilities}) an item handler that inserters and hoppers already know how to use.
 * A {@code ResourceHandler} would have meant writing a menu and a screen to gain nothing.
 *
 * <p>Thirty-six slots against Factorio's thirty-two. Slot count is behaviour, not identity - the
 * id, the eight iron plates and the half-second are the parts non-negotiable #1 governs - and 36
 * is 9x4, which is a screen vanilla already has. Thirty-two would have cost a custom GUI to be
 * four slots worse.
 */
public class IronChestBlockEntity extends RandomizableContainerBlockEntity {

    /** Four rows of nine. See the class comment on why not Factorio's thirty-two. */
    public static final int SLOT_COUNT = 36;
    public static final int ROWS = 4;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public IronChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_CHEST.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.nauvis_logistics.iron_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x4, containerId, inventory, this, ROWS);
    }

    /**
     * The loot-table dance is vanilla's, kept so a structure could one day place a pre-filled
     * iron chest without this class needing to change.
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
