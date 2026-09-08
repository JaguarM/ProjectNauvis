package com.jaguarm.nauvisresearch.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The lab's models, generated from its shape, and a flat icon for each science pack. */
public class NauvisResearchModels extends ModelProvider {

    public NauvisResearchModels(PackOutput output) {
        super(output, NauvisResearch.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        machine(blockModels, ModBlocks.LAB.get(), LabShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.PRISMARINE_BRICKS).sprite(),
                TextureMapping.getBlockTexture(Blocks.SEA_LANTERN).sprite());

        // Seven flasks off one map in texture-workshop/make_science_textures.py; the space pack
        // is the rocket mod's.
        for (var pack : List.of(ModItems.AUTOMATION_SCIENCE_PACK, ModItems.LOGISTIC_SCIENCE_PACK,
                ModItems.CHEMICAL_SCIENCE_PACK, ModItems.MILITARY_SCIENCE_PACK,
                ModItems.PRODUCTION_SCIENCE_PACK, ModItems.UTILITY_SCIENCE_PACK)) {
            itemModels.generateFlatItem(pack.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    private void machine(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier side, Identifier top) {
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
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));

        blockModels.registerSimpleItemModel(block,
                inventoryModel(blockModels, block, shape, side, top));
    }

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
                elements.add(element(box, true));
            }
            return model(elements, side, top, LabShape.DOME.equals(cell.model()));
        });
        return id;
    }

    /**
     * The whole lab in one block, for the item in your hand.
     *
     * <p>The same geometry read a second time, scaled until the machine's longest side is one
     * block. It cannot fall out of step with the block because it is the same numbers.
     */
    private static Identifier inventoryModel(BlockModelGenerators blockModels, Block block,
            MachineShape shape, Identifier side, Identifier top) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_inventory");
        blockModels.modelOutput.accept(id, () -> {
            int longest = Math.max(shape.width(), Math.max(shape.height(), shape.depth()));
            float scale = 1.0F / longest;
            float shiftX = (16.0F - 16.0F * shape.width() / longest) / 2.0F;
            float shiftZ = (16.0F - 16.0F * shape.depth() / longest) / 2.0F;

            JsonArray elements = new JsonArray();
            for (MachineCell cell : shape.cells()) {
                for (float[] box : Boxes.rotate(cell.boxes(), cell.turns())) {
                    elements.add(element(new float[] {
                        shiftX + scale * (box[0] + 16 * cell.x()),
                        scale * (box[1] + 16 * cell.y()),
                        shiftZ + scale * (box[2] + 16 * cell.z()),
                        shiftX + scale * (box[3] + 16 * cell.x()),
                        scale * (box[4] + 16 * cell.y()),
                        shiftZ + scale * (box[5] + 16 * cell.z()),
                    }, false));
                }
            }
            return model(elements, side, top, false);
        });
        return id;
    }

    /** The dome is drawn in the bright texture on every face; the shell keeps it for its top. */
    private static JsonObject model(JsonArray elements, Identifier side, Identifier top,
            boolean allBright) {
        JsonObject textures = new JsonObject();
        textures.addProperty("side", (allBright ? top : side).toString());
        textures.addProperty("top", top.toString());
        textures.addProperty("bottom", side.toString());
        // Without a particle texture a broken or walked-on block throws up the missing one.
        textures.addProperty("particle", side.toString());

        JsonObject model = new JsonObject();
        // block/block, not block/cube: it carries the display transforms an item needs and no
        // geometry of its own.
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * One box, with the shell's three texture slots on its six faces.
     *
     * <p>{@code cullface} only where a face lies exactly on a block boundary, and only on a block
     * model - that is what keeps the floor of a lab from drawing interior walls nobody can see. It
     * must never go on a face hanging outside its own block, and the miniature is a whole machine
     * inside one block, so it gets none.
     */
    private static JsonObject element(float[] box, boolean cull) {
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
