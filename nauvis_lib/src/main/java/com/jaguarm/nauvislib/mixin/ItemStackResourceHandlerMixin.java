package com.jaguarm.nauvislib.mixin;

import com.jaguarm.nauvislib.item.Stacks;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The single-stack handler, for the same reason as {@link ItemStacksResourceHandlerMixin}. */
@Mixin(ItemStackResourceHandler.class)
public abstract class ItemStackResourceHandlerMixin {

    @Inject(method = "getCapacity(Lnet/neoforged/neoforge/transfer/item/ItemResource;)I", at = @At("HEAD"), cancellable = true)
    private void nauvis_lib$liftTheStackCeiling(ItemResource resource, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(resource.isEmpty() ? Stacks.CEILING : resource.getMaxStackSize());
    }
}
