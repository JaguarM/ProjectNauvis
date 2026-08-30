package com.jaguarm.nauvismachines.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerShape;
import com.jaguarm.nauvismachines.multiblock.Boxes;
import com.jaguarm.nauvismachines.multiblock.MachineCell;
import com.jaguarm.nauvismachines.multiblock.MachineShape;
import com.jaguarm.nauvismachines.registry.ModBlocks;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Block and item models, generated from the machine's shape rather than written beside it.
 *
 * <p>Textures are placeholders and point at <em>vanilla</em> ones, which is not the same thing as
 * leaving them out. A model naming a texture this mod does not ship renders as the magenta-and-
 * black checkerboard, and that reads as a broken model rather than as art nobody has drawn yet -
 * which matters, because the person judging whether a machine looks right is looking at it in
 * game. A blast furnace body is the nearest vanilla thing to an assembler.
 *
 * <p>Real art is Yannic's half - see {@code texture-workshop/} for the
 * approach that produced the drills - and swapping it in is a one-line change here.
 *
 * <h2>One model per shape, not per block</h2>
 *
 * <p>An assembler is ten blocks but four models. Cells that are the same thing turned a quarter
 * turn - the four corners, the four edges - share a file and differ by the blockstate's {@code y}
 * rotation, which is the same rotation {@link MachineCell} applies to their collision boxes. That
 * is the point of {@link Boxes}: turn the model one way and the shape the other and you get a
 * machine you can see through on one side and walk into on the other, which no test would catch.
 *
 * <h2>No template, and no giant model on the middle block</h2>
 *
 * <p>There is no vanilla parent shaped like a machine, so these are written out directly:
 * {@code modelOutput} takes any {@code ModelInstance}, and a {@code ModelInstance} is a
 * {@code Supplier<JsonElement>}. That is what lets the geometry live in exactly one place, in
 * {@code AssemblerShape}, and be read from here.
 *
 * <p>Every cell draws its own block rather than one cell drawing the lot, and that is deliberate.
 * A model may only reach one block in each direction - {@code CuboidModelElement} caps an element
 * at {@code -16..32} - so one big model does not scale past 3x3x3 and would fail outright on the
 * five-tile steam engine. Per-cell models also get per-block lighting for free, need no
 * {@code getRenderBoundingBox}, and cost nothing extra to draw.
 */
public class NauvisMachinesModels extends ModelProvider {

    public NauvisMachinesModels(PackOutput output) {
        super(output, NauvisMachines.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        assembler(blockModels);
    }

    private void assembler(BlockModelGenerators blockModels) {
        Block block = ModBlocks.ASSEMBLING_MACHINE_1.get();
        MachineShape shape = AssemblerShape.SHAPE;

        // A machine body, and machinery on top of it. Two mappings, so the gearbox reads as a
        // moving part rather than as more casing.
        Textures casing = new Textures(
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_side").sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite());
        Textures machinery = new Textures(
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite());

        // Keyed by model name, not by cell: the four corners are one file between them.
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(), name -> cellModel(blockModels, block, cell,
                    AssemblerShape.GEARBOX.equals(name) ? machinery : casing));
        }

        PropertyDispatch.C1<MultiVariant, Integer> dispatch =
                PropertyDispatch.initial(shape.part());
        for (int index = 0; index < shape.cellCount(); index++) {
            MachineCell cell = shape.cell(index);
            dispatch = dispatch.select(index, BlockModelGenerators
                    .plainVariant(models.get(cell.model()))
                    .with(turn(cell.turns())));
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));

        // The whole machine, shrunk into one block. See inventoryModel.
        blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape, casing));
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

    private static Identifier cellModel(BlockModelGenerators blockModels, Block block,
            MachineCell cell, Textures textures) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_" + cell.model());
        blockModels.modelOutput.accept(id, () -> {
            JsonArray elements = new JsonArray();
            for (float[] box : cell.boxes()) {
                elements.add(element(box, true));
            }

            JsonObject model = new JsonObject();
            // block/block, not block/cube: it carries the display transforms an item needs and
            // no geometry of its own.
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures.json());
            model.add("elements", elements);
            return model;
        });
        return id;
    }

    /**
     * The whole machine in one block, for the item in your hand.
     *
     * <p>An assembler's anchor cell on its own is the middle of a deck, which in the hand reads as
     * a plain metal cube. The whole machine at full size does not fit. So the geometry is read out
     * of the shape a second time - every cell turned by its own {@code turns}, moved to where that
     * cell sits, and the lot scaled down until the machine's longest side is one block. It is a
     * miniature, generated, and it cannot fall out of step with the block because it is the same
     * numbers.
     *
     * <p>{@code PipeBlock} has a hand-written {@code pipe_inventory} for the same reason. This is
     * that idea with the hand taken out of it.
     */
    private static Identifier inventoryModel(BlockModelGenerators blockModels, Block block,
            MachineShape shape, Textures textures) {
        Identifier id = ModelLocationUtils.getModelLocation(block, "_inventory");
        blockModels.modelOutput.accept(id, () -> {
            int longest = Math.max(shape.width(), Math.max(shape.height(), shape.depth()));
            float scale = 1.0F / longest;
            // Centred across, and standing on the bottom - a machine sitting on the ground reads
            // better in the hand than one floating in the middle of its block.
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

            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures.json());
            model.add("elements", elements);
            return model;
        });
        return id;
    }

    /**
     * One box, with the machine's three texture slots on its six faces.
     *
     * <p>{@code cullface} is set only on a face lying exactly on a block boundary, and only for a
     * block model. That is what stops the nine cubes of a deck drawing eight interior walls
     * nobody can see. It must never be set on a face that hangs outside its own block - there is
     * nothing there to be culled against, and the face would vanish - and the miniature is one
     * block containing a whole machine, so it gets none at all.
     *
     * <p>No {@code uv} either: absent, it is derived from the box's own footprint, which is what
     * these want. {@code tools/check_models.py} is what makes that safe to leave implicit - it
     * fails the build on a box that leaves {@code 0..16} without stating its uv, because the
     * derived one would run off the end of the texture.
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

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
