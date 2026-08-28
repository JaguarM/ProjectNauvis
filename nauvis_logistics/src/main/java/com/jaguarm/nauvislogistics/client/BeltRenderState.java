package com.jaguarm.nauvislogistics.client;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * What one belt block has to draw this frame.
 *
 * <p>The lists are grown and reused rather than rebuilt. A belt block is asked for this sixty
 * times a second and holds up to eight items, so a base in view is thousands of these a frame;
 * allocating a fresh {@code ItemStackRenderState} for each of them - which is what the campfire
 * does for its four - would be a lot of rubbish for the collector to sweep up every frame.
 * {@link #used} says how many of the pooled entries this frame actually filled.
 */
public class BeltRenderState extends BlockEntityRenderState {

    /** Item models, pooled. Only the first {@link #used} are meaningful. */
    public final List<ItemStackRenderState> models = new ArrayList<>();

    /** Where each item sits, relative to the corner of this belt's own block. */
    public final List<Vec3> offsets = new ArrayList<>();

    /** Which way each item is pointing, so a line of plates all lie the same way. */
    public final FloatArrayList yaws = new FloatArrayList();

    public int used;

    /** Hands back the pooled model at {@code index}, making one if the pool is not that deep yet. */
    public ItemStackRenderState model(int index) {
        while (models.size() <= index) {
            models.add(new ItemStackRenderState());
            offsets.add(Vec3.ZERO);
            yaws.add(0.0F);
        }
        return models.get(index);
    }
}
