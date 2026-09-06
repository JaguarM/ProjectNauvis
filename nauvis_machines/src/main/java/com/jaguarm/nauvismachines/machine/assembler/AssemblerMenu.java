package com.jaguarm.nauvismachines.machine.assembler;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.machine.RecipeSelector;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvismachines.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The assembler's menu: six ingredient slots, one output slot, the tier's module slots, and the
 * machine's progress.
 *
 * <p>Implements {@link RecipeSelector}, which is what makes Facrafting's crafting panel change
 * its mind about what a click means. With this menu open, clicking a recipe over there points
 * this machine at it. There is deliberately no recipe list in this screen: the panel already has
 * one, with search and tabs and a picture of everything the pack can make, and two of them would
 * be two things to keep in step.
 *
 * <p>The chosen recipe is not a data slot, because a recipe key is not an int. The client reads
 * it off the block entity instead, which it already has - {@link AssemblerBlockEntity} pushes its
 * state to watching clients whenever the recipe changes.
 *
 * <p>How many module slots there are is the tier's, read off the block at the machine's position
 * on both sides, so an assembling machine 1 draws none and an assembling machine 2 draws two, and
 * the client's stand-in handler is the right size for the slots it is asked to show.
 */
public class AssemblerMenu extends AbstractContainerMenu implements RecipeSelector {

    /** Ticks into the current craft, and how many it needs. Enough to draw an arrow. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CRAFT_TICKS = 1;

    /** FE in the buffer. */
    public static final int DATA_ENERGY = 2;

    /**
     * What a tick of crafting costs and how much the buffer holds, which differ by tier - and by
     * the modules in it. Sent rather than read off a constant, because the client's menu knows
     * the machine's position and not its block, and a bar drawn against the wrong tier's capacity
     * would read as always full.
     */
    public static final int DATA_ENERGY_PER_TICK = 3;
    public static final int DATA_ENERGY_CAPACITY = 4;

    public static final int DATA_COUNT = 5;

    /**
     * Where the screen expects to find things. Shared, so the two cannot drift apart.
     *
     * <p>Everything is arranged around one horizontal centre line at y=35: the ingredient block
     * runs 17..53, the output well 26..44, and the progress bar sits between them. The first
     * attempt put the bar at x=74 while the third ingredient column ran to x=84, and drew one on
     * top of the other. The module slots stand to the right of the output, in a row, on the same
     * centre line.
     */
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 26;
    public static final int MODULE_X = 136;
    public static final int MODULE_Y = 26;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final Level level;

    /** How many of this menu's slots are the machine's; the player's follow. */
    private final int machineSlots;
    private final int moduleSlots;

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public AssemblerMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory,
                new AssemblerInventory(AssemblerBlockEntity.SLOT_COUNT, () -> {}),
                new ModuleSlots(moduleSlotsAt(playerInventory.player.level(), machinePos), () -> {}),
                new SimpleContainerData(DATA_COUNT),
                machinePos);
    }

    public AssemblerMenu(int containerId, Inventory playerInventory, AssemblerInventory inventory,
            ModuleSlots modules, ContainerData data, BlockPos machinePos) {
        super(ModMenus.ASSEMBLER.get(), containerId);
        this.data = data;
        this.machinePos = machinePos;
        this.level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, machinePos);

        // Two rows of three. The layout deliberately does not look like a crafting grid: an
        // assembler's recipe has no shape, only amounts, and a 3x3 would invite people to
        // arrange things in it.
        for (int index = 0; index < AssemblerBlockEntity.INPUT_SLOTS; index++) {
            addSlot(new ResourceHandlerSlot(inventory, inventory::set, index,
                    INPUT_X + (index % 3) * 18, INPUT_Y + (index / 3) * 18));
        }

        addSlot(new OutputSlot(inventory, AssemblerBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        moduleSlots = modules.size();
        for (int index = 0; index < moduleSlots; index++) {
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

    /** The tier's module slot count, read off the block, or none for a block that is not one of ours. */
    private static int moduleSlotsAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof AssemblerBlock block ? block.moduleSlots() : 0;
    }

    /**
     * The machine fills this; the player may only take from it.
     *
     * <p>Not just tidiness: the input slots are what the later per-ingredient buffers will
     * filter, and a writable output slot would be the hole in that filter.
     */
    private static class OutputSlot extends ResourceHandlerSlot {

        OutputSlot(AssemblerInventory inventory, int index, int x, int y) {
            super(inventory, inventory::set, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int craftTicks() {
        return data.get(DATA_CRAFT_TICKS);
    }

    /** 0 to 1 across the current craft, and 0 when the machine is idle. */
    public float craftProgress() {
        int total = craftTicks();
        return total <= 0 ? 0.0f : Math.clamp(progress() / (float) total, 0.0f, 1.0f);
    }

    public int energy() {
        return data.get(DATA_ENERGY);
    }

    /** 0 to 1 across the buffer. */
    public float charge() {
        int capacity = data.get(DATA_ENERGY_CAPACITY);
        if (capacity <= 0) {
            capacity = AssemblerBlockEntity.ENERGY_CAPACITY;  // not synced yet: the first tier's
        }
        return Math.clamp(energy() / (float) capacity, 0.0f, 1.0f);
    }

    /** Whether the machine has enough in the buffer to advance a craft by one tick. */
    public boolean hasPower() {
        int perTick = data.get(DATA_ENERGY_PER_TICK);
        return energy() >= (perTick <= 0 ? AssemblerBlockEntity.ENERGY_PER_TICK : perTick);
    }

    // ------------------------------------------------------------------ RecipeSelector

    /**
     * Server side only, and reached only through Facrafting's payload handler - which has already
     * checked that this player has this menu open and that the key names a real timed recipe.
     */
    @Override
    public void selectRecipe(@Nullable ResourceKey<Recipe<?>> recipe) {
        if (machine() instanceof AssemblerBlockEntity assembler) {
            assembler.setRecipe(recipe);
        }
    }

    @Override
    public @Nullable ResourceKey<Recipe<?>> selectedRecipe() {
        return machine() instanceof AssemblerBlockEntity assembler ? assembler.recipeKey() : null;
    }

    private @Nullable BlockEntity machine() {
        return level.getBlockEntity(machinePos);
    }

    // ------------------------------------------------------------------------- plumbing

    /**
     * Any tier, not one block. Vanilla's {@code stillValid(access, player, block)} compares the
     * block at the position with a single block, and with the first machine's block passed in
     * an assembling machine 2's screen closed on the tick after it opened. Nothing failed: the
     * menu was built, the packet went out, and the server shut it again before anyone could see.
     */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof AssemblerBlock
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
            // A module goes to the module slots and nowhere else: an ingredient slot would take
            // it and the machine would then sit waiting for a recipe that wants one.
            if (!moveItemStackTo(stack, AssemblerBlockEntity.SLOT_COUNT, machineSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, AssemblerBlockEntity.INPUT_SLOTS, false)) {
            // Ingredient slots only. Shift-clicking into the output would be a way to hand the
            // machine something it never made.
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
