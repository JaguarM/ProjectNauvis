package com.jaguarm.nauvislogistics.transport;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The inserter's one slot: what it burns. */
public class InserterFuel extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public InserterFuel(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    /** Coal arriving is the one thing that can restart an inserter that ran dry. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
