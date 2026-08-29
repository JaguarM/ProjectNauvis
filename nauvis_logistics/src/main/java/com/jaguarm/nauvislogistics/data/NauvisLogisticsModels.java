package com.jaguarm.nauvislogistics.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlock;
import com.jaguarm.nauvislogistics.belt.BeltShape;
import com.jaguarm.nauvislogistics.belt.SplitterShape;
import com.jaguarm.nauvislogistics.multiblock.Boxes;
import com.jaguarm.nauvislogistics.multiblock.MachineCell;
import com.jaguarm.nauvislogistics.multiblock.MachineShape;
import com.jaguarm.nauvislogistics.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
        // Three of it: straight, and the two hands of a corner. They differ only in which top
        // texture they name - the slab and its sides are the same object whichever way it bends.
        Identifier belt = beltModel(blockModels, "", "transport_belt_top");
        Identifier left = beltModel(blockModels, "_left", "transport_belt_top_left");
        Identifier right = beltModel(blockModels, "_right", "transport_belt_top_right");

        PropertyDispatch.C1<MultiVariant, BeltShape> bend = PropertyDispatch.initial(BeltBlock.SHAPE)
                .select(BeltShape.STRAIGHT, BlockModelGenerators.plainVariant(belt))
                .select(BeltShape.FROM_LEFT, BlockModelGenerators.plainVariant(left))
                .select(BeltShape.FROM_RIGHT, BlockModelGenerators.plainVariant(right));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.TRANSPORT_BELT.get())
                        .with(bend)
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        blockModels.registerSimpleItemModel(ModBlocks.TRANSPORT_BELT.get(), belt);

        // The splitter: 2x1 multiblock machine with iron housing and golden top
        machine(blockModels, ModBlocks.SPLITTER.get(), SplitterShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                TextureMapping.getBlockTexture(Blocks.GOLD_BLOCK).sprite());

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

    private void machine(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier side, Identifier top) {
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(),
                    name -> cellModel(blockModels, block, cell, side, top));
        }

        PropertyDispatch.C2<MultiVariant, Integer, Direction> dispatch =
                PropertyDispatch.initial(shape.part(), BlockStateProperties.HORIZONTAL_FACING);
        for (int index = 0; index < shape.cellCount(); index++) {
            MachineCell cell = shape.cell(index);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                dispatch = dispatch.select(index, facing, BlockModelGenerators
                        .plainVariant(models.get(cell.model()))
                        .with(turn(cell.turns() + Boxes.quarterTurns(facing))));
            }
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
                elements.add(machineElement(box, true));
            }
            return machineModel(elements, side, top);
        });
        return id;
    }

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
        textures.addProperty("particle", side.toString());

        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

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
