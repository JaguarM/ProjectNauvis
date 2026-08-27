package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The burner inserter's menu: one fuel slot, and how far through a swing it is.
 *
 * <p>It replaces right-clicking with coal in hand to fuel it and right-clicking empty-handed to
 * print a line of text. Both were stand-ins for a screen: you could not see what was in the slot,
 * could not take it back out, and could not tell an inserter that was out of coal from one that
 * simply had nothing to move.
 *
 * <p>The electric inserter deliberately has none of this. It has no slot, so there would be
 * nothing in the screen but a bar - which is what the hover display is for.
 */
public class BurnerInserterMenu extends AbstractContainerMenu {

    public static final int DATA_BURN_TIME = 0;
    public static final int DATA_BURN_TIME_TOTAL = 1;
    public static final int DATA_SWING = 2;
    public static final int DATA_COUNT = 3;

    /** Where the screen expects to find things. Shared, so the two cannot drift apart. */
    public static final int FUEL_X = 44;
    public static final int FUEL_Y = 34;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client side: there is no inserter here, only the numbers the server sends. */
    public BurnerInserterMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
                new InserterFuel(BurnerInserterBlockEntity.SLOT_COUNT, () -> {}, () -> null),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    public BurnerInserterMenu(int containerId, Inventory playerInventory, InserterFuel fuel,
            ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.BURNER_INSERTER.get(), containerId);
        this.data = data;
        this.access = access;

        addSlot(new ResourceHandlerSlot(fuel, fuel::set,
                BurnerInserterBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public boolean isBurning() {
        return data.get(DATA_BURN_TIME) > 0;
    }

    /** 0 to 1 through the current item of fuel, and 0 when nothing is burning. */
    public float burnProgress() {
        int total = data.get(DATA_BURN_TIME_TOTAL);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_BURN_TIME) / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across one swing, and 0 when the inserter is holding nothing. */
    public float swingProgress() {
        return Math.clamp(
                data.get(DATA_SWING) / (float) BurnerInserterBlockEntity.SWING_TICKS, 0.0f, 1.0f);
    }

    public boolean isSwinging() {
        return data.get(DATA_SWING) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.BURNER_INSERTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = BurnerInserterBlockEntity.SLOT_COUNT;
        int inventoryEnd = machineEnd + PLAYER_SLOTS;

        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineEnd, false)) {
            // The fuel slot refuses anything that will not burn, so shift-clicking iron in here
            // simply does nothing rather than jamming the slot with something useless.
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
