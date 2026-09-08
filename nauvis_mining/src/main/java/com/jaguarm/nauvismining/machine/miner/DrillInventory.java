package com.jaguarm.nauvismining.machine.miner;

import java.util.function.Predicate;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** A drill's three slots: what it burns, what it digs with, and what it dug. */
public class DrillInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;
    private final Predicate<ItemResource> fuel;

    public DrillInventory(Runnable onChanged, Predicate<ItemResource> fuel) {
        super(MinerBlockEntity.SLOT_COUNT);
        this.onChanged = onChanged;
        this.fuel = fuel;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return switch (index) {
            case MinerBlockEntity.FUEL_SLOT -> fuel.test(resource);
            case MinerBlockEntity.PICKAXE_SLOT -> resource.toStack(1).is(ItemTags.PICKAXES);
            default -> true;
        };
    }

    /**
     * Every change is a reason to wake up: coal arriving, a pickaxe put in or swapped for a
     * better one, ore taken away from a drill that had filled up.
     */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
