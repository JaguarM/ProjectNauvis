package com.jaguarm.nauvislogistics.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlock;
import com.jaguarm.nauvislogistics.belt.BeltShape;
import com.jaguarm.nauvislogistics.belt.Belts;
import com.jaguarm.nauvislogistics.belt.SplitterShape;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
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
 * {@code texture-workshop/}.
 */
public class NauvisLogisticsModels extends ModelProvider {

    /**
     * One belt tier: three models, the blockstate that picks between them, and the item.
     *
     * <p>A tier is a name and a palette. The models differ only in which top texture they name -
     * the slab and its sides are the same object whichever way a belt bends and however fast it
     * is - and the texture names are the block's own, which is what
     * {@code texture-workshop/make_belt_textures.py} writes for that tier.
     */
    private static void belt(BlockModelGenerators blockModels, Block block, String textures) {
        Identifier straight = beltModel(blockModels, block, textures, "", "_top");
        Identifier left = beltModel(blockModels, block, textures, "_left", "_top_left");
        Identifier right = beltModel(blockModels, block, textures, "_right", "_top_right");
        Identifier up = beltRamp(blockModels, block, textures, "_up", true);
        Identifier down = beltRamp(blockModels, block, textures, "_down", false);

        PropertyDispatch.C1<MultiVariant, BeltShape> bend = PropertyDispatch.initial(BeltBlock.SHAPE)
                .select(BeltShape.STRAIGHT, BlockModelGenerators.plainVariant(straight))
                .select(BeltShape.FROM_LEFT, BlockModelGenerators.plainVariant(left))
                .select(BeltShape.FROM_RIGHT, BlockModelGenerators.plainVariant(right))
                .select(BeltShape.UP, BlockModelGenerators.plainVariant(up))
                .select(BeltShape.DOWN, BlockModelGenerators.plainVariant(down));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(bend)
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        blockModels.registerSimpleItemModel(block, straight);
    }

    /**
     * Half the diagonal of a block, which is how long a ramp across one is.
     *
     * <p>A slope rises a whole block over a whole block, so its surface is the tile's diagonal:
     * {@code 16 * sqrt(2)}, a little over 22 pixels, against the 16 a flat belt is. That is also
     * why an item crosses a ramp faster than it crosses a flat belt - see {@code docs/GAPS.md}.
     */
    private static final float RAMP_REACH = (float) (8.0 * Math.sqrt(2.0));

    /** How tall a belt is drawn, in pixels: {@link Belts#HEIGHT} in the units a model speaks. */
    private static final float BELT_PIXELS = (float) (Belts.HEIGHT * 16.0);

    /**
     * How far the slanted slab is held in from the sides of its block.
     *
     * <p>A tenth of a pixel, and it is the whole of the z-fighting fix. The slab overlaps two
     * things it cannot help overlapping - the square box under its low end, and the flat belt at
     * the top of the climb, which its tilted underside reaches into - and while all three spanned
     * the full width, they sat their side faces on the same two planes and flickered against each
     * other.
     *
     * <p><b>It is the slanted piece that gives way, not the square ones</b>, because it is the one
     * doing the overlapping: pull it in and every pair of coincident planes goes at once, while the
     * flat joints stay exactly as wide as the belts they meet. A tenth of a pixel is a hundred and
     * sixtieth of a block - far too small to see, far too big for a depth buffer to confuse.
     *
     * <p>In rather than out. A slab wider than its block would reach into the belt line laid beside
     * it, and belt lines are laid side by side all the time.
     */
    private static final float RAMP_INSET = 0.1F;

