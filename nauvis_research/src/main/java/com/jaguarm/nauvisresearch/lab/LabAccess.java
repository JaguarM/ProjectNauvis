package com.jaguarm.nauvisresearch.lab;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What an inserter or a hopper sees: science packs go in, and nothing ever comes out.
 *
 * <p>A lab consumes what it is given. Without the one-way rule a hopper under a lab would pull
 * the packs straight back out of it, which is the same trap the assembler's automation view
 * exists to avoid - and here it would be worse, because there is no output slot to tell the two
 * apart by.
 */
public record LabAccess(ResourceHandler<ItemResource> backing) implements ResourceHandler<ItemResource> {

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

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return backing.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return backing.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return backing.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        return backing.insert(resource, amount, transaction);
    }

    /** Always nothing, and the whole point of this wrapper. */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
