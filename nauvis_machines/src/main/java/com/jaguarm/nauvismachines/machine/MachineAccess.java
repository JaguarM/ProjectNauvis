package com.jaguarm.nauvismachines.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's inventory as the outside world is allowed to use it: put things in the front,
 * take things out of the back.
 *
 * <p>A machine's own inventory is one flat list of indices, and it has to stay unrestricted so
 * the machine can spend its ingredients and bank its results. This is the view published as
 * {@code Capabilities.Item.BLOCK} instead. Insertion is confined to the first
 * {@code inputSlots} indices and extraction to everything after them, so a hopper under an
 * assembler takes the product rather than draining the ingredients back out.
 *
 * <p>Only the index-addressed methods need restricting: {@link ResourceHandler}'s
 * whole-handler {@code insert} and {@code extract} defaults walk every index and call these,
 * so they inherit the same rule.
 */
public record MachineAccess(ResourceHandler<ItemResource> backing, int inputSlots)
        implements ResourceHandler<ItemResource> {

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return backing.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return backing.getAmountAsLong(index);
    }

    /**
     * Zero for anything {@link #isValid} rejects, which the handler contract requires and
     * which is how a hopper aiming at an output slot learns to stop trying.
     */
    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() || isValid(index, resource)
                ? backing.getCapacityAsLong(index, resource)
                : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index < inputSlots && backing.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index < inputSlots ? backing.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= inputSlots ? backing.extract(index, resource, amount, transaction) : 0;
    }
}
