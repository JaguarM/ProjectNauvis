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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
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

    /** The dyed blocks are a {@code ColorCollection} in 26.2; there is no {@code YELLOW_TERRACOTTA} field. */
    private static final Block YELLOW_TERRACOTTA = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW);

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

        // The same furnace body, in blast-furnace colours: the two inserters have to be told
        // apart on a belt line at a glance, and the front face still has to say which way it
        // points, which is the one thing the model genuinely has to communicate.
        Identifier electric = ModelTemplates.CUBE_ORIENTABLE.create(
                ModBlocks.INSERTER.get(),
                new TextureMapping()
                        .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_front"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_side"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.INSERTER.get(), BlockModelGenerators.plainVariant(electric))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        // The belt: a bottom slab, because it is half a block high and you walk over it. The top
        // texture is the one thing this model has to get right - a belt facing the wrong way looks
        // exactly like a belt that is working - so it is a piston side, whose diagonals show which
        // way the blockstate has turned it. Yellow flanks, because Factorio's first belt is yellow.
        Identifier belt = ModelTemplates.SLAB_BOTTOM.create(
                ModBlocks.TRANSPORT_BELT.get(),
                new TextureMapping()
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.PISTON, "_side"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(YELLOW_TERRACOTTA))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(YELLOW_TERRACOTTA)),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.TRANSPORT_BELT.get(), BlockModelGenerators.plainVariant(belt))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        // Said out loud rather than left to the default, because the default is "block/<name>" and
        // a template that adds a suffix silently breaks it. See the silent-failures list.
        blockModels.registerSimpleItemModel(ModBlocks.TRANSPORT_BELT.get(), belt);

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
