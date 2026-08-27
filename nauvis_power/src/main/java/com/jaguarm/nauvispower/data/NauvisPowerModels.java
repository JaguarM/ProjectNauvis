package com.jaguarm.nauvispower.data;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.registry.ModBlocks;

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
 * Placeholder models on vanilla textures, so a missing one cannot be mistaken for the magenta
 * checkerboard. A brick body for the boiler because it burns things, iron with a furnace top for
 * the engine because it is machinery.
 */
public class NauvisPowerModels extends ModelProvider {

    public NauvisPowerModels(PackOutput output) {
        super(output, NauvisPower.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier boiler = ModelTemplates.CUBE_BOTTOM_TOP.create(
                ModBlocks.BOILER.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.BRICKS))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.FURNACE, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(Blocks.BRICKS)),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(
                ModBlocks.BOILER.get(), BlockModelGenerators.plainVariant(boiler)));

        Identifier engine = ModelTemplates.CUBE_COLUMN.create(
                ModBlocks.STEAM_ENGINE.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.IRON_BLOCK))
                        .put(TextureSlot.END, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(
                ModBlocks.STEAM_ENGINE.get(), BlockModelGenerators.plainVariant(engine)));

        // A fence post is a 4x16x4 column, which is the shape SmallElectricPoleBlock collides
        // with - so the model and the hitbox are the same object rather than two numbers that
        // can drift. Stripped oak because a pole is planks and wire.
        Identifier pole = ModelTemplates.FENCE_POST.create(
                ModBlocks.SMALL_ELECTRIC_POLE.get(),
                new TextureMapping().put(TextureSlot.TEXTURE,
                        TextureMapping.getBlockTexture(Blocks.STRIPPED_OAK_LOG)),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(
                ModBlocks.SMALL_ELECTRIC_POLE.get(), BlockModelGenerators.plainVariant(pole)));
        blockModels.registerSimpleItemModel(ModBlocks.SMALL_ELECTRIC_POLE.get(), pole);
    }
}
