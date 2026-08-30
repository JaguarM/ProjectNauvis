package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MinerMenu extends AbstractContainerMenu {

    /** Fuel, fill, pickaxe, then the module slots. */
    private static final int MACHINE_MENU_SLOTS = 3 + MinerBlockEntity.MODULE_SLOTS;

    /** Top-left of the 2x2 module block. The screen mirrors these. */
    public static final int MODULE_X = 74;
    public static final int MODULE_Y = 17;

    /** The fuel slot, which the screen paints over on electric drills. */
    public static final int FUEL_X = 30;
    public static final int FUEL_Y = 17;

    private final Container container;
    private final ContainerData data;
    /** Needed to resolve fuel burn times; present on both client and server. */
    private final net.minecraft.world.level.Level level;
    /** Where the machine is, so the client can draw its dig area. */
    private final BlockPos machinePos;

    /** Client-side constructor: a stand-in container the server syncs into, plus the machine's position. */
    public MinerMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new SimpleContainer(MinerBlockEntity.SLOT_COUNT),
                new SimpleContainerData(12), machinePos);
    }

    public MinerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data,
            BlockPos machinePos) {
        super(ModMenus.MINER.get(), containerId);
        checkContainerSize(container, MinerBlockEntity.SLOT_COUNT);
        this.container = container;
        this.data = data;
        this.level = playerInventory.player.level();
        this.machinePos = machinePos;

        // Machine slots. Positions mirror the generated GUI texture. Every one uses the
        // same shared rule as the block entity so client and server never disagree.
        addSlot(new RuleSlot(container, MinerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));
        addSlot(new RuleSlot(container, MinerBlockEntity.SLOT_COBBLE, 48, 17));
        addSlot(new RuleSlot(container, MinerBlockEntity.SLOT_PICKAXE, 30, 35));

        // Modules as a 2x2 block. All four always exist so the container size is fixed
        // across tiers; locked ones reject everything and the screen hides them.
        for (int i = 0; i < MinerBlockEntity.MODULE_SLOTS; i++) {
            addSlot(new RuleSlot(container, MinerBlockEntity.SLOT_MODULE_START + i,
                    MODULE_X + (i % 2) * 18, MODULE_Y + (i / 2) * 18));
        }

        // Output grid is extract-only.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = MinerBlockEntity.SLOT_OUTPUT_START + row * 3 + col;
                addSlot(new OutputSlot(container, index, 116 + col * 18, 17 + row * 18));
            }
        }

        // Must match GenGui's layout: the panel is 184 tall, so the inventory sits at
        // height-82 and the hotbar at height-24.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 160));
        }

        addDataSlots(data);
    }

    /** A slot the miner fills and the player may only take from. */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * A machine slot whose acceptance comes from the shared static rule rather than from
     * the container, so it behaves identically on both sides and shift-click lands the
     * item in the right slot on the first frame.
     */
    private class RuleSlot extends Slot {
        RuleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return MinerBlockEntity.acceptsInSlot(getContainerSlot(), stack, level,
                    new MinerBlockEntity.SlotRules(unlockedModuleSlots(), isElectric(), needsFill()));
        }

        @Override
        public int getMaxStackSize() {
            return isModuleSlot() ? 1 : super.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return isModuleSlot() ? 1 : super.getMaxStackSize(stack);
        }

        private boolean isModuleSlot() {
            return getContainerSlot() >= MinerBlockEntity.SLOT_MODULE_START
                    && getContainerSlot() < MinerBlockEntity.SLOT_OUTPUT_START;
        }
    }

    /** True if this module slot is usable at the machine's tier, for shading in the screen. */
    public boolean isModuleSlotUnlocked(int moduleIndex) {
        return data.get(5) > moduleIndex;
    }

    /** How many module slots this machine's tier unlocks. */
    public int unlockedModuleSlots() {
        return data.get(5);
    }

    /** The machine's dig radius, synced so the client can draw the preview. */
    public int range() {
        return Math.max(1, data.get(6));
    }

    public BlockPos machinePos() {
        return machinePos;
    }

    /** True if this machine runs on FE rather than solid fuel. */
    public boolean isElectric() {
        return data.get(7) != 0;
    }

    /** True if the current mode fills its holes, so the fill slot is actually used. */
    public boolean needsFill() {
        return data.get(10) != 0;
    }

    public DigMode digMode() {
        return DigMode.byOrdinal(data.get(11));
    }

    /** Button id for the dig-mode cycle, sent by the screen. */
    public static final int BUTTON_CYCLE_DIG_MODE = 0;

    /**
     * Handles the screen's button. Uses vanilla's menu-button channel rather than a custom
     * packet: the server already validates that this player has this menu open, which is
     * the check that matters.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CYCLE_DIG_MODE && container instanceof MinerBlockEntity miner) {
            miner.cycleDigMode();
            return true;
        }
        return false;
    }

    /** Charge level from 0 to 1, for the energy bar. */
    public float energyProgress() {
        int capacity = data.get(9);
        return capacity <= 0 ? 0.0f : (float) data.get(8) / capacity;
    }

    public int storedEnergy() {
        return data.get(8);
    }

    public int energyCapacity() {
        return data.get(9);
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }

    /** Why the machine is or is not running, for the status line in the screen. */
    public MinerStatus status() {
        return MinerStatus.byOrdinal(data.get(4));
    }

    /**
     * Fraction of the current fuel item remaining, for the flame indicator.
     *
     * <p>Vanilla's {@code AbstractFurnaceMenu#getLitProgress}, fallback and clamp included.
     * The 200-tick stand-in covers a client that has the burn time but not yet the total,
     * which would otherwise draw an empty flame over a machine that is plainly burning.
     */
    public float fuelProgress() {
        int total = data.get(1);
        if (total == 0) {
            total = 200;
        }
        return Mth.clamp((float) data.get(0) / total, 0.0f, 1.0f);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = MinerBlockEntity.SLOT_COUNT;
        int inventoryEnd = machineEnd + 36;

        if (index < machineEnd) {
            // Machine -> player inventory
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> whichever machine slot will accept it
            if (!moveIntoMachine(stack)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Routes a shift-clicked stack to the first machine slot that accepts it, so cobble
     * goes to the cobble slot and coal to the fuel slot without the player aiming.
     */
    private boolean moveIntoMachine(ItemStack stack) {
        // Menu index -> container slot, in the order the machine slots were added above.
        int[] containerSlots = new int[MACHINE_MENU_SLOTS];
        containerSlots[0] = MinerBlockEntity.SLOT_FUEL;
        containerSlots[1] = MinerBlockEntity.SLOT_COBBLE;
        containerSlots[2] = MinerBlockEntity.SLOT_PICKAXE;
        for (int i = 0; i < MinerBlockEntity.MODULE_SLOTS; i++) {
            containerSlots[3 + i] = MinerBlockEntity.SLOT_MODULE_START + i;
        }

        for (int menuIndex = 0; menuIndex < containerSlots.length; menuIndex++) {
            // Ask the slot, not the container: the slot uses the shared rule, so this
            // picks the same destination on client and server.
            if (slots.get(menuIndex).mayPlace(stack)
                    && moveItemStackTo(stack, menuIndex, menuIndex + 1, false)) {
                return true;
            }
        }
        return false;
    }
}
