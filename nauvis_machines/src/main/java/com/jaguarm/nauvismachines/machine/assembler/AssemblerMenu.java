package com.jaguarm.nauvismachines.machine.assembler;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.machine.RecipeSelector;
import com.jaguarm.nauvismachines.registry.ModBlocks;
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
 * The assembler's menu: six ingredient slots, one output slot, and the machine's progress.
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
 */
public class AssemblerMenu extends AbstractContainerMenu implements RecipeSelector {

    /** Ticks into the current craft, and how many it needs. Enough to draw an arrow. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_CRAFT_TICKS = 1;
    public static final int DATA_COUNT = 2;

    /** Where the screen expects to find things. Shared, so the two cannot drift apart. */
    public static final int INPUT_X = 30;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 35;

    private static final int PLAYER_SLOTS = 36;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos machinePos;
    private final Level level;

    /** Client side: NeoForge's menu factory hands the machine's position across. */
    public AssemblerMenu(int containerId, Inventory playerInventory, BlockPos machinePos) {
        this(containerId, playerInventory,
                new AssemblerInventory(AssemblerBlockEntity.SLOT_COUNT, () -> {}),
                new SimpleContainerData(DATA_COUNT),
                machinePos);
    }

    public AssemblerMenu(int containerId, Inventory playerInventory, AssemblerInventory inventory,
            ContainerData data, BlockPos machinePos) {
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

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ASSEMBLING_MACHINE_1.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = AssemblerBlockEntity.SLOT_COUNT;
        int inventoryEnd = machineEnd + PLAYER_SLOTS;

        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, inventoryEnd, true)) {
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
