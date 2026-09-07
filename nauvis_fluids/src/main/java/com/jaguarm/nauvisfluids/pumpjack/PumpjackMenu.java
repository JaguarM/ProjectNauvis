package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvisfluids.registry.ModMenus;
import net.minecraft.core.BlockPos;
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
 * The pumpjack's menu: two module slots and the numbers a screen draws. Nothing goes in or comes
 * out by hand - the oil leaves through the outlet - so the slots are the whole reason there is
 * a screen, and the screen is where the charge and the tank are read.
 */
public class PumpjackMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CYCLE_TICKS = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_CAPACITY = 3;
    public static final int DATA_STORED = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_COUNT = 6;

    /** The two module slots, in a row along the top. */
    public static final int MODULE_X = 80;
    public static final int MODULE_Y = 17;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final int machineSlots;

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public PumpjackMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new ModuleSlots(PumpjackBlockEntity.MODULE_SLOTS, () -> {}),
                new SimpleContainerData(DATA_COUNT), machinePos);
    }

    public PumpjackMenu(int containerId, Inventory playerInventory, ModuleSlots modules, ContainerData data,
            BlockPos machinePos) {
        super(ModMenus.PUMPJACK.get(), containerId);
        this.data = data;
        this.machinePos = machinePos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), machinePos);

        for (int index = 0; index < modules.size(); index++) {
            addSlot(new ResourceHandlerSlot(modules, modules::set, index, MODULE_X + index * 18, MODULE_Y));
        }
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

    public BlockPos machinePos() {
        return machinePos;
    }

    /** 0 to 1 across the current cycle. */
    public float cycleProgress() {
        int total = data.get(DATA_CYCLE_TICKS);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_PROGRESS) / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across the buffer. */
    public float charge() {
        int capacity = data.get(DATA_ENERGY_CAPACITY);
        return capacity <= 0 ? 0.0f : Math.clamp(data.get(DATA_ENERGY) / (float) capacity, 0.0f, 1.0f);
    }

    /** 0 to 1 across the tank. */
    public float tankFill() {
        return Math.clamp(data.get(DATA_STORED) / (float) PumpjackBlockEntity.TANK_CAPACITY, 0.0f, 1.0f);
    }

    public int stored() {
        return data.get(DATA_STORED);
    }

    public PumpjackStatus status() {
        return PumpjackStatus.byOrdinal(data.get(DATA_STATUS));
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof PumpjackBlock
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
        } else if (ModuleSlots.moduleOf(stack) == null || !moveItemStackTo(stack, 0, machineSlots, false)) {
            // Only a module has anywhere to go.
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
