package com.jaguarm.nauvismachines.data;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.data.PackOutput;

/**
 * Block and item models.
 *
 * <p>Textures are placeholders and deliberately point at vanilla ones: a missing texture renders
 * as the magenta-and-black checkerboard, which reads as a broken model rather than as art that
 * has not been made yet. Real art is Yannic's half - see
 * {@code ../NeoProgressiveAutomation/texture-workshop/} for the approach that produced the
 * drills - and swapping it in is a one-line change here.
 */
public class NauvisMachinesModels extends ModelProvider {

    public NauvisMachinesModels(PackOutput output) {
        super(output, NauvisMachines.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.createTrivialCube(ModBlocks.ASSEMBLING_MACHINE_1.get());
    }
}
