package com.jaguarm.nauvismachines.machine.furnace;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvismachines.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The furnace's menu: an input slot, a fuel slot for the burner tiers, an output slot, and the
 * numbers a screen draws.
 *
 * <p>Not a {@code RecipeSelector}, on purpose. A furnace is told nothing; it smelts what is put
 * in it. So the crafting panel beside this screen behaves as it does beside a chest - it queues
 * the player's own crafts - and the machine's recipe is whatever the input slot says.
 *
 * <p>One menu for three tiers. Which slots it has is decided by the block at the machine's
 * position, which both sides can read: an electric furnace simply has no fuel slot, and its
 * inventory's fuel index is a slot nothing can go in. The position travels with the menu for that
 * reason and for the screen to name what is being smelted, which it reads off the block entity.
 */
public class FurnaceMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CRAFT_TICKS = 1;
    public static final int DATA_BURN_TIME = 2;
    public static final int DATA_BURN_TIME_TOTAL = 3;
    public static final int DATA_ENERGY = 4;
    public static final int DATA_ENERGY_PER_TICK = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_COUNT = 8;

    /**
     * Where the screen expects to find things: vanilla's furnace layout, exactly, because the
     * screen is vanilla's furnace texture. The input above the flame, the fuel below it, the
     * output past the arrow.
     */
    public static final int INPUT_X = 56;
    public static final int INPUT_Y = 17;
    public static final int FUEL_X = 56;
    public static final int FUEL_Y = 53;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 35;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final Level level;
    private final boolean burner;

    /** How many of this menu's slots are the machine's; the player's follow. */
    private final int machineSlots;

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public FurnaceMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory,
                new FurnaceInventory(() -> {}, resource -> true, resource -> true),
                new SimpleContainerData(DATA_COUNT),
                machinePos,
                isBurnerAt(playerInventory.player.level(), machinePos));
    }

    public FurnaceMenu(int containerId, Inventory playerInventory, FurnaceInventory inventory,
            ContainerData data, BlockPos machinePos, boolean burner) {
        super(ModMenus.FURNACE.get(), containerId);
        this.data = data;
        this.machinePos = machinePos;
        this.level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, machinePos);
        this.burner = burner;

        addSlot(new ResourceHandlerSlot(inventory, inventory::set, FurnaceBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y));
        if (burner) {
            addSlot(new ResourceHandlerSlot(inventory, inventory::set, FurnaceBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y));
        }
        addSlot(new OutputSlot(inventory, FurnaceBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));
        machineSlots = slots.size();

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

    private static boolean isBurnerAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FurnaceBlock block && block.isBurner();
    }

    /** The machine fills this; the player may only take from it. */
    private static class OutputSlot extends ResourceHandlerSlot {

        OutputSlot(FurnaceInventory inventory, int index, int x, int y) {
            super(inventory, inventory::set, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public boolean isBurner() {
        return burner;
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int craftTicks() {
        return data.get(DATA_CRAFT_TICKS);
    }

    /** 0 to 1 across the current smelt, and 0 when the machine is idle. */
    public float craftProgress() {
        int total = craftTicks();
        return total <= 0 ? 0.0f : Math.clamp(progress() / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 through the current item of fuel, and 0 when nothing is burning. */
    public float burnProgress() {
        int total = data.get(DATA_BURN_TIME_TOTAL);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_BURN_TIME) / (float) total, 0.0f, 1.0f);
    }

    public boolean isBurning() {
        return data.get(DATA_BURN_TIME) > 0;
    }

    /** 0 to 1 across an electric furnace's buffer. */
    public float charge() {
        int capacity = data.get(DATA_ENERGY_CAPACITY);
        return capacity <= 0 ? 0.0f : Math.clamp(data.get(DATA_ENERGY) / (float) capacity, 0.0f, 1.0f);
    }

    public FurnaceBlockEntity.Status status() {
        return FurnaceBlockEntity.Status.of(data.get(DATA_STATUS));
    }

    /** What the machine is making, read off the synced block entity, or null when nothing. */
    public @Nullable Item smelting() {
        return level.getBlockEntity(machinePos) instanceof FurnaceBlockEntity furnace ? furnace.making() : null;
    }

    /**
     * Any tier, not one block: vanilla's helper compares the block at the position with a single
     * block, and this menu is shared by three.
     */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof FurnaceBlock
                && player.isWithinBlockInteractionRange(pos, 4.0), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int inventoryEnd = machineSlots + PLAYER_SLOTS;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots - 1, false)) {
            // Into the input or the fuel slot, whichever will have it, and never the output.
            // Each slot refuses what it cannot use, so ore lands in the input and coal in the
            // fuel slot without the click having to say which.
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
