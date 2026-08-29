package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModMenus;

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
 * The lab's menu: six science pack slots and three numbers.
 *
 * <p>No position travels with it. Everything the screen draws arrives as ints in a
 * {@link ContainerData}, so the client half can be built from nothing - which is what the
 * two-argument constructor is for, and why the menu type is a plain vanilla one. The assembler's
 * needs NeoForge's factory because its chosen recipe cannot be an int; a lab has no such thing.
 */
public class LabMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_ENERGY = 1;
    public static final int DATA_CYCLES = 2;

    /**
     * How long the cycle in progress is meant to take.
     *
     * <p>A number rather than a constant because it is the <em>technology's</em>, not the lab's -
     * Factorio prices research by the technology and this pack does too. The screen needs it to
     * draw a bar against something, and sending it beats having the client look the current
     * research up: the two would then disagree for the tick after a research was swapped, which
     * is a bar that jumps.
     */
    public static final int DATA_CYCLE_TICKS = 3;

    public static final int DATA_COUNT = 4;

    /**
     * Where the screen expects the pack slots. Shared, so the two cannot drift apart, and read
     * back out of here by {@code tools/check_gui_layout.py}.
     */
    public static final int PACKS_X = 26;
    public static final int PACKS_Y = 34;
    public static final int PACK_COLUMNS = 6;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client side: there is no lab here, only the numbers the server sends. */
    public LabMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
                new LabInventory(LabBlockEntity.SLOT_COUNT, () -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    public LabMenu(int containerId, Inventory playerInventory, LabInventory packs,
            ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.LAB.get(), containerId);
        this.data = data;
        this.access = access;

        for (int slot = 0; slot < LabBlockEntity.SLOT_COUNT; slot++) {
            addSlot(new ResourceHandlerSlot(packs, packs::set, slot,
                    PACKS_X + (slot % PACK_COLUMNS) * 18,
                    PACKS_Y + (slot / PACK_COLUMNS) * 18));
        }

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

    /** 0 to 1 through the cycle in progress. */
    public float progress() {
        int total = data.get(DATA_CYCLE_TICKS);
        if (total <= 0) {
            total = LabBlockEntity.IDLE_TICKS_PER_CYCLE;
        }
        return Math.clamp(data.get(DATA_PROGRESS) / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across the energy buffer. */
    public float charge() {
        return Math.clamp(
                data.get(DATA_ENERGY) / (float) LabBlockEntity.ENERGY_CAPACITY, 0.0f, 1.0f);
    }

    /** How much research this lab has finished. */
    public int cycles() {
        return data.get(DATA_CYCLES);
    }

    public boolean hasPower() {
        return data.get(DATA_ENERGY) >= LabBlockEntity.ENERGY_PER_TICK;
    }

    public boolean isWorking() {
        return data.get(DATA_PROGRESS) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.LAB.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = LabBlockEntity.SLOT_COUNT;
        int inventoryEnd = machineEnd + PLAYER_SLOTS;

        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineEnd, false)) {
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
