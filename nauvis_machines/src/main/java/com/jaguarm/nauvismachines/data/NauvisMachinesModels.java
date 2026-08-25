package com.jaguarm.nauvismachines.data;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/**
 * Block and item models.
 *
 * <p>Textures are placeholders and point at <em>vanilla</em> ones, which is not the same thing as
 * leaving them out. A model naming a texture this mod does not ship renders as the magenta-and-
 * black checkerboard, and that reads as a broken model rather than as art nobody has drawn yet -
 * which matters, because the person judging whether a machine looks right is looking at it in
 * game. A blast furnace body is the nearest vanilla thing to an assembler.
 *
 * <p>Real art is Yannic's half - see {@code ../NeoProgressiveAutomation/texture-workshop/} for the
 * approach that produced the drills - and swapping it in is a one-line change here.
 */
public class NauvisMachinesModels extends ModelProvider {

    public NauvisMachinesModels(PackOutput output) {
        super(output, NauvisMachines.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                ModBlocks.ASSEMBLING_MACHINE_1.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_side"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(
                ModBlocks.ASSEMBLING_MACHINE_1.get(), BlockModelGenerators.plainVariant(model)));
    }
}
