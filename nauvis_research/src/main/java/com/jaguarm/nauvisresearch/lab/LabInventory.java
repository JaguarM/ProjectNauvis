package com.jaguarm.nauvisresearch.lab;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The lab's slots: one per kind of science pack.
 *
 * <p>Six of them, which is Factorio's number - a lab has a slot for each of the six packs and a
 * technology draws one from each slot it needs. Only red science exists in the pack today; the
 * other five slots are the shape the machine will keep, and they are cheaper to have now than to
 * add later, because a slot count is in every saved lab.
 *
 * <p>Unrestricted: the lab has to be able to spend from its own slots. What automation sees is
 * {@link LabAccess}, which takes packs in and never lets them back out.
 */
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
