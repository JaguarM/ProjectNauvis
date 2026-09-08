package com.jaguarm.nauvislib.mixin;

import com.jaguarm.nauvislib.item.Stacks;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code Container.getMaxStackSize()} is a default method answering ninety-nine, and Mixin
 * cannot inject into an interface, so this class answers the ceiling itself and the default is
 * never reached. A slot's real limit is still the smaller of this and the item's own stack size.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    public int getMaxStackSize() {
        return Stacks.CEILING;
    }
}
