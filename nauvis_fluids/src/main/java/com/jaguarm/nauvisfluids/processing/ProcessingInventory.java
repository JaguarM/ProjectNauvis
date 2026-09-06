package com.jaguarm.nauvisfluids.processing;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A processing machine's item slots: inputs first, then outputs, as the layout counts them.
 *
 * <p>Unrestricted, like the assembler's. What automation may do with it is
 * {@link com.jaguarm.nauvislib.transfer.MachineAccess}'s to say; the machine itself has to be
 * able to spend from its inputs and write to its output. A refinery has none of these, and gets
 * one of size zero, which is simpler than a null.
 */
public class ProcessingInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public ProcessingInventory(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    /** Every change is a reason to wake up: coal arriving can start a craft, plastic leaving can unblock one. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
