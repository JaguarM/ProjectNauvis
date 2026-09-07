package com.jaguarm.nauvismilitary.turret;

import com.jaguarm.nauvismilitary.registry.ModMenus;
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

/** The turret's menu: one magazine slot, and what the turret is doing. */
public class GunTurretMenu extends AbstractContainerMenu {

    public static final int DATA_STATUS = 0;
    public static final int DATA_ROUNDS = 1;
    public static final int DATA_SHOTS = 2;
    public static final int DATA_COUNT = 3;

    /** The one slot, in the middle of the panel above the status line. */
    public static final int AMMO_X = 80;
    public static final int AMMO_Y = 30;

    private static final int PLAYER_SLOTS = 36;
    private static final int MACHINE_SLOTS = 1;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;

    /** Client side: NeoForge's menu factory hands the turret's position across. */
    public GunTurretMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory, new TurretInventory(() -> {}), new SimpleContainerData(DATA_COUNT), machinePos);
    }

    public GunTurretMenu(int containerId, Inventory playerInventory, TurretInventory inventory, ContainerData data,
            BlockPos machinePos) {
        super(ModMenus.GUN_TURRET.get(), containerId);
        this.data = data;
        this.machinePos = machinePos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), machinePos);

        addSlot(new ResourceHandlerSlot(inventory, inventory::set, GunTurretBlockEntity.AMMO_SLOT, AMMO_X, AMMO_Y));

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

    public GunTurretBlockEntity.Status status() {
        return GunTurretBlockEntity.Status.of(data.get(DATA_STATUS));
    }

    public int roundsLeft() {
        return data.get(DATA_ROUNDS);
    }

    public int shots() {
        return data.get(DATA_SHOTS);
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof GunTurretBlock
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
        int inventoryEnd = MACHINE_SLOTS + PLAYER_SLOTS;

        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
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
