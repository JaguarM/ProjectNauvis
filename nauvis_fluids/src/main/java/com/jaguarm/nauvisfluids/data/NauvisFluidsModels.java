package com.jaguarm.nauvisfluids.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;
import com.jaguarm.nauvisfluids.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Block and item models.
 *
 * <p>Textures are placeholders and point at <em>vanilla</em> ones on purpose. A model naming a
 * texture this mod does not ship renders as the magenta-and-black checkerboard, which reads as a
 * broken model rather than as art nobody has drawn yet.
 *
 * <p>The pipe is a multipart, like a fence: a core that is always drawn and an arm for each
 * connected face. That is not decoration - the connections are real, and a run that has not joined
 * the boiler you thought it had joined is a thing you can see rather than a thing you have to work
 * out from a tooltip.
 */
public class NauvisFluidsModels extends ModelProvider {

    public NauvisFluidsModels(PackOutput output) {
        super(output, NauvisFluids.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier texture = TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite();

        Identifier core = pipeModel(blockModels, "pipe_core", texture,
                new float[][] {{5, 5, 5, 11, 11, 11}});
        // One arm, drawn pointing down, and rotated into place by the blockstate below. A single
        // model turned six ways is one file to keep right instead of six.
        Identifier arm = pipeModel(blockModels, "pipe_arm", texture,
                new float[][] {{5, 0, 5, 11, 5, 11}});

        MultiPartGenerator pipe = MultiPartGenerator.multiPart(ModBlocks.PIPE.get())
                .with(BlockModelGenerators.plainVariant(core));
        for (Direction side : Direction.values()) {
            pipe = pipe.with(
                    BlockModelGenerators.condition().term(property(side), true),
                    BlockModelGenerators.plainVariant(arm).with(rotation(side)));
        }
        blockModels.blockStateOutput.accept(pipe);

        // The item is the core plus two arms, so a pipe in your hand looks like a pipe rather than
        // like a small cube.
        Identifier inventory = pipeModel(blockModels, "pipe_inventory", texture, new float[][] {
            {5, 5, 5, 11, 11, 11},
            {5, 0, 5, 11, 5, 11},
            {5, 11, 5, 11, 16, 11},
        });
        blockModels.registerSimpleItemModel(ModBlocks.PIPE.get(), inventory);
    }

    /** The blockstate property matching a face, from vanilla's own set so the names are standard. */
    private static BooleanProperty property(Direction side) {
        return switch (side) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }

    /** How far to turn the downward arm to make it point at {@code side}. */
    private static VariantMutator rotation(Direction side) {
        return switch (side) {
            case DOWN -> BlockModelGenerators.NOP;
            case UP -> BlockModelGenerators.X_ROT_180;
            case NORTH -> BlockModelGenerators.X_ROT_270;
            case SOUTH -> BlockModelGenerators.X_ROT_90;
            case WEST -> BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_270);
            case EAST -> BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_90);
        };
    }

    /**
     * A model written out directly from a list of boxes.
     *
     * <p>No {@code ModelTemplate}, because a pipe arm is geometry and a template is a parent plus
     * texture slots. {@code modelOutput} takes any {@link ModelInstance}, which is a
     * {@code Supplier<JsonElement>} - the model file itself. The pole's models are built the same
     * way.
     */
    private static Identifier pipeModel(BlockModelGenerators blockModels, String name,
            Identifier texture, float[][] boxes) {
        Identifier id = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "block/" + name);
        blockModels.modelOutput.accept(id, () -> {
            JsonObject textures = new JsonObject();
            textures.addProperty("texture", texture.toString());
            textures.addProperty("particle", texture.toString());

            JsonArray elements = new JsonArray();
            for (float[] box : boxes) {
                elements.add(element(box));
            }

            JsonObject model = new JsonObject();
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
