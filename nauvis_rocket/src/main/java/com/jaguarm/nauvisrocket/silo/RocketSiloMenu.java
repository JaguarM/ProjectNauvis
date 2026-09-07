package com.jaguarm.nauvisrocket.silo;

import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvisrocket.client.ClientSiloRules;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The silo's menu: three ingredient slots, the satellite's, the output's, four module slots, and
 * the numbers a screen draws.
 *
 * <p>Not a {@code RecipeSelector}: a silo builds rocket parts and nothing else, so the crafting
 * panel beside this screen queues the player's own crafts, as it does beside a chest. Everything
 * the screen draws arrives as ints, so the client half is built from nothing - the lab's
 * arrangement.
 */
public class RocketSiloMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CRAFT_TICKS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_PER_TICK = 3;
    public static final int DATA_PARTS = 4;
    public static final int DATA_PARTS_NEEDED = 5;
    public static final int DATA_LAUNCH_TICKS = 6;
    public static final int DATA_OWED = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_LAUNCHES = 9;
    public static final int DATA_AUTO_LAUNCH = 10;
    public static final int DATA_COUNT = 11;

    /** The two buttons, by the id vanilla's menu-button packet carries. */
    public static final int BUTTON_AUTO_LAUNCH = 0;
    public static final int BUTTON_LAUNCH = 1;

    /**
     * Where the screen expects to find things. Shared, so the two cannot drift apart, and read
     * back out of here by {@code tools/check_gui_layout.py}.
     *
     * <p>Two rows above the status line at 58: the ingredients, the satellite and the output
     * along the top, the four module slots along the second row on the left, with the rocket
     * bar and the charge to their right.
     */
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 17;
    public static final int SATELLITE_X = 80;
    public static final int SATELLITE_Y = 17;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 17;
    public static final int MODULE_X = 8;
    public static final int MODULE_Y = 36;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** The machine, on the server; null on a client, which only has the numbers. */
    private final @Nullable RocketSiloBlockEntity silo;

    /**
     * Client side: there is no silo here, only the numbers the server sends - and the client's own
     * copy of the rocket part recipe, so a shift-click lands where the server will put it.
     */
    public RocketSiloMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
                new RocketSiloInventory(() -> {}, ClientSiloRules.partWants(), resource -> resource.is(ModItems.SATELLITE.get())),
                new ModuleSlots(RocketSiloBlockEntity.MODULE_SLOTS, () -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL, null);
    }

    public RocketSiloMenu(int containerId, Inventory playerInventory, RocketSiloInventory inventory,
            ModuleSlots modules, ContainerData data, ContainerLevelAccess access, @Nullable RocketSiloBlockEntity silo) {
        super(ModMenus.ROCKET_SILO.get(), containerId);
        this.data = data;
        this.access = access;
        this.silo = silo;

        for (int index = 0; index < RocketSiloBlockEntity.INPUT_SLOTS; index++) {
            addSlot(new ResourceHandlerSlot(inventory, inventory::set, index, INPUT_X + index * 18, INPUT_Y));
        }
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, RocketSiloBlockEntity.SATELLITE_SLOT,
                SATELLITE_X, SATELLITE_Y));
        addSlot(new OutputSlot(inventory, RocketSiloBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));
        for (int index = 0; index < modules.size(); index++) {
            addSlot(new ResourceHandlerSlot(modules, modules::set, index, MODULE_X + index * 18, MODULE_Y));
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

    /** The machine fills this; the player may only take from it. */
    private static class OutputSlot extends ResourceHandlerSlot {

        OutputSlot(RocketSiloInventory inventory, int index, int x, int y) {
            super(inventory, inventory::set, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** 0 to 1 across the part being built, and 0 when none is. */
    public float partProgress() {
        int total = data.get(DATA_CRAFT_TICKS);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_PROGRESS) / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across the rocket: parts built over parts needed. */
    public float rocketProgress() {
        int needed = data.get(DATA_PARTS_NEEDED);
        return needed <= 0 ? 0.0f : Math.clamp(data.get(DATA_PARTS) / (float) needed, 0.0f, 1.0f);
    }

    public int parts() {
        return data.get(DATA_PARTS);
    }

    public int partsNeeded() {
        return data.get(DATA_PARTS_NEEDED);
    }

    public int launchTicks() {
        return data.get(DATA_LAUNCH_TICKS);
    }

    public int owed() {
        return data.get(DATA_OWED);
    }

    public int launches() {
        return data.get(DATA_LAUNCHES);
    }

    /** 0 to 1 across the buffer. */
    public float charge() {
        return Math.clamp(data.get(DATA_ENERGY) / (float) RocketSiloBlockEntity.ENERGY_CAPACITY, 0.0f, 1.0f);
    }

    public RocketSiloStatus status() {
        return RocketSiloStatus.of(data.get(DATA_STATUS));
    }

    public boolean autoLaunch() {
        return data.get(DATA_AUTO_LAUNCH) != 0;
    }

    /** Whether the rocket is complete and not yet leaving: when the Launch button does something. */
    public boolean canLaunch() {
        return parts() >= partsNeeded() && partsNeeded() > 0 && launchTicks() <= 0;
    }

    /**
     * The two buttons, through vanilla's menu-button packet: the screen calls
     * {@code handleInventoryButtonClick} and the server lands here. Nothing to write and nothing
     * to trust - a button id the menu does not know is ignored.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (silo == null) {
            return false;
        }
        switch (buttonId) {
            case BUTTON_AUTO_LAUNCH -> silo.setAutoLaunch(!silo.autoLaunch());
            case BUTTON_LAUNCH -> silo.requestLaunch();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ROCKET_SILO.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int itemsEnd = RocketSiloBlockEntity.SLOT_COUNT;
        int machineEnd = itemsEnd + RocketSiloBlockEntity.MODULE_SLOTS;
        int inventoryEnd = machineEnd + PLAYER_SLOTS;

        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ModuleSlots.moduleOf(stack) != null) {
            if (!moveItemStackTo(stack, itemsEnd, machineEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.SATELLITE.get())) {
            if (!moveItemStackTo(stack, RocketSiloBlockEntity.SATELLITE_SLOT, RocketSiloBlockEntity.SATELLITE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, RocketSiloBlockEntity.INPUT_SLOTS, false)) {
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
