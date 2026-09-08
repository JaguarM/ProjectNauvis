package com.jaguarm.nauvismachines.machine.furnace;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** A furnace's three slots: what it smelts, what it burns, and what came out. */
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
