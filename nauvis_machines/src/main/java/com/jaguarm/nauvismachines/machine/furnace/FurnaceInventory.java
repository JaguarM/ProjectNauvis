package com.jaguarm.nauvismachines.machine.furnace;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A furnace's three slots: what it smelts, what it burns, and what came out.
 *
 * <p>Unlike the assembler's, the slots are fussy, because a furnace has no recipe selector: what
 * it makes is decided by what is put in it, so the input slot takes only something a furnace can
 * smelt and the fuel slot only something that burns. Both rules are handed in as predicates by the
 * block entity, which is the one that can ask the recipe manager; an electric furnace hands in a
 * fuel rule that refuses everything, and its fuel slot is simply a slot nothing can go in. The
 * output slot takes anything, because the machine writes to it; automation is kept out of it by
 * {@link com.jaguarm.nauvismachines.machine.MachineAccess}.
 *
 * <p>{@code ResourceHandlerSlot} reads {@link #isValid} for {@code mayPlace} and the handler
 * reports zero capacity for what it rejects, so one override closes the screen, the hopper and
 * the inserter at once - which is Factorio's rule too, where an inserter holding something a
 * furnace cannot smelt simply waits.
 */
public class FurnaceInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;
    private final Predicate<ItemResource> smeltable;
    private final Predicate<ItemResource> fuel;

    public FurnaceInventory(Runnable onChanged, Predicate<ItemResource> smeltable,
            Predicate<ItemResource> fuel) {
        super(FurnaceBlockEntity.SLOT_COUNT);
        this.onChanged = onChanged;
        this.smeltable = smeltable;
        this.fuel = fuel;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return switch (index) {
            case FurnaceBlockEntity.INPUT_SLOT -> smeltable.test(resource);
            case FurnaceBlockEntity.FUEL_SLOT -> fuel.test(resource);
            default -> true;
        };
    }

    /** Every change is a reason to wake up: ore arriving, coal arriving, a plate taken away. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
