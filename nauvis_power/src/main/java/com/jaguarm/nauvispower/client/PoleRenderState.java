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

    /** Light where the wires attach, which is the top of this pole. */
    public int headLight;

    /** One wire: both of its ends, as offsets from this pole's own block origin. */
    public record Wire(float x0, float y0, float z0, float x1, float y1, float z1) {}
}
