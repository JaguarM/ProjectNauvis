package com.jaguarm.nauvispower.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.grid.PolePart;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;
import com.jaguarm.nauvispower.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Direction;
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

        // A horizontal column, laid along the engine's axis, so the two ends steam goes in and out
        // of are the two ends you can see. An engine that looked the same from every side would
        // make its facing - which is the whole of how a row of them chains - invisible.
        Identifier engine = ModelTemplates.CUBE_COLUMN_HORIZONTAL.create(
                ModBlocks.STEAM_ENGINE.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.IRON_BLOCK))
                        .put(TextureSlot.END, TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.STEAM_ENGINE.get(),
                                BlockModelGenerators.plainVariant(engine))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        pole(blockModels);
    }

    /**
     * The pole, one model per {@link PolePart}, built from the same boxes the block collides with.
     *
     * <p>No {@code ModelTemplate} here on purpose. A template is a parent plus texture slots, and
     * a crossarm is geometry - there is no vanilla parent shaped like one. What there is instead
     * is {@code modelOutput}, which takes any {@link ModelInstance}, and a {@link ModelInstance}
     * is a {@code Supplier<JsonElement>}: the model file, written out directly. That is what lets
     * the shape live in exactly one place.
     */
    private void pole(BlockModelGenerators blockModels) {
        // Material wraps the sprite id; the model file wants the plain identifier.
        Identifier texture = TextureMapping.getBlockTexture(Blocks.STRIPPED_OAK_LOG).sprite();

        // Keyed by model name, not by part: the two shaft parts are the same post and deserve one
        // file between them.
        Map<String, Identifier> models = new HashMap<>();
        for (PolePart part : PolePart.values()) {
            models.computeIfAbsent(part.modelName(), name -> poleModel(blockModels, part, texture));
        }

        PropertyDispatch.C1<MultiVariant, PolePart> dispatch =
                PropertyDispatch.initial(SmallElectricPoleBlock.PART);
        for (PolePart part : PolePart.values()) {
            dispatch = dispatch.select(part,
                    BlockModelGenerators.plainVariant(models.get(part.modelName())));
        }
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.SMALL_ELECTRIC_POLE.get()).with(dispatch));

        // The crossarm is the silhouette a player recognises, so the item is the head.
        blockModels.registerSimpleItemModel(ModBlocks.SMALL_ELECTRIC_POLE.get(),
                models.get(PolePart.HEAD.modelName()));
    }

    private static Identifier poleModel(BlockModelGenerators blockModels, PolePart part, Identifier texture) {
        Identifier id = Identifier.fromNamespaceAndPath(NauvisPower.MODID,
                "block/small_electric_pole_" + part.modelName());
        blockModels.modelOutput.accept(id, () -> {
            JsonObject textures = new JsonObject();
            textures.addProperty("texture", texture.toString());
            // Without a particle texture a broken or walked-on block throws up the missing one.
            textures.addProperty("particle", texture.toString());

            JsonArray elements = new JsonArray();
            for (float[] box : part.boxes()) {
                elements.add(element(box));
            }

            JsonObject model = new JsonObject();
            // block/block, not block/cube: it carries the display transforms an item needs and
            // no geometry of its own.
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        });
        return id;
    }

    private static JsonObject element(float[] box) {
        JsonObject element = new JsonObject();
        element.add("from", vector(box[0], box[1], box[2]));
        element.add("to", vector(box[3], box[4], box[5]));

        JsonObject faces = new JsonObject();
        for (Direction direction : Direction.values()) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", "#texture");
            // No uv and no cullface: uv defaults to the element's own footprint, which is what
            // a post wants, and nothing here reaches a block face to be culled against.
            faces.add(direction.getSerializedName(), face);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
