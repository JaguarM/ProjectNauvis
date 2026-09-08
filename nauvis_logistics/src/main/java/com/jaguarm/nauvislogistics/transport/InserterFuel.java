package com.jaguarm.nauvislogistics.transport;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The burner inserter's one slot: what it burns. */
public class InserterFuel extends ItemStacksResourceHandler {

    private final Runnable onChanged;
    private final Supplier<@Nullable FuelValues> fuelValues;

    public InserterFuel(int size, Runnable onChanged, Supplier<@Nullable FuelValues> fuelValues) {
        super(size);
        this.onChanged = onChanged;
        this.fuelValues = fuelValues;
    }

    /**
     * Whether this would burn.
     *
     * <p>Permissive when there is no level to ask - a block entity deserialises before it has one,
     * and an inserter that threw away its own saved coal on load would be a memorable bug.
     */
    @Override
    public boolean isValid(int index, ItemResource resource) {
        FuelValues values = fuelValues.get();
        return values == null || resource.toStack(1).getBurnTime(null, values) > 0;
    }

    /** Coal arriving is the one thing that can restart an inserter that ran dry. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
