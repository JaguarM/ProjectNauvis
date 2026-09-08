package com.jaguarm.nauvislib.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The count in the corner of a slot is drawn for one or two digits, right-aligned at the slot's
 * edge; a hundred or two thousand runs out of the slot to the left. A count wider than the slot
 * is scaled down to fit, bottom and right where vanilla puts it. One- and two-digit counts are
 * left to vanilla untouched.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    /** The slot's width, and the widest count vanilla draws as it is. */
    private static final int SLOT = 16;

    @Shadow @Final private Matrix3x2fStack pose;

    @Shadow public abstract void text(Font font, String str, int x, int y, int color, boolean dropShadow);

    @Inject(method = "itemCount", at = @At("HEAD"), cancellable = true)
    private void nauvis_lib$fitTheCount(Font font, ItemStack itemStack, int x, int y, @Nullable String countText, CallbackInfo ci) {
        if (itemStack.getCount() == 1 && countText == null) {
            return;
        }
        String amount = countText == null ? String.valueOf(itemStack.getCount()) : countText;
        int width = font.width(amount);
        if (width <= SLOT) {
            return;
        }
        float scale = (float) SLOT / width;
        pose.pushMatrix();
        // Vanilla's text sits at (x + 17 - width, y + 9), eight tall; this keeps its right edge and its baseline.
        pose.translate(x + 17 - SLOT, y + 17 - 8 * scale);
        pose.scale(scale, scale);
        text(font, amount, 0, 0, -1, true);
        pose.popMatrix();
        ci.cancel();
    }
}
