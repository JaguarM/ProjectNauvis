package com.jaguarm.nauvisfluids.data;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.registry.ModBlocks;

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
 * Placeholder models, pointing at vanilla textures so a missing one cannot be mistaken for the
 * magenta checkerboard.
 *
 * <p>Iron sides with a furnace top on each end: metal, and round enough at the ends to read as
 * something hollow rather than as a block of iron.
 */
public class NauvisFluidsModels extends ModelProvider {

    public NauvisFluidsModels(PackOutput output) {
        super(output, NauvisFluids.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier pipe = ModelTemplates.CUBE_COLUMN.create(
                ModBlocks.PIPE.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.IRON_BLOCK))
                        .put(TextureSlot.END, TextureMapping.getBlockTexture(Blocks.FURNACE, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(ModBlocks.PIPE.get(), BlockModelGenerators.plainVariant(pipe)));
    }
}
