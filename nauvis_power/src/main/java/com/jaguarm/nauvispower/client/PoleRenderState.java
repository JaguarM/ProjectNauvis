package com.jaguarm.nauvispower.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * What {@link PoleWireRenderer} needs to know about one pole, pulled off the block entity once a
 * frame and then left alone.
 *
 * <p>26.2 splits a renderer in two: {@code extractRenderState} reads the world, {@code submit}
 * builds geometry and may not. This is the thing in between, and it is reused between frames -
 * hence {@link #wires} being cleared and refilled rather than replaced.
 */
public class PoleRenderState extends BlockEntityRenderState {

    /** Only the wires this pole is responsible for drawing - see the renderer. */
    public final List<Wire> wires = new ArrayList<>();

    /** Light where the wires attach, which is three blocks above the block entity. */
    public int headLight;

    /**
     * One wire, as an offset from this pole's own block origin to the far pole's.
     *
     * <p>An offset rather than a position because that is what the pose stack is already set up
     * for: by the time {@code submit} runs, the origin is this block's corner.
     */
    public record Wire(float dx, float dy, float dz) {}
}
