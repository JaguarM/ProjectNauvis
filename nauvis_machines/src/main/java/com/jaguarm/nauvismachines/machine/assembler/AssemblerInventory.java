package com.jaguarm.nauvismachines.machine.assembler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The assembler's slots: ingredients in the first {@link AssemblerBlockEntity#INPUT_SLOTS},
 * results after them.
 *
 * <p>Deliberately unrestricted — any slot will take anything. Restricting is
 * {@link com.jaguarm.nauvislib.transfer.MachineAccess}'s job, on the view published to
 * automation; the machine itself has to be able to spend from its inputs and write to its
 * output. Filtering inputs by the selected recipe, the way Factorio does, belongs with the
 * per-ingredient buffers that replace this whole class later.
 *
 * <p>Six input slots is not a round number. Six is the most ingredients any of Factorio's 214
 * recipes asks for — the satellite — so six slots can always hold one craft's worth.
 */
public class AssemblerInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public AssemblerInventory(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    /**
     * Every change is a reason to wake up: ingredients arriving can start a craft, and a
     * result being taken away can unblock one. See {@link AssemblerBlockEntity} on sleeping.
     */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
