package com.jaguarm.nauvislogistics.data;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/**
 * Block and item models.
 *
 * <p>Textures are placeholders and point at <em>vanilla</em> ones on purpose. A model naming a
 * texture this mod does not ship renders as the magenta-and-black checkerboard, which reads as a
 * broken model rather than as art nobody has drawn yet - and the difference matters when the
 * person judging whether a machine looks right is looking at it in game.
 *
 * <p>A furnace body gives the inserter a distinct front face, which is the one thing its model
 * genuinely has to communicate: an inserter that is facing the wrong way looks exactly like one
 * that is working. Real art is Yannic's half; see
 * {@code ../NeoProgressiveAutomation/texture-workshop/}.
 */
public class NauvisLogisticsModels extends ModelProvider {

    public NauvisLogisticsModels(PackOutput output) {
        super(output, NauvisLogistics.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier model = ModelTemplates.CUBE_ORIENTABLE.create(
                ModBlocks.BURNER_INSERTER.get(),
                new TextureMapping()
                        .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(Blocks.FURNACE, "_front"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.FURNACE, "_side"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.FURNACE, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.BURNER_INSERTER.get(), BlockModelGenerators.plainVariant(model))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        // Iron sides with a barrel's lid: reads as a metal container at a glance, and does not
        // read as a block of iron, which is what a plain iron cube would have looked like.
        Identifier chest = ModelTemplates.CUBE_TOP.create(
                ModBlocks.IRON_CHEST.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.IRON_BLOCK))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.BARREL, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(
                ModBlocks.IRON_CHEST.get(), BlockModelGenerators.plainVariant(chest)));
    }
}
