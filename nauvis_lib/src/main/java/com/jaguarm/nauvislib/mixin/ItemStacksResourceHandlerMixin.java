package com.jaguarm.nauvislib.mixin;

import com.jaguarm.nauvislib.item.Stacks;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's slot capacity is the smaller of the item's stack size and
 * {@code Item.ABSOLUTE_MAX_STACK_SIZE} - a compile-time constant, inlined, so the field cannot
 * be changed from outside and the method is answered instead: the item's stack size, or the
 * ceiling for an empty slot. Every chest, furnace and machine inventory of the pack's is one of
 * these, and a subclass with a rule of its own still gets this through {@code super}.
 */
@Mixin(ItemStacksResourceHandler.class)
public abstract class ItemStacksResourceHandlerMixin {

    @Inject(method = "getCapacity(ILnet/neoforged/neoforge/transfer/item/ItemResource;)I", at = @At("HEAD"), cancellable = true)
    private void nauvis_lib$liftTheStackCeiling(int index, ItemResource resource, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(resource.isEmpty() ? Stacks.CEILING : resource.getMaxStackSize());
    }
}
