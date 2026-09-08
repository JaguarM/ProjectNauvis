package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The drill's menu: a pickaxe slot, a fuel slot on the burner, an output slot, the electric
 * drill's three module slots, and the numbers a screen draws.
 *
 * <p>One menu for both tiers. Which slots it has is decided by the block at the machine's
 * position, which both sides can read; the position travels with the menu for that, for the
 * area preview, and for the screen to name what is being mined, which it reads off the block
 * entity.
 */
public class MinerMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CYCLE_TICKS = 1;
    public static final int DATA_BURN_TIME = 2;
    public static final int DATA_BURN_TIME_TOTAL = 3;
    public static final int DATA_ENERGY = 4;
    public static final int DATA_ENERGY_PER_TICK = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_FACTORIO_ORES_ONLY = 8;
    public static final int DATA_COUNT = 9;

    /** The one button: {@code clickMenuButton} with this id flips whether the drill takes only Factorio's ores. */
    public static final int BUTTON_FACTORIO_ORES = 0;

    /**
     * Where the screen expects to find things. Shared, so the two cannot drift apart.
     *
     * <p>The furnace's arrangement: the tool over the fuel on the left with the flame beside
     * them, the output on the right. The module row runs along the top, so the output sits under
     * it and the arrow drops a row to pass beneath - everything above the status line at 58.
     */
    public static final int PICKAXE_X = 44;
    public static final int PICKAXE_Y = 17;
    public static final int FUEL_X = 44;
    public static final int FUEL_Y = 35;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 36;
    /** The electric drill's three module slots, in a row above the output. */
    public static final int MODULE_X = 80;
    public static final int MODULE_Y = 17;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final Level level;
    private final MachineTier tier;

    /** How many of this menu's slots are the machine's; the player's follow. */
    private final int machineSlots;

    /** How many of those are item slots; the module slots follow them. */
    private final int itemSlots;

    /**
     * Client side: NeoForge's menu factory hands the machine's position across, and the tier is
     * read off the block there. The stand-in inventory is told the same rules the server's has,
     * so a shift-click is predicted where it will land.
     */
    public MinerMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory,
                clientInventory(playerInventory.player.level(), tierAt(playerInventory.player.level(), machinePos)),
                new ModuleSlots(tierAt(playerInventory.player.level(), machinePos).moduleSlots(), () -> {}),
                new SimpleContainerData(DATA_COUNT),
                machinePos,
                tierAt(playerInventory.player.level(), machinePos));
    }

    private static MachineTier tierAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MinerBlock drill ? drill.tier() : MachineTier.BURNER;
    }

    private static DrillInventory clientInventory(Level level, MachineTier tier) {
        return new DrillInventory(() -> {}, tier.isElectric()
                ? resource -> false
                : resource -> resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0);
    }

    public MinerMenu(int containerId, Inventory playerInventory, DrillInventory inventory,
            ModuleSlots modules, ContainerData data, BlockPos machinePos, MachineTier tier) {
        super(ModMenus.MINER.get(), containerId);
        this.data = data;
        this.machinePos = machinePos;
        this.level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, machinePos);
        this.tier = tier;

        addSlot(new ResourceHandlerSlot(inventory, inventory::set, MinerBlockEntity.PICKAXE_SLOT, PICKAXE_X, PICKAXE_Y));
        if (!tier.isElectric()) {
            addSlot(new ResourceHandlerSlot(inventory, inventory::set, MinerBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y));
        }
        addSlot(new OutputSlot(inventory, MinerBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));
        itemSlots = slots.size();
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

    /** The machine fills this; the player may only take from it. */
    private static class OutputSlot extends ResourceHandlerSlot {

        OutputSlot(DrillInventory inventory, int index, int x, int y) {
            super(inventory, inventory::set, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public MachineTier tier() {
        return tier;
    }

    public boolean isElectric() {
        return tier.isElectric();
    }

    public BlockPos machinePos() {
        return machinePos;
    }

    /** 0 to 1 across the current ore, and 0 when the machine is idle. */
    public float cycleProgress() {
        int total = data.get(DATA_CYCLE_TICKS);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_PROGRESS) / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 through the current item of fuel, and 0 when nothing is burning. */
    public float burnProgress() {
        int total = data.get(DATA_BURN_TIME_TOTAL);
        return total <= 0 ? 0.0f : Math.clamp(data.get(DATA_BURN_TIME) / (float) total, 0.0f, 1.0f);
    }

    public boolean isBurning() {
        return data.get(DATA_BURN_TIME) > 0;
    }

    /** 0 to 1 across an electric drill's buffer. */
    public float charge() {
        int capacity = data.get(DATA_ENERGY_CAPACITY);
        return capacity <= 0 ? 0.0f : Math.clamp(data.get(DATA_ENERGY) / (float) capacity, 0.0f, 1.0f);
    }

    public MinerStatus status() {
        return MinerStatus.byOrdinal(data.get(DATA_STATUS));
    }

    public boolean factorioOresOnly() {
        return data.get(DATA_FACTORIO_ORES_ONLY) != 0;
    }

    /** Vanilla's menu-button packet, as the screen sends it; the server's menu reaches the drill through the level. */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != BUTTON_FACTORIO_ORES || !(level.getBlockEntity(machinePos) instanceof MinerBlockEntity drill)) {
            return false;
        }
        drill.setFactorioOresOnly(!drill.factorioOresOnly());
        return true;
    }

    /** The ore being mined, read off the synced block entity, or null when there is none. */
    public @Nullable Block mining() {
        return level.getBlockEntity(machinePos) instanceof MinerBlockEntity drill ? drill.mining() : null;
    }

    /** Either drill: vanilla's helper compares with one block, and this menu is shared by two. */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof MinerBlock
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
        } else if (ModuleSlots.moduleOf(stack) != null) {
            // A module goes to the module slots and nowhere else.
            if (!moveItemStackTo(stack, itemSlots, machineSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, itemSlots - 1, false)) {
            // Into the pickaxe or the fuel slot, whichever will have it, and never the output.
            // Each slot refuses what it cannot use, so a pickaxe lands in the pickaxe slot and
            // coal in the fuel slot without the click having to say which.
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