    /**
     * A sloped belt: one slanted slab, and one square box to end it with.
     *
     * <p><b>Vanilla's raised rail is a plane with no thickness</b> - {@code template_rail_raised_ne}
     * is a single element whose Y extent is zero - which is why it can be rotated 45 degrees and
     * still meet a flat rail cleanly. A belt is half a block thick, and both of the awkward parts
     * here come from that.
     *
     * <p>The slab is sized to the block's <em>diagonal</em> rather than its side, so its top surface
     * runs corner to corner: from half a block up at the low edge to half a block up in the block
     * above at the high edge, which is exactly where the flat belts at either end are.
     * {@code rescale} would stretch it corner to corner too, and would stretch the thickness with
     * it - an 8-pixel belt would come out 11.
     *
     * <p><b>Its ends tilt with it</b>, being a rotated box, so neither end meets a flat belt's
     * upright face squarely. At the low end that leaves a wedge of open air between two belts that
     * are meant to be one line, and the square box below closes it. At the high end it is the other
     * way about - the underside reaches past the block and into the belt at the top of the climb -
     * and that one is left alone, because the material is inside that belt or lying along the top
     * of it, which is a thing a conveyor does.
     *
     * <p>What is <em>not</em> left alone is the flickering, and {@link #RAMP_INSET} is the whole of
     * that fix. Both overlaps are unavoidable - a square box cannot end flush against a 45-degree
     * face without either overlapping it or leaving a gap, and a tilted end cannot stop square at a
     * block boundary - but neither has to share a plane with what it overlaps.
     *
     * @param rises whether the ramp climbs the way the belt faces, which is the whole of the
     *              difference between {@link BeltShape#UP} and {@link BeltShape#DOWN}
     */
    private static Identifier beltRamp(BlockModelGenerators blockModels, Block block, String base,
            String suffix, boolean rises) {
        Identifier id = ModelLocationUtils.getModelLocation(block, suffix);
        blockModels.modelOutput.accept(id, () -> {
            JsonArray elements = new JsonArray();

            // The slab, laid flat and pivoted about the midpoint of the surface it will become.
            // Modelled facing north, like every other belt model, so `up` climbs towards -Z.
            JsonObject ramp = beltElement(new float[] {
                RAMP_INSET, 16 - BELT_PIXELS, 8 - RAMP_REACH,
                16 - RAMP_INSET, 16, 8 + RAMP_REACH}, false, true);
            JsonObject rotation = new JsonObject();
            rotation.add("origin", vector(8, 16, 8));
            rotation.addProperty("axis", "x");
            rotation.addProperty("angle", rises ? 45 : -45);
            ramp.add("rotation", rotation);
            elements.add(ramp);

            // The low end, squared off against the flat belt it joins - full width, because the
            // slab is the piece that gave way and this is a joint that has to line up.
            float near = rises ? 8 : 0;
            elements.add(beltElement(
                    new float[] {0, 0, near, 16, BELT_PIXELS, near + 8}, true, false));

            return beltModel(elements, base);
        });
        return id;
    }

