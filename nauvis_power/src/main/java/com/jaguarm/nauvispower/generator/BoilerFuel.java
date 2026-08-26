package com.jaguarm.nauvispower.generator;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The boiler's one slot: what it burns. */
public class BoilerFuel extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public BoilerFuel(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    /** Coal arriving is the one thing that can restart a boiler that ran dry. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
