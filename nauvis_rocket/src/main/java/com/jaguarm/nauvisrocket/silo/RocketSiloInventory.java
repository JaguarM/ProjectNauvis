package com.jaguarm.nauvisrocket.silo;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The silo's slots: three for what a rocket part is made of, one for the satellite, one for
 * what comes back.
 *
 * <p>Each ingredient slot takes one ingredient of the rocket part - the first slot the first,
 * and so on - and nothing else, which is Factorio's rule for a machine with a fixed recipe. It is
 * what keeps an inserter from filling all three with low density structures and stalling the
 * silo, and the reason a satellite offered to the whole machine lands in the satellite slot rather
 * than the first slot with room. A slot holds twice what a part takes of its ingredient, or a
 * stack if that is more, as an assembler's does - a low density structure stacks to ten and a
 * part takes ten, so a slot that stopped at a stack would run dry the moment a part began. The
 * satellite slot takes one thing and one of it, because a
 * rocket carries one satellite and a stack of them in the slot would be a hundred launches'
 * worth of cargo sitting where an inserter could not tell it had done its job. The output is the
 * machine's to write.
 */
public class RocketSiloInventory extends ItemStacksResourceHandler {

    /** How many of a resource one rocket part takes at this slot: its ingredient there, or zero. */
    public interface Wants {
        int of(int slot, ItemResource resource);
    }

    private final Runnable onChanged;
    private final Wants wanted;
    private final Predicate<ItemResource> cargo;

    /**
     * @param wanted how many of a resource a rocket part takes in this slot, or zero for a resource
     *               that is not the slot's ingredient
     * @param cargo  whether a resource is something a rocket carries - the satellite, and nothing else
     */
    public RocketSiloInventory(Runnable onChanged, Wants wanted, Predicate<ItemResource> cargo) {
        super(RocketSiloBlockEntity.SLOT_COUNT);
        this.onChanged = onChanged;
        this.wanted = wanted;
        this.cargo = cargo;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (index < RocketSiloBlockEntity.INPUT_SLOTS) {
            return !resource.isEmpty() && wanted.of(index, resource) > 0;
        }
        if (index == RocketSiloBlockEntity.SATELLITE_SLOT) {
            return cargo.test(resource);
        }
        return super.isValid(index, resource);
    }

    /** Twice a part's worth in an ingredient slot, or a stack if that is more; one satellite at a time. */
    @Override
    protected int getCapacity(int index, ItemResource resource) {
        if (index < RocketSiloBlockEntity.INPUT_SLOTS) {
            return Math.max(super.getCapacity(index, resource), 2 * wanted.of(index, resource));
        }
        return index == RocketSiloBlockEntity.SATELLITE_SLOT ? 1 : super.getCapacity(index, resource);
    }

    /** Every change is a reason to wake up: ingredients arriving, a satellite, science taken away. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
