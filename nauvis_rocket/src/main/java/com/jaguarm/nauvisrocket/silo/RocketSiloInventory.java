package com.jaguarm.nauvisrocket.silo;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The silo's slots: three for what a rocket part is made of, one for the satellite, one for
 * what comes back.
 *
 * <p>The ingredient slots take what a rocket part is made of and nothing else, which is
 * Factorio's rule for a machine with a fixed recipe - and the reason a satellite offered to the
 * whole machine lands in the satellite slot rather than the first slot with room. The satellite
 * slot takes one thing and one of it, because a rocket carries one satellite and a stack of them
 * in the slot would be a hundred launches' worth of cargo sitting where an inserter could not
 * tell it had done its job. The output is the machine's to write.
 */
public class RocketSiloInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;
    private final Predicate<ItemResource> ingredient;
    private final Predicate<ItemResource> cargo;

    /**
     * @param ingredient whether a resource is something a rocket part is made of
     * @param cargo      whether a resource is something a rocket carries - the satellite, and nothing else
     */
    public RocketSiloInventory(Runnable onChanged, Predicate<ItemResource> ingredient, Predicate<ItemResource> cargo) {
        super(RocketSiloBlockEntity.SLOT_COUNT);
        this.onChanged = onChanged;
        this.ingredient = ingredient;
        this.cargo = cargo;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (index < RocketSiloBlockEntity.INPUT_SLOTS) {
            return ingredient.test(resource);
        }
        if (index == RocketSiloBlockEntity.SATELLITE_SLOT) {
            return cargo.test(resource);
        }
        return super.isValid(index, resource);
    }

    /** One satellite at a time. */
    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return index == RocketSiloBlockEntity.SATELLITE_SLOT ? 1 : super.getCapacity(index, resource);
    }

    /** Every change is a reason to wake up: ingredients arriving, a satellite, science taken away. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
