package com.jaguarm.nauvisfluids.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.AABB;

/**
 * What {@link CrudeOilRenderer} needs to know about one well, pulled off the world once a frame.
 *
 * <p>26.2 splits a renderer in two: {@code extractRenderState} reads the world, {@code submit}
 * builds geometry and may not. This is the thing in between, and it is reused between frames.
 */
public class CrudeOilRenderState extends BlockEntityRenderState {

    /** Whether the local player is holding a pumpjack, which is the only time anything is drawn. */
    public boolean show;

    /**
     * The footprint a pumpjack placed at the crosshair would take, relative to this well's block,
     * when the placement would snap to this well - or null when it would not.
     */
    public @Nullable AABB footprint;
}
