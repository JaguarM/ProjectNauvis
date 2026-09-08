package com.jaguarm.nauvislib.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Two piles on the ground merge up to a literal sixty-four, whatever the item stacks to; now up
 * to the item's stack, so a chest of circuits broken open becomes one pile rather than four.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @ModifyArg(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/ItemEntity;merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;"),
            index = 2)
    private static int nauvis_lib$mergeUpToTheStack(ItemStack toStack, ItemStack fromStack, int maxCount) {
        return Math.max(maxCount, toStack.getMaxStackSize());
    }
}
