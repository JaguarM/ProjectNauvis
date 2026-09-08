package com.jaguarm.nauvispower.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.SolarPanelShape;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvispower.grid.BigPoleShape;
import com.jaguarm.nauvispower.grid.MediumPoleShape;
import com.jaguarm.nauvispower.grid.SmallPoleShape;
import com.jaguarm.nauvispower.grid.SubstationShape;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.storage.AccumulatorShape;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

        poles(blockModels);

        // Iron frames under blue glass. A panel has no facing, so it takes the poles' generator.
        turnless(blockModels, ModBlocks.SOLAR_PANEL.get(), SolarPanelShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                Identifier.withDefaultNamespace("block/blue_terracotta"));

        // A copper cabinet with the panel's blue on top: a battery, and part of the same solar
        // field. Named rather than looked up - the copper blocks are a weathering collection
        // in 26.2, which TextureMapping refuses.
        turnless(blockModels, ModBlocks.ACCUMULATOR.get(), AccumulatorShape.SHAPE,
                Identifier.withDefaultNamespace("block/cut_copper"),
                Identifier.withDefaultNamespace("block/blue_terracotta"));
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

        // One dispatch over both properties, and the two rotations added by hand.
        //
        // NOT `.with(dispatch).with(ROTATION_HORIZONTAL_FACING)`, which is what this used to be
        // and which is wrong in a way you only see in three of the four directions: a
        // VariantMutator *sets* `y` rather than adding to it, so the facing overwrote each cell's
        // own turn and every corner of an east-facing boiler pointed the same way. The collision
        // boxes were right the whole time, because MachineCell adds the two - so the machine you
        // saw and the machine you walked into were different objects, which is the exact failure
        // Boxes warns about.
        //
        // `tools/check_models.py` now checks this sum against the shape, so it cannot come back.
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

    /** The whole machine in one block, for the item in your hand. */
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

    /** One box of a machine, with the shell's three texture slots on its six faces. */
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
    /**
     * The four poles: the same {@link #turnless} generator four times over, in four materials.
     */
    private void poles(BlockModelGenerators blockModels) {
        // Material wraps the sprite id; the model file wants the plain identifier.
        turnless(blockModels, ModBlocks.SMALL_ELECTRIC_POLE.get(), SmallPoleShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.STRIPPED_OAK_LOG).sprite());
        turnless(blockModels, ModBlocks.MEDIUM_ELECTRIC_POLE.get(), MediumPoleShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.ANVIL).sprite());
        turnless(blockModels, ModBlocks.BIG_ELECTRIC_POLE.get(), BigPoleShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite());
        turnless(blockModels, ModBlocks.SUBSTATION.get(), SubstationShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.DEEPSLATE_TILES).sprite());
    }

    /**
     * A machine with no facing: one dispatch over {@code part} and nothing else.
     *
     * <p>{@link #machine} dispatches over {@code part} and {@code HORIZONTAL_FACING} together,
     * which a block without the second property cannot do. Everything under that - the cell
     * models, the turns, the miniature in the hand - is shared, so this is the dispatch and not a
     * second generator.
     */
    private void turnless(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier texture) {
        turnless(blockModels, block, shape, texture, texture);
    }

    /** The same, with a different texture on top - a panel is a frame with glass in it. */
    private void turnless(BlockModelGenerators blockModels, Block block, MachineShape shape,
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
