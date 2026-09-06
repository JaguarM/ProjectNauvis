package com.jaguarm.nauvisfluids.processing;

import com.jaguarm.facrafting.machine.RecipeSelector;
import com.jaguarm.facrafting.recipe.FacraftRecipe;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The menu of a refinery or a chemical plant: its item slots, if it has any, and everything the
 * screen draws as a number - progress, charge, status, and what is in each tank.
 *
 * <p>Implements {@link RecipeSelector}, which is what makes Facrafting's panel change its mind
 * about what a click means: with this menu open, clicking a recipe points the machine at it, and
 * the panel offers only the recipes {@link #accepts} says this machine runs. There is deliberately
 * no recipe list in the screen itself.
 *
 * <p>A tank travels as two ints, its amount and its fluid's registry id. The fluid registry is
 * synced to the client in the server's order, so the id means the same thing on both sides, and
 * the client turns it back into a fluid to draw the bar in that fluid's colour.
 */
public abstract class ProcessingMenu extends AbstractContainerMenu implements RecipeSelector {

    /** Ticks into the current craft, and how many it needs. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CRAFT_TICKS = 1;
    /** FE in the buffer, and how much it holds. */
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_CAPACITY = 3;
    /** {@link ProcessingStatus}, by ordinal. */
    public static final int DATA_STATUS = 4;
    /** From here: amount and fluid id for each tank, inputs first. */
    public static final int DATA_TANKS = 5;

    public static int dataCount(ProcessingLayout layout) {
        return DATA_TANKS + 2 * layout.tankCount();
    }

    private static final int PLAYER_SLOTS = 36;

    protected final ProcessingLayout layout;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final Level level;

    /**
     * @param inputSlots  x, y of each item input slot, in the screen's frame
     * @param outputSlots x, y of each item output slot
     */
    protected ProcessingMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            ProcessingLayout layout, ItemStacksResourceHandler items, int[][] inputSlots,
            int[][] outputSlots, ContainerData data, BlockPos machinePos) {
        super(type, containerId);
        this.layout = layout;
        this.data = data;
        this.machinePos = machinePos;
        this.level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, machinePos);
        for (int index = 0; index < layout.itemInputs(); index++) {
            addSlot(new ResourceHandlerSlot(items, items::set, index, inputSlots[index][0], inputSlots[index][1]));
        }
        for (int index = 0; index < layout.itemOutputs(); index++) {
            addSlot(new OutputSlot(items, layout.itemInputs() + index,
                    outputSlots[index][0], outputSlots[index][1]));
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
        OutputSlot(ItemStacksResourceHandler items, int index, int x, int y) {
            super(items, items::set, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public ProcessingLayout layout() {
        return layout;
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    /** 0 to 1 across the current craft, and 0 when the machine is idle. */
    public float craftProgress() {
        int total = data.get(DATA_CRAFT_TICKS);
        return total <= 0 ? 0.0f : Math.clamp(progress() / (float) total, 0.0f, 1.0f);
    }

    /** 0 to 1 across the buffer. */
    public float charge() {
        int capacity = data.get(DATA_ENERGY_CAPACITY);
        return capacity <= 0 ? 0.0f : Math.clamp(data.get(DATA_ENERGY) / (float) capacity, 0.0f, 1.0f);
    }

    public ProcessingStatus status() {
        return ProcessingStatus.byOrdinal(data.get(DATA_STATUS));
    }

    /** How much is in tank {@code index}, inputs first. */
    public int tankAmount(int index) {
        return data.get(DATA_TANKS + 2 * index);
    }

    /** 0 to 1 across the tank. */
    public float tankFill(int index) {
        return Math.clamp(tankAmount(index) / (float) ProcessingBlockEntity.TANK_CAPACITY, 0.0f, 1.0f);
    }

    /** What tank {@code index} holds or is waiting for; {@code minecraft:empty} for neither. */
    public Fluid tankFluid(int index) {
        return BuiltInRegistries.FLUID.byId(data.get(DATA_TANKS + 2 * index + 1));
    }

    // ------------------------------------------------------------------ RecipeSelector

    /** Server side only, and reached only through Facrafting's payload handler. */
    @Override
    public void selectRecipe(@Nullable ResourceKey<Recipe<?>> recipe) {
        if (machine() instanceof ProcessingBlockEntity machine) {
            machine.setRecipe(recipe);
        }
    }

    @Override
    public @Nullable ResourceKey<Recipe<?>> selectedRecipe() {
        return machine() instanceof ProcessingBlockEntity machine ? machine.recipeKey() : null;
    }

    /** This machine's category, and no more of anything than it has ports for. */
    @Override
    public boolean accepts(FacraftRecipe recipe) {
        return layout.accepts(recipe);
    }

    private @Nullable BlockEntity machine() {
        return level.getBlockEntity(machinePos);
    }

    // ------------------------------------------------------------------------- plumbing

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof ProcessingBlock
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
        int machineEnd = layout.itemSlots();
        int inventoryEnd = machineEnd + PLAYER_SLOTS;
        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (layout.itemInputs() == 0 || !moveItemStackTo(stack, 0, layout.itemInputs(), false)) {
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
