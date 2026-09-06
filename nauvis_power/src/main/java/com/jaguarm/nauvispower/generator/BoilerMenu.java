package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModMenus;

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
 * The boiler's menu: one fuel slot and three numbers.
 *
 * <p>It replaces what used to be right-click handling - coal in hand to fuel it, empty hand to
 * print a line of text. Both were stand-ins for a screen, and a machine you interact with by
 * holding the right item and clicking is a machine whose contents you cannot see, cannot take back
 * out, and cannot point a hopper at with any confidence.
 *
 * <p>No position travels with this menu, unlike the assembler's. The screen reads everything it
 * needs out of {@link ContainerData}, so the client half can be built from nothing - which is what
 * the two-argument constructor is for, and why the menu type is a plain vanilla one.
 */
public class BoilerMenu extends AbstractContainerMenu {

    /** Ticks of fuel left, what the last item was worth, and the two tanks. */
    public static final int DATA_BURN_TIME = 0;
    public static final int DATA_BURN_TIME_TOTAL = 1;
    public static final int DATA_STEAM = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_COUNT = 4;

    /** Where the screen expects to find things. Shared, so the two cannot drift apart. */
    public static final int FUEL_X = 26;
    public static final int FUEL_Y = 34;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /**
     * Client side: there is no boiler here, only the numbers the server sends - and the fuel
     * values, which the server sends too, so the stand-in slot refuses what the real one will
     * and a shift-clicked stick stays where it was rather than bouncing back a tick later.
     */
    public BoilerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
                new BoilerFuel(BoilerBlockEntity.SLOT_COUNT, () -> {},
                        () -> playerInventory.player.level().fuelValues()),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    public BoilerMenu(int containerId, Inventory playerInventory, BoilerFuel fuel,
            ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.BOILER.get(), containerId);
        this.data = data;
        this.access = access;

        addSlot(new ResourceHandlerSlot(fuel, fuel::set, BoilerBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y));

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

    public int burnTime() {
        return data.get(DATA_BURN_TIME);
    }

    /** 0 to 1 through the current item of fuel, and 0 when nothing is burning. */
    public float burnProgress() {
        int total = data.get(DATA_BURN_TIME_TOTAL);
        return total <= 0 ? 0.0f : Math.clamp(burnTime() / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across the steam buffer. */
    public float steam() {
        return Math.clamp(
                data.get(DATA_STEAM) / (float) BoilerBlockEntity.STEAM_CAPACITY, 0.0f, 1.0f);
    }

    /** 0 to 1 across the water tank. */
    public float water() {
        return Math.clamp(
                data.get(DATA_WATER) / (float) BoilerBlockEntity.WATER_CAPACITY, 0.0f, 1.0f);
    }

    /** Whether there is water enough to boil - the first thing a stopped boiler is short of. */
    public boolean hasWater() {
        return data.get(DATA_WATER) >= BoilerBlockEntity.WATER_PER_TICK;
    }

    public boolean isBurning() {
        return burnTime() > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.BOILER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = BoilerBlockEntity.SLOT_COUNT;
        int inventoryEnd = machineEnd + PLAYER_SLOTS;

        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineEnd, false)) {
            // The fuel slot refuses anything that will not burn, so shift-clicking a diamond in
            // here simply does nothing rather than filling the slot with something useless.
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
