package com.jaguarm.nauvispower.generator;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The boiler's fuel slot as automation may use it: coal in, nothing out.
 *
 * <p>A near-twin of the same class in {@code nauvis_logistics}, and a deliberate copy rather than
 * a shared one. Non-negotiable #3 wants each subsystem mod to stand alone, and Facrafting is the
 * only place shared code is allowed to live - a capability wrapper is not a crafting concern, so
 * sixty duplicated lines is the cheaper of the two wrong answers.
 */
public record FuelAccess(ResourceHandler<ItemResource> backing) implements ResourceHandler<ItemResource> {

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

    /** Always nothing: an inserter feeding a boiler must not empty it again next swing. */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
