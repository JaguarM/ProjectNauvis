package com.jaguarm.nauvisfluids.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantShape;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;
import com.jaguarm.nauvisfluids.refinery.OilRefineryShape;
import com.jaguarm.nauvisfluids.tank.StorageTankShape;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModItems;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelInstance;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Block and item models. */
public class NauvisFluidsModels extends ModelProvider {

    public NauvisFluidsModels(PackOutput output) {
        super(output, NauvisFluids.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        pipe(blockModels);
        crudeOil(blockModels);

        // The one flat item. Its art is texture-workshop/make_material_textures.py's.
        itemModels.generateFlatItem(ModItems.EXPLOSIVES.get(), ModelTemplates.FLAT_ITEM);
        // The barrels: one drum in eight bands, from texture-workshop/make_barrel_textures.py.
        itemModels.generateFlatItem(ModItems.BARREL.get(), ModelTemplates.FLAT_ITEM);
        for (var barrel : ModItems.FILLED_BARRELS) {
            itemModels.generateFlatItem(barrel.get(), ModelTemplates.FLAT_ITEM);
        }

        // Natural water is drawn by the fluid renderer, not from a model, exactly as vanilla's
        // water is; what a liquid block's model carries is the particle its splashes use. This
        // is vanilla's own water model, sprite for sprite.
        blockModels.createAirLikeBlock(ModBlocks.WATER.get(),
                new Material(Identifier.withDefaultNamespace("block/water_still")));

        // Dark metal, with a blast furnace's top for the decks: a pumpjack is heavy iron standing
        // in oil. Not the anvil's top, which was the first choice and drew every upward face with
        // two slits in it - anvil_top.png has three transparent columns down each side, being the
        // sprite for a block that is not a full cube. tools/check_models.py refuses that now.
        machine(blockModels, ModBlocks.PUMPJACK.get(), PumpjackShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.POLISHED_BLACKSTONE).sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite());

        // The offshore pump is mostly pipe, so it is the pipe's iron, with the same top as the
        // pumpjack so the two read as one family of machines.
        machine(blockModels, ModBlocks.OFFSHORE_PUMP.get(), OffshorePumpShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite());
        // The oil machines share the pumpjack's dark shell, so the oil field and the refinery
        // behind it read as one family; what stands on each deck is drawn in something else so
        // it reads as the part that makes the machine that machine.
        Identifier shell = TextureMapping.getBlockTexture(Blocks.POLISHED_BLACKSTONE).sprite();
        Identifier deck = TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite();
        Identifier iron = TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite();
        // Copper is a weathering collection in 26.2, so its texture is named directly.
        Identifier copper = Identifier.withDefaultNamespace("block/copper_block");
        machine(blockModels, ModBlocks.STORAGE_TANK.get(), StorageTankShape.SHAPE, shell, deck,
                Map.of(StorageTankShape.DRUM, iron));
        machine(blockModels, ModBlocks.OIL_REFINERY.get(), OilRefineryShape.SHAPE, shell, deck,
                Map.of(OilRefineryShape.TOWER, iron, OilRefineryShape.DRUM, copper));
        // Factorio's chemical plant is the teal one.
        machine(blockModels, ModBlocks.CHEMICAL_PLANT.get(), ChemicalPlantShape.SHAPE,
                Identifier.withDefaultNamespace("block/cyan_terracotta"), deck,
                Map.of(ChemicalPlantShape.VAT, iron));
    }

    /**
     * An oil well: the ground with oil on it.
     *
     * <p>Black concrete on top, dirt below and round the sides, so a well sits in a field as a dark
     * puddle rather than as a cube of something. The dyed blocks are {@code ColorCollection}s in
     * 26.2 and their textures are still {@code block/<colour>_<block>}, so the top is named directly.
     */
    private static void crudeOil(BlockModelGenerators blockModels) {
        Block well = ModBlocks.CRUDE_OIL.get();
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.TOP, new Material(Identifier.withDefaultNamespace("block/black_concrete")))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.DIRT))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(Blocks.DIRT));
        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(well, textures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(well, BlockModelGenerators.plainVariant(model)));
        blockModels.registerSimpleItemModel(well, model);
    }

    private static void pipe(BlockModelGenerators blockModels) {
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

    // --- machines, generated from their shapes exactly as nauvis_power generates its own ---------

    /**
     * Every cell of a machine, plus the miniature that goes in the player's hand.
     *
     * <p>The same generator {@code nauvis_power} has, duplicated rather than shared for the reason
     * non-negotiable #3 gives. One dispatch over both {@code part} and the facing, with the two
     * rotations <em>added</em> by hand: a {@code VariantMutator} sets {@code y} rather than adding
     * to it, so chaining the facing after the cell's turn overwrote it in three directions of four.
     * See {@code docs/PITFALLS.md}; {@code tools/check_models.py} checks the sum against the shape.
     */
    private static void machine(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier side, Identifier top) {
        machine(blockModels, block, shape, side, top, Map.of());
    }

    /**
     * The same, with some models drawn in a texture of their own: {@code parts} names a model
     * and the texture for all six of its faces, for the drum on a tank or the column on a
     * refinery, so the mechanism stands out from the shell it stands on.
     */
    private static void machine(BlockModelGenerators blockModels, Block block, MachineShape shape,
            Identifier side, Identifier top, Map<String, Identifier> parts) {
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(), name -> parts.containsKey(name)
                    ? cellModel(blockModels, block, cell, parts.get(name), parts.get(name))
                    : cellModel(blockModels, block, cell, side, top));
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
     * The whole machine in one block, for the item in your hand: every cell turned by its own
     * {@code turns}, moved to where it sits, and the lot scaled until the longest side is one block.
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

    /**
     * One box of a machine, with the shell's three texture slots on its six faces. {@code cullface}
     * only where a face lies on a block boundary, and only for a block model - see the same method
     * in {@code nauvis_power} for why.
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
