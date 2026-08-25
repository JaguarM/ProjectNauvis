package com.jaguarm.nauvis.data;

import com.jaguarm.nauvis.ModContent;
import com.jaguarm.nauvis.Nauvis;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.data.PackOutput;

/**
 * Block and item models.
 *
 * <p>Textures are placeholders and deliberately point at vanilla ones: a missing texture
 * renders as the magenta-and-black checkerboard and is easy to mistake for a broken model.
 * Real art is Yannic's half, and swapping it is a one-line change here.
 */
public class NauvisModels extends ModelProvider {

    public NauvisModels(PackOutput output) {
        super(output, Nauvis.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.createTrivialCube(ModContent.ASSEMBLING_MACHINE_1.get());
    }
}
