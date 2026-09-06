package com.jaguarm.nauvisfluids.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * What {@link CrudeOilRenderer} needs to know about one well, pulled off the world once a frame.
 *
 * <p>26.2 splits a renderer in two: {@code extractRenderState} reads the world, {@code submit}
 * builds geometry and may not. This is the thing in between, and it is reused between frames.
 */
public class CrudeOilRenderState extends BlockEntityRenderState {

    /** Whether the local player is holding a pumpjack, which is the only time anything is drawn. */
    public boolean show;
}
