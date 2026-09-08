package com.jaguarm.nauvislib.transfer;

import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's inventory as the outside world is allowed to use it: put things in the front, no
 * further than the machine's rule for automation says, and take things out of the back.
 */
public record MachineAccess(ResourceHandler<ItemResource> backing, int inputSlots, @Nullable Limit limit)
        implements ResourceHandler<ItemResource> {

    /** How many of a resource automation may leave in a slot; {@link Integer#MAX_VALUE} for no rule. */
    public interface Limit {
        int of(int index, ItemResource resource);
    }

    /** An inserter's swing, in thirds of a tick: Factorio's 1.166 seconds. */
    private static final int SWING_THIRD_TICKS = 70;

    public MachineAccess(ResourceHandler<ItemResource> backing, int inputSlots) {
        this(backing, inputSlots, null);
    }

    /**
     * Factorio's automated insertion limit for one ingredient: a craft's worth, plus the crafts
     * that finish inside one inserter swing, never fewer than two crafts nor more than a hundred.
     */
    public static int insertionLimit(int perCraft, int craftTicks) {
        int crafts = Math.clamp(1 + Math.ceilDiv(SWING_THIRD_TICKS, 3 * Math.max(1, craftTicks)), 2, 100);
        return perCraft * crafts;
    }

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
        long capacity = resource.isEmpty() || isValid(index, resource)
                ? backing.getCapacityAsLong(index, resource)
                : 0;
        return limit == null || resource.isEmpty() ? capacity : Math.min(capacity, limit.of(index, resource));
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index < inputSlots && backing.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index >= inputSlots) {
            return 0;
        }
        if (limit != null) {
            int room = limit.of(index, resource) - backing.getAmountAsInt(index);
            if (room <= 0) {
                return 0;
            }
            amount = Math.min(amount, room);
        }
        return backing.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= inputSlots ? backing.extract(index, resource, amount, transaction) : 0;
    }
}
