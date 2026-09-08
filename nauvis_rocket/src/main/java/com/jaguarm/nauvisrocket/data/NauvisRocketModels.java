package com.jaguarm.nauvisrocket.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.silo.RocketSiloShape;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** The silo's models, generated from its shape, and flat icons for the three items. */
public class NauvisRocketModels extends ModelProvider {

    public NauvisRocketModels(PackOutput output) {
        super(output, NauvisRocket.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier concrete = Identifier.withDefaultNamespace("block/gray_concrete");
        Identifier white = Identifier.withDefaultNamespace("block/white_concrete");
        Identifier red = Identifier.withDefaultNamespace("block/red_concrete");

        Textures pad = new Textures(concrete, concrete, concrete);
        Textures body = new Textures(white, white, white);
        Textures nose = new Textures(red, red, white);
        machine(blockModels, ModBlocks.ROCKET_SILO.get(), RocketSiloShape.SHAPE, pad, Map.of(
                RocketSiloShape.BODY_CORNER, body,
                RocketSiloShape.BODY_SIDE, body,
                RocketSiloShape.BODY_CORE, body,
                RocketSiloShape.NOSE_CORNER, nose,
                RocketSiloShape.NOSE_EDGE, nose,
                RocketSiloShape.NOSE_TIP, nose));

        for (var item : List.of(ModItems.ROCKET_PART, ModItems.SATELLITE, ModItems.SPACE_SCIENCE_PACK)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    /** The three texture slots every model here has. */
    private record Textures(Identifier side, Identifier top, Identifier bottom) {

        JsonObject json() {
            JsonObject textures = new JsonObject();
            textures.addProperty("side", side.toString());
            textures.addProperty("top", top.toString());
            textures.addProperty("bottom", bottom.toString());
            // Without a particle texture a broken or walked-on block throws up the missing one.
            textures.addProperty("particle", side.toString());
            return textures;
        }
    }

    /** Every cell of a machine with no facing, plus the miniature that goes in the player's hand. */
    private void machine(BlockModelGenerators blockModels, Block block, MachineShape shape, Textures casing,
            Map<String, Textures> parts) {
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(),
                    name -> cellModel(blockModels, block, name, cell, parts.getOrDefault(name, casing)));
        }
        PropertyDispatch.C1<MultiVariant, Integer> dispatch = PropertyDispatch.initial(shape.part());
        for (int index = 0; index < shape.cellCount(); index++) {
            MachineCell cell = shape.cell(index);
            dispatch = dispatch.select(index, BlockModelGenerators
                    .plainVariant(models.get(cell.model()))
                    .with(turn(cell.turns())));
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
        blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape, casing, parts));
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

    private static Identifier cellModel(BlockModelGenerators blockModels, Block block, String name,
            MachineCell cell, Textures textures) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_" + name);
        blockModels.modelOutput.accept(id, () -> {
            JsonArray elements = new JsonArray();
            for (float[] box : cell.boxes()) {
                elements.add(element(box, true));
            }
            return model(elements, textures);
        });
        return id;
    }

    /**
     * The whole silo in one block, for the item in your hand: every cell turned by its own turn,
     * moved to where it sits, and the lot scaled down until the nine-wide pad is one block. The
     * rocket in the miniature keeps its own colours, or the thing in the hand is a grey slab.
     */
    private static Identifier inventoryModel(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Textures casing, Map<String, Textures> parts) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_inventory");
        blockModels.modelOutput.accept(id, () -> {
            int longest = Math.max(shape.width(), Math.max(shape.height(), shape.depth()));
            float scale = 1.0F / longest;
            float shiftX = (16.0F - 16.0F * shape.width() / longest) / 2.0F;
            float shiftZ = (16.0F - 16.0F * shape.depth() / longest) / 2.0F;

            JsonArray elements = new JsonArray();
            for (MachineCell cell : shape.cells()) {
                // Three texture slots, so a cell of the rocket names the rocket's rather than the pad's.
                String prefix = parts.containsKey(cell.model()) ? cell.model() + "_" : "";
                for (float[] box : Boxes.rotate(cell.boxes(), cell.turns())) {
                    elements.add(element(new float[] {
                        shiftX + scale * (box[0] + 16 * cell.x()),
                        scale * (box[1] + 16 * cell.y()),
                        shiftZ + scale * (box[2] + 16 * cell.z()),
                        shiftX + scale * (box[3] + 16 * cell.x()),
                        scale * (box[4] + 16 * cell.y()),
                        shiftZ + scale * (box[5] + 16 * cell.z()),
                    }, false, prefix));
                }
            }

            JsonObject textures = casing.json();
            for (Map.Entry<String, Textures> part : parts.entrySet()) {
                textures.addProperty(part.getKey() + "_side", part.getValue().side().toString());
                textures.addProperty(part.getKey() + "_top", part.getValue().top().toString());
                textures.addProperty(part.getKey() + "_bottom", part.getValue().bottom().toString());
            }
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        });
        return id;
    }

    private static JsonObject model(JsonArray elements, Textures textures) {
        JsonObject model = new JsonObject();
        // block/block, not block/cube: it carries the display transforms an item needs and no
        // geometry of its own.
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures.json());
        model.add("elements", elements);
        return model;
    }

    private static JsonObject element(float[] box, boolean cull) {
        return element(box, cull, "");
    }

    /**
     * One box, with three texture slots on its six faces - the slots' names prefixed for a part
     * of the miniature that is dressed differently from the pad. {@code cullface} only where a
     * face lies on a block boundary, and only on a block model; the miniature gets none.
     */
    private static JsonObject element(float[] box, boolean cull, String prefix) {
        JsonObject element = new JsonObject();
        element.add("from", vector(box[0], box[1], box[2]));
        element.add("to", vector(box[3], box[4], box[5]));

        JsonObject faces = new JsonObject();
        for (Direction direction : Direction.values()) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", "#" + prefix + switch (direction) {
                case UP -> "top";
                case DOWN -> "bottom";
                default -> "side";
            });
            if (cull && onBoundary(box, direction)) {
                face.addProperty("cullface", direction.getSerializedName());
            }
            faces.add(direction.getSerializedName(), face);
        }
        element.add("faces", faces);
        return element;
    }

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

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
