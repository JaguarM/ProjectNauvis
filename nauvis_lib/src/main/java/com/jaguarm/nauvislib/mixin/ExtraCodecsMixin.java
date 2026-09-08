package com.jaguarm.nauvislib.mixin;

import com.jaguarm.nauvislib.item.Stacks;
import com.mojang.serialization.Codec;

import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The count codecs: {@code ItemStack}, {@code ItemStackTemplate} and the {@code max_stack_size}
 * component each build their count as {@code ExtraCodecs.intRange(1, 99)} inside a lambda, so
 * the range is lifted where it is made. Nothing else in Minecraft or NeoForge asks for exactly
 * one to ninety-nine.
 */
@Mixin(ExtraCodecs.class)
public abstract class ExtraCodecsMixin {

    @Inject(method = "intRange(II)Lcom/mojang/serialization/Codec;", at = @At("HEAD"), cancellable = true)
    private static void nauvis_lib$liftTheStackCeiling(int min, int max, CallbackInfoReturnable<Codec<Integer>> cir) {
        if (min == 1 && max == Stacks.VANILLA_CEILING) {
            cir.setReturnValue(ExtraCodecs.intRange(1, Stacks.CEILING));
        }
    }
}
