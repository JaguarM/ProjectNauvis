package com.jaguarm.nauvisresearch.lab;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The lab's slots: one per kind of science pack. */
public class LabInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public LabInventory(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    /** A pack arriving is the thing that gives a stopped lab work again. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
