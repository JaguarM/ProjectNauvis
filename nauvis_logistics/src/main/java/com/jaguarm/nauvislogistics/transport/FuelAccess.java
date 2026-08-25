package com.jaguarm.nauvislogistics.transport;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The inserter's fuel slot as the outside world may use it: coal in, nothing out.
 *
 * <p>Feeding burner inserters with other burner inserters is how a Factorio base starts, so the
 * slot has to be reachable. Letting anything take the coal back out is not part of that - an
 * inserter aimed at another inserter would otherwise sit there passing one lump of coal back and
 * forth forever.
 *
 * <p>A near-twin of {@code nauvis_machines}' {@code MachineAccess}, and deliberately a copy
 * rather than a shared class. Non-negotiable #3 wants each subsystem mod standalone, and sixty
 * lines duplicated is a much smaller price than a dependency between two subsystems - or a
 * library mod that would then have to ship with both.
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

    /** Always nothing. The whole point of this wrapper. */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
