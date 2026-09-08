package com.jaguarm.nauvislib.mixin;

import com.mojang.logging.LogUtils;

import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A handler's size is its constructor's, not its save's. */
@Mixin(StacksResourceHandler.class)
public abstract class StacksResourceHandlerMixin<S> {

    @Unique
    private static final Logger NAUVIS_LIB$LOGGER = LogUtils.getLogger();

    @Shadow protected NonNullList<S> stacks;
    @Shadow @Final protected S emptyStack;

    @Shadow
    private void updateStacksSize() {
        throw new AssertionError("shadowed");
    }

    @Unique
    private int nauvis_lib$sizeBefore;

    @Inject(method = "deserialize", at = @At("HEAD"))
    private void nauvis_lib$rememberTheSize(ValueInput input, CallbackInfo ci) {
        nauvis_lib$sizeBefore = stacks.size();
    }

    @Inject(method = "deserialize", at = @At("RETURN"))
    private void nauvis_lib$keepTheSize(ValueInput input, CallbackInfo ci) {
        int size = nauvis_lib$sizeBefore;
        if (stacks.size() == size) {
            return;
        }
        NAUVIS_LIB$LOGGER.warn("A saved inventory has {} slots and its handler {}; keeping the handler's",
                stacks.size(), size);
        NonNullList<S> resized = NonNullList.withSize(size, emptyStack);
        for (int index = 0; index < Math.min(size, stacks.size()); index++) {
            resized.set(index, stacks.get(index));
        }
        stacks = resized;
        updateStacksSize();
    }
}
