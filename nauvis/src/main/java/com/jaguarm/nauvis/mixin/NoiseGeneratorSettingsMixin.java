package com.jaguarm.nauvis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Turns vanilla's noise ore veins off. They are not features, so no biome modifier reaches them,
 * and they are the same iron and copper blocks the patches are made of: a snaking vein sixty
 * blocks long under a patch is a second, spread-out ore body the drill and the x-ray cannot tell
 * from the first. Pack policy, so it lives in the pack mod; a world's own noise settings still
 * say what they say and are simply not asked.
 */
@Mixin(NoiseGeneratorSettings.class)
public abstract class NoiseGeneratorSettingsMixin {

    @Inject(method = "oreVeinsEnabled", at = @At("HEAD"), cancellable = true)
    private void nauvis$noOreVeins(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
