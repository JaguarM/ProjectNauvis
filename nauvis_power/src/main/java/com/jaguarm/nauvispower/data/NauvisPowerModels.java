package com.jaguarm.nauvispower.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvispower.grid.PolePart;
import com.jaguarm.nauvispower.multiblock.Boxes;
import com.jaguarm.nauvispower.multiblock.MachineCell;
import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;
import com.jaguarm.nauvispower.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
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
        // A brick body with a metal top, and the chimney in the same brick.
        machine(blockModels, ModBlocks.BOILER.get(), BoilerShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.BRICKS).sprite(),
                TextureMapping.getBlockTexture(Blocks.FURNACE, "_top").sprite());

        // Iron, with a blast furnace top for the flywheels standing in its spine.
        machine(blockModels, ModBlocks.STEAM_ENGINE.get(), SteamEngineShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite());

        pole(blockModels);
    }

    /**
     * Every cell of a machine, plus the miniature that goes in the player's hand.
     *
     * <p>No {@code ModelTemplate}: a template is a parent plus texture slots, and none of these
     * shapes has a vanilla parent. {@code modelOutput} takes any {@link ModelInstance}, and a
     * {@link ModelInstance} is a {@code Supplier<JsonElement>} - the model file, written out
     * directly. That is what lets the geometry live in exactly one place, in the shape class.
     */
    private void machine(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier side, Identifier top) {
        // Keyed by model name, not by cell: four corners are one file between them.
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(),
                    name -> cellModel(blockModels, block, cell, side, top));
        }

        PropertyDispatch.C1<MultiVariant, Integer> dispatch = PropertyDispatch.initial(shape.part());
        for (int index = 0; index < shape.cellCount(); index++) {
            MachineCell cell = shape.cell(index);
            dispatch = dispatch.select(index, BlockModelGenerators
                    .plainVariant(models.get(cell.model()))
                    .with(turn(cell.turns())));
        }

        // Two dispatches, composed: which cell this is, and which way the whole machine looks.
        // The facing rotation lands on top of each cell's own turn, which is exactly what
        // MachineCell does to the collision boxes - it adds the two before building the shape.
        // These are the same rotation applied to the two halves of one machine, and the day they
        // stop agreeing is the day you can see through a wall you cannot walk through.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(dispatch)
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        blockModels.registerSimpleItemModel(block,
                inventoryModel(blockModels, block, shape, side, top));
    }

    /** The blockstate rotation matching {@link MachineCell#turns()}, which turns the boxes. */
    private static VariantMutator turn(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> BlockModelGenerators.Y_ROT_90;
            case 2 -> BlockModelGenerators.Y_ROT_180;
            case 3 -> BlockModelGenerators.Y_ROT_270;
            default -> BlockModelGenerators.NOP;
        };
    }

    private static Identifier cellModel(BlockModelGenerators blockModels, Block block,
            MachineCell cell, Identifier side, Identifier top) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_" + cell.model());
        blockModels.modelOutput.accept(id, () -> {
            JsonArray elements = new JsonArray();
            for (float[] box : cell.boxes()) {
                elements.add(machineElement(box, true));
            }
            return machineModel(elements, side, top);
        });
        return id;
    }

    /**
     * The whole machine in one block, for the item in your hand.
     *
     * <p>A boiler's anchor cell on its own is a slab of brick, and the whole machine at full size
     * does not fit anywhere. So the geometry is read out of the shape a second time - every cell
     * turned by its own {@code turns}, moved to where that cell sits, and the lot scaled down
     * until the machine's longest side is one block. It cannot fall out of step with the block,
     * because it is the same numbers.
     *
     * <p>{@code PipeBlock} has a hand-written {@code pipe_inventory} for the same reason. This is
     * that idea with the hand taken out of it.
     */
    private static Identifier inventoryModel(BlockModelGenerators blockModels, Block block,
            MachineShape shape, Identifier side, Identifier top) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_inventory");
        blockModels.modelOutput.accept(id, () -> {
            int longest = Math.max(shape.width(), Math.max(shape.height(), shape.depth()));
            float scale = 1.0F / longest;
            // Centred across, and standing on the bottom: a machine sitting on the ground reads
            // better in the hand than one floating in the middle of its block.
            float shiftX = (16.0F - 16.0F * shape.width() / longest) / 2.0F;
            float shiftZ = (16.0F - 16.0F * shape.depth() / longest) / 2.0F;

            JsonArray elements = new JsonArray();
            for (MachineCell cell : shape.cells()) {
                for (float[] box : Boxes.rotate(cell.boxes(), cell.turns())) {
                    elements.add(machineElement(new float[] {
                        shiftX + scale * (box[0] + 16 * cell.x()),
                        scale * (box[1] + 16 * cell.y()),
                        shiftZ + scale * (box[2] + 16 * cell.z()),
                        shiftX + scale * (box[3] + 16 * cell.x()),
                        scale * (box[4] + 16 * cell.y()),
                        shiftZ + scale * (box[5] + 16 * cell.z()),
                    }, false));
                }
            }
            return machineModel(elements, side, top);
        });
        return id;
    }

    private static JsonObject machineModel(JsonArray elements, Identifier side, Identifier top) {
        JsonObject textures = new JsonObject();
        textures.addProperty("side", side.toString());
        textures.addProperty("top", top.toString());
        textures.addProperty("bottom", side.toString());
        // Without a particle texture a broken or walked-on block throws up the missing one.
        textures.addProperty("particle", side.toString());

        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * One box of a machine, with the shell's three texture slots on its six faces.
     *
     * <p>{@code cullface} is set only where a face lies exactly on a block boundary, and only for
     * a block model: that is what stops the deck of a machine drawing interior walls nobody can
     * see. It must never be set on a face hanging outside its own block - there would be nothing
     * to cull against and the face would simply vanish - and the miniature is a whole machine
     * inside one block, so it gets none at all.
     *
     * <p>No {@code uv}: absent, it is derived from the box's own footprint, which is what these
     * want. {@code tools/check_models.py} is what makes leaving it implicit safe - it fails the
     * build on a box that leaves {@code 0..16} without stating its uv.
     */
    private static JsonObject machineElement(float[] box, boolean cull) {
        JsonObject element = new JsonObject();
        element.add("from", vector(box[0], box[1], box[2]));
        element.add("to", vector(box[3], box[4], box[5]));

        JsonObject faces = new JsonObject();
        for (Direction direction : Direction.values()) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", switch (direction) {
                case UP -> "#top";
                case DOWN -> "#bottom";
                default -> "#side";
            });
            if (cull && onBoundary(box, direction)) {
                face.addProperty("cullface", direction.getSerializedName());
            }
            faces.add(direction.getSerializedName(), face);
        }
        element.add("faces", faces);
        return element;
    }

    /** Whether this face of the box lies flat against the edge of its own block. */
    private static boolean onBoundary(float[] box, Direction direction) {
        return switch (direction) {
            case DOWN -> box[1] == 0;
            case UP -> box[4] == 16;
            case NORTH -> box[2] == 0;
            case SOUTH -> box[5] == 16;
            case WEST -> box[0] == 0;
            case EAST -> box[3] == 16;
        };
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