    /**
     * One box of a belt model, textured the way a slab is: tread on top, tin down the sides.
     *
     * @param stretch whether the top and bottom carry one whole tread stretched over the element,
     *                which is what the ramp itself wants: it is longer than its block, so a derived
     *                UV would sample the tread from past the edge of its own texture and bring it
     *                back wrapped. Everything else takes the slice of tread under its own footprint.
     *                Neither is left to be derived, because none of these elements stays inside its
     *                block - {@code tools/check_models.py} fails the build on a derived UV that
     *                cannot be trusted.
     */
    private static JsonObject beltElement(float[] box, boolean cull, boolean stretch) {
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
            if (direction.getAxis() != Direction.Axis.Y) {
                // The lower half of the side texture, which is the half a half-height belt shows -
                // vanilla's slab model crops to exactly these rows and the trim band lines up with
                // the flat belt next to it because of it.
                face.add("uv", uv(0, 8, 16, 16));
            } else if (stretch) {
                // One tread over the whole ramp. It is a block and a bit long, so the pattern runs
                // slower up a slope than along the flat - which is the same stretch the items on it
                // get, and so reads as one thing rather than two.
                face.add("uv", uv(0, 0, 16, 16));
            } else {
                // The slice of tread this box actually stands on, which is what Minecraft would
                // have derived for it - said out loud because a box that leaves its block in any
                // direction gets no derived UV it can trust, and the ramp's end caps climb out of
                // theirs. See tools/check_models.py.
                face.add("uv", uv(box[0], box[2], box[3], box[5]));
            }
            if (cull && direction == Direction.DOWN) {
                face.addProperty("cullface", direction.getSerializedName());
            }
            faces.add(direction.getSerializedName(), face);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject beltModel(JsonArray elements, String base) {
        JsonObject textures = new JsonObject();
        textures.addProperty("top", NauvisLogistics.MODID + ":block/" + base + "_top");
        textures.addProperty("side", NauvisLogistics.MODID + ":block/" + base + "_side");
        textures.addProperty("bottom", NauvisLogistics.MODID + ":block/" + base + "_bottom");
        textures.addProperty("particle", NauvisLogistics.MODID + ":block/" + base + "_side");

        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonArray uv(float u1, float v1, float u2, float v2) {
        JsonArray array = new JsonArray();
        array.add(u1);
        array.add(v1);
        array.add(u2);
        array.add(v2);
        return array;
    }

    /** One belt model: a bottom slab, with the given top. */
    private static Identifier beltModel(BlockModelGenerators blockModels, Block block, String base,
            String suffix, String top) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.TOP, texture(base + top))
                .put(TextureSlot.SIDE, texture(base + "_side"))
                .put(TextureSlot.BOTTOM, texture(base + "_bottom"));
        return suffix.isEmpty()
                ? ModelTemplates.SLAB_BOTTOM.create(block, textures, blockModels.modelOutput)
                : ModelTemplates.SLAB_BOTTOM.createWithSuffix(
                        block, suffix, textures, blockModels.modelOutput);
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

        // The belts: a bottom slab each, because they are half a block high and you walk over
        // them. Two tiers, one call apiece - a belt tier is a name and a palette, and the tread's
        // animation comes from the .mcmeta the texture workshop writes beside the PNG.
        belt(blockModels, ModBlocks.TRANSPORT_BELT.get(), "transport_belt");
        belt(blockModels, ModBlocks.FAST_TRANSPORT_BELT.get(), "fast_transport_belt");

        // The splitters: 2x1 multiblock machines with an iron housing, and a top that says which
        // belt they keep pace with - gold for the yellow tier, redstone for the red one. The same
        // shape and the same call twice, because a splitter tier is a speed and a colour.
        machine(blockModels, ModBlocks.SPLITTER.get(), SplitterShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                TextureMapping.getBlockTexture(Blocks.GOLD_BLOCK).sprite());
        machine(blockModels, ModBlocks.FAST_SPLITTER.get(), SplitterShape.SHAPE,
                TextureMapping.getBlockTexture(Blocks.IRON_BLOCK).sprite(),
                TextureMapping.getBlockTexture(Blocks.REDSTONE_BLOCK).sprite());

        // The chests are drawn by a block entity renderer, not by a block model, so what
        // `createChest` writes is a blockstate pointing at a model that holds nothing but a
        // particle texture - the block you see is the lid, the box and the latch in
        // MetalChestRenderer - plus an item model that is vanilla's chest special-renderer
        // pointed at our sprite. One call does all three files, which is worth using rather than
        // reproducing: the item model alone is a `minecraft:special` wrapper most mods get wrong.
        //
        // The particle block is what a broken chest scatters and what dust falls off it, so it is
        // the metal rather than the texture: iron for one tier and, for the other, the anvil,
        // which is vanilla's only worked-steel surface that is not also stone.
        //
        // The texture id has no path prefix and no extension. `ChestSpecialRenderer` and
        // `Sheets.CHEST_MAPPER` both put `entity/chest/` in front of it, so this names
        // assets/nauvis_logistics/textures/entity/chest/iron.png, which the chest atlas picks up
        // because its one source is a directory over every namespace. See MetalChestRenderer.
        blockModels.createChest(ModBlocks.IRON_CHEST.get(), Blocks.IRON_BLOCK,
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "iron"), false);
        blockModels.createChest(ModBlocks.STEEL_CHEST.get(), Blocks.ANVIL,
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "steel"), false);
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
