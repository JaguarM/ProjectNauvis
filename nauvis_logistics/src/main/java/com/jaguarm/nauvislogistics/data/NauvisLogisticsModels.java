package com.jaguarm.nauvislogistics.data;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlock;
import com.jaguarm.nauvislogistics.belt.BeltShape;
import com.jaguarm.nauvislogistics.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.data.PackOutput;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/**
 * Block and item models.
 *
 * <p>Most textures here are placeholders and point at <em>vanilla</em> ones on purpose. A model
 * naming a texture this mod does not ship renders as the magenta-and-black checkerboard, which
 * reads as a broken model rather than as art nobody has drawn yet - and the difference matters
 * when the person judging whether a machine looks right is looking at it in game.
 *
 * <p><b>The belt is the exception and has art of its own</b>, from
 * {@code texture-workshop/make_belt_textures.py}. It had to: a belt's facing has to be legible at
 * a glance, and its tread has to move, neither of which a borrowed texture can do.
 *
 * <p>A furnace body gives the inserter a distinct front face, which is the one thing its model
 * genuinely has to communicate: an inserter that is facing the wrong way looks exactly like one
 * that is working. Real art is Yannic's half; see
 * {@code ../NeoProgressiveAutomation/texture-workshop/}.
 */
public class NauvisLogisticsModels extends ModelProvider {

    /** One belt model: a bottom slab, with the given top. */
    private static Identifier beltModel(BlockModelGenerators blockModels, String suffix, String top) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.TOP, texture(top))
                .put(TextureSlot.SIDE, texture("transport_belt_side"))
                .put(TextureSlot.BOTTOM, texture("transport_belt_bottom"));
        return suffix.isEmpty()
                ? ModelTemplates.SLAB_BOTTOM.create(
                        ModBlocks.TRANSPORT_BELT.get(), textures, blockModels.modelOutput)
                : ModelTemplates.SLAB_BOTTOM.createWithSuffix(
                        ModBlocks.TRANSPORT_BELT.get(), suffix, textures, blockModels.modelOutput);
    }

    /**
     * One of ours, from {@code texture-workshop/}, rather than a vanilla stand-in.
     *
     * <p>A {@code Material} rather than an {@code Identifier}: {@code TextureMapping.put} takes
     * one in 26.2, and {@code TextureMapping.getBlockTexture} - which every other model here uses
     * - only names blocks in a registry, which our own PNG files are not.
     */
    private static Material texture(String name) {
        return new Material(Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "block/" + name));
    }

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

        // And once more in smoker colours, for the long arm. Three inserters on one belt line
        // have to be told apart at a glance - the two electric ones do the same job at different
        // reaches, so mistaking one for the other is a line that silently misses a machine.
        //
        // Nothing here says the arm is longer, which is the one thing this model ought to
        // communicate and the one thing a borrowed cube cannot. Real art is Yannic's half.
        Identifier longHanded = ModelTemplates.CUBE_ORIENTABLE.create(
                ModBlocks.LONG_HANDED_INSERTER.get(),
                new TextureMapping()
                        .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(Blocks.SMOKER, "_front"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.SMOKER, "_side"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.SMOKER, "_top")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                                ModBlocks.LONG_HANDED_INSERTER.get(),
                                BlockModelGenerators.plainVariant(longHanded))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        // The belt: a bottom slab, because it is half a block high and you walk over it.
        //
        // The only art in this mod that is ours rather than a vanilla placeholder, because the
        // belt is the one block here whose facing has to be legible at a glance - a belt pointing
        // the wrong way looks exactly like a belt that is working. The top carries a chevron and
        // it scrolls, at exactly the speed the belt carries things, which is what
        // `texture-workshop/make_belt_textures.py` goes to some trouble over.
        // Three of it: straight, and the two hands of a corner. They differ only in which top
        // texture they name - the slab and its sides are the same object whichever way it bends.
        Identifier belt = beltModel(blockModels, "", "transport_belt_top");
        Identifier left = beltModel(blockModels, "_left", "transport_belt_top_left");
        Identifier right = beltModel(blockModels, "_right", "transport_belt_top_right");

        // Two properties, and they do not fight: the shape picks a model and the facing sets the
        // rotation. That is the arrangement the boiler could not use - there both the cell and the
        // machine wanted to set `y`, and a VariantMutator sets rather than adds.
        PropertyDispatch.C1<MultiVariant, BeltShape> bend = PropertyDispatch.initial(BeltBlock.SHAPE)
                .select(BeltShape.STRAIGHT, BlockModelGenerators.plainVariant(belt))
                .select(BeltShape.FROM_LEFT, BlockModelGenerators.plainVariant(left))
                .select(BeltShape.FROM_RIGHT, BlockModelGenerators.plainVariant(right));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.TRANSPORT_BELT.get())
                        .with(bend)
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        // Said out loud rather than left to the default, because the default is "block/<name>" and
        // a template that adds a suffix silently breaks it. See the silent-failures list. In hand
        // it is a straight belt, whatever it would become once placed.
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
