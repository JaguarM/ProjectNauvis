package com.jaguarm.nauvismachines.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerShape;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine2Shape;
import com.jaguarm.nauvismachines.machine.furnace.ElectricFurnaceShape;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceShape;
import com.jaguarm.nauvismachines.machine.radar.RadarShape;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Block and item models, generated from each machine's shape rather than written beside it. */
public class NauvisMachinesModels extends ModelProvider {

    public NauvisMachinesModels(PackOutput output) {
        super(output, NauvisMachines.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // The nine modules, on art of their own from texture-workshop/make_module_textures.py:
        // one chip in Factorio's three colours, with a stripe for each tier above the first.
        for (var module : ModItems.modules()) {
            itemModels.generateFlatItem(module.get(), ModelTemplates.FLAT_ITEM);
        }
        // The repair pack, from texture-workshop/make_material_textures.py.
        itemModels.generateFlatItem(ModItems.REPAIR_PACK.get(), ModelTemplates.FLAT_ITEM);

        Identifier blastSide = TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_side").sprite();
        Identifier metalTop = TextureMapping.getBlockTexture(Blocks.BLAST_FURNACE, "_top").sprite();
        Identifier furnaceSide = TextureMapping.getBlockTexture(Blocks.FURNACE, "_side").sprite();
        Identifier furnaceTop = TextureMapping.getBlockTexture(Blocks.FURNACE, "_top").sprite();
        Identifier iron = TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite();
        Identifier deepslate = TextureMapping.getBlockTexture(Blocks.POLISHED_DEEPSLATE).sprite();
        // Fire, for a lit furnace's mouth. Opaque and animated, and drawn only on upward faces.
        Identifier lava = Identifier.withDefaultNamespace("block/lava_still");

        // A machine body, and machinery on top of it. Two mappings, so the gearbox reads as a
        // moving part rather than as more casing. The first machine is furnace-grey; the second
        // is Factorio's blue, which is how a player tells the tiers apart across a base.
        Textures machinery = new Textures(metalTop, metalTop, metalTop);
        machine(blockModels, ModBlocks.ASSEMBLING_MACHINE_1.get(), AssemblerShape.SHAPE,
                new Look(new Textures(blastSide, metalTop, metalTop),
                        Map.of(AssemblerShape.GEARBOX, machinery), Map.of()), false);
        // The second machine has a fluid box each side and so a facing: its shape has two port
        // cells, and its blockstate turns with the machine as a boiler's does.
        facingMachine(blockModels, ModBlocks.ASSEMBLING_MACHINE_2.get(), AssemblingMachine2Shape.SHAPE,
                new Look(new Textures(Identifier.withDefaultNamespace("block/light_blue_terracotta"), metalTop, metalTop),
                        Map.of(AssemblerShape.GEARBOX, machinery), Map.of()));

        // The furnaces. The stack's top is the fire while it is lit.
        machine(blockModels, ModBlocks.STONE_FURNACE.get(), FurnaceShape.SHAPE,
                new Look(new Textures(furnaceSide, furnaceTop, furnaceTop),
                        Map.of(), Map.of(FurnaceShape.STACK, new Textures(furnaceSide, lava, furnaceTop))), true);
        machine(blockModels, ModBlocks.STEEL_FURNACE.get(), FurnaceShape.SHAPE,
                new Look(new Textures(iron, iron, iron),
                        Map.of(), Map.of(FurnaceShape.STACK, new Textures(iron, lava, iron))), true);
        machine(blockModels, ModBlocks.ELECTRIC_FURNACE.get(), ElectricFurnaceShape.SHAPE,
                new Look(new Textures(deepslate, deepslate, deepslate),
                        Map.of(ElectricFurnaceShape.HOOD, new Textures(iron, iron, iron)),
                        Map.of(ElectricFurnaceShape.HOOD, new Textures(iron, lava, iron))), true);

        // The radar: an iron casing with the dish in the darker metal of its mast.
        machine(blockModels, ModBlocks.RADAR.get(), RadarShape.SHAPE,
                new Look(new Textures(iron, iron, iron),
                        Map.of(RadarShape.DISH, new Textures(deepslate, iron, deepslate)), Map.of()), false);
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

    /**
     * How a machine is dressed: its casing, any part drawn in something else, and any part that
     * changes when the machine is lit.
     */
    private record Look(Textures casing, Map<String, Textures> parts, Map<String, Textures> lit) {

        Textures of(String model) {
            return parts.getOrDefault(model, casing);
        }
    }

    /**
     * Every cell of a machine, plus the miniature that goes in the player's hand.
     *
     * @param lit whether the block carries {@link FurnaceBlock#LIT}, and so needs a dispatch over
     *            it. A block without the property cannot be dispatched over it, which is the only
     *            reason this is a flag rather than a look-up.
     */
    private void machine(BlockModelGenerators blockModels, Block block, MachineShape shape, Look look,
            boolean lit) {
        // Keyed by model name, not by cell: the four corners are one file between them.
        Map<String, Identifier> models = new HashMap<>();
        Map<String, Identifier> litModels = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(), name -> cellModel(blockModels, block, name, cell, look.of(name)));
            if (lit && look.lit().containsKey(cell.model())) {
                litModels.computeIfAbsent(cell.model(), name -> cellModel(
                        blockModels, block, name + "_lit", cell, look.lit().get(name)));
            }
        }

        if (lit) {
            PropertyDispatch.C2<MultiVariant, Integer, Boolean> dispatch =
                    PropertyDispatch.initial(shape.part(), FurnaceBlock.LIT);
            for (int index = 0; index < shape.cellCount(); index++) {
                MachineCell cell = shape.cell(index);
                for (boolean on : new boolean[] {false, true}) {
                    Identifier model = on ? litModels.getOrDefault(cell.model(), models.get(cell.model()))
                            : models.get(cell.model());
                    dispatch = dispatch.select(index, on,
                            BlockModelGenerators.plainVariant(model).with(turn(cell.turns())));
                }
            }
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
        } else {
            PropertyDispatch.C1<MultiVariant, Integer> dispatch = PropertyDispatch.initial(shape.part());
            for (int index = 0; index < shape.cellCount(); index++) {
                MachineCell cell = shape.cell(index);
                dispatch = dispatch.select(index, BlockModelGenerators
                        .plainVariant(models.get(cell.model()))
                        .with(turn(cell.turns())));
            }
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
        }

        // The whole machine, shrunk into one block. See inventoryModel.
        blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape, look.casing()));
    }

    /**
     * A machine that faces: one dispatch over {@code part} and {@code HORIZONTAL_FACING} together,
     * with each cell's own turn and the machine's added by hand. Not two mutators in a row - a
     * {@code VariantMutator} sets {@code y} rather than adding to it, and the facing would overwrite
     * the corner's turn. See {@code NauvisPowerModels} and {@code docs/PITFALLS.md}.
     */
    private void facingMachine(BlockModelGenerators blockModels, Block block, MachineShape shape, Look look) {
        Map<String, Identifier> models = new HashMap<>();
        for (MachineCell cell : shape.cells()) {
            models.computeIfAbsent(cell.model(), name -> cellModel(blockModels, block, name, cell, look.of(name)));
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
        blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape, look.casing()));
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

    /** The whole machine in one block, for the item in your hand. */
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

    /** One box, with the machine's three texture slots on its six faces. */
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
