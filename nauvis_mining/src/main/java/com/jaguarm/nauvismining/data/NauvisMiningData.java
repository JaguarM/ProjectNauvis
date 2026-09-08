package com.jaguarm.nauvismining.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvismining.registry.ModBlocks;
import com.jaguarm.nauvismining.registry.ModItems;

import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Generates the drills' models, blockstates and loot tables. */
@EventBusSubscriber(modid = NauvisMining.MODID)
public final class NauvisMiningData {

    private NauvisMiningData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(Models::new);
    }

    @SubscribeEvent
    static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider((PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) ->
                new LootTableProvider(
                        output,
                        Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(DrillLoot::new,
                                LootContextParamSets.BLOCK)),
                        lookup));
        event.createProvider(BlockTagsData::new);
    }

    /**
     * Which tool mines what: every block this mod registers is pickaxe work.
     *
     * <p>A block that requires the correct tool for its drops and is in no {@code mineable/} tag
     * has no correct tool - it never drops - and any block in no such tag digs at bare-hand speed,
     * a boiler taking fifteen seconds. Every machine here was in that state until this provider
     * existed. With the tag an iron pickaxe takes a machine of hardness three down in three
     * quarters of a second, which is about what Factorio's mining time gives.
     */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisMining.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
            tag(com.jaguarm.nauvismining.registry.ModTags.FACTORIO_ORES)
                    .addTag(net.neoforged.neoforge.common.Tags.Blocks.ORES_IRON)
                    .addTag(net.neoforged.neoforge.common.Tags.Blocks.ORES_COPPER)
                    .addTag(net.neoforged.neoforge.common.Tags.Blocks.ORES_COAL);
        }
    }

    /** One model per distinct shape, and one blockstate that turns them. */
    public static class Models extends ModelProvider {

        Models(PackOutput output) {
            super(output, NauvisMining.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators items) {
            for (MachineTier tier : MachineTier.values()) {
                drill(blockModels, ModBlocks.DRILLS.get(tier).get());
            }
        }

        /**
         * The drills, and nothing else.
         *
         * <p>{@code ModelProvider} checks that every block and item it is told about came out with
         * a model, and refuses to finish if one did not - which is the check that would have
         * caught half the visual bugs this mod ever had. So it is told exactly what it is
         * responsible for.
         */
        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return ModBlocks.DRILLS.values().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return ModItems.DRILLS.values().stream();
        }

        /**
         * Every block of one drill, plus the miniature that goes in the player's hand.
         *
         * <p>Three dispatches on one blockstate: which block of the drill this is, whether the
         * drill is running, and which way it faces. The first two pick a model and the third
         * turns it, on top of whatever turn the cell already had - the same sum
         * {@link MachineCell} applies to the collision boxes, which is what keeps the thing you
         * see and the thing you walk into the same object.
         */
        private void drill(BlockModelGenerators blockModels, MinerBlock block) {
            MachineShape shape = block.shape();

            // The block that wears the drill's face is the one the player clicks, which is the
            // firebox on a burner and the output head on an electric. It is the only one with a
            // lit variant, because it is the only one showing a front.
            String faceModel = shape.cell(shape.placement()).model();

            Map<String, Identifier> off = new HashMap<>();
            for (MachineCell cell : shape.cells()) {
                off.computeIfAbsent(cell.model(), name ->
                        cellModel(blockModels, block, cell, name.equals(faceModel), false));
            }
            Identifier lit = cellModel(blockModels, block, shape.cell(shape.placement()), true, true);

            // All three properties in one dispatch, with the cell's own turn and the drill's
            // facing added together by hand.
            //
            // NOT `.with(dispatch).with(ROTATION_HORIZONTAL_FACING)`: a VariantMutator *sets*
            // `y` rather than adding to it, so the facing would overwrite each cell's turn and
            // every corner of an east-facing burner drill would point the same way. The collision
            // boxes add the two, so the drill you saw and the drill you walked into would be
            // different shapes - visible in three directions out of four.
            PropertyDispatch.C3<MultiVariant, Integer, Boolean, Direction> dispatch =
                    PropertyDispatch.initial(shape.part(), MinerBlock.LIT, MinerBlock.FACING);
            for (int index = 0; index < shape.cellCount(); index++) {
                MachineCell cell = shape.cell(index);
                boolean isFace = cell.model().equals(faceModel);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VariantMutator turn = turn(cell.turns() + Boxes.quarterTurns(facing));
                    dispatch = dispatch
                            .select(index, false, facing, BlockModelGenerators
                                    .plainVariant(off.get(cell.model())).with(turn))
                            .select(index, true, facing, BlockModelGenerators
                                    .plainVariant(isFace ? lit : off.get(cell.model())).with(turn));
                }
            }

            blockModels.blockStateOutput.accept(
                    MultiVariantGenerator.dispatch(block).with(dispatch));

            blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape));
        }

        private Identifier cellModel(BlockModelGenerators blockModels, MinerBlock block,
                MachineCell cell, boolean showsFace, boolean lit) {
            Identifier id = ModelLocationUtils.getModelLocation(block,
                    "_" + cell.model() + (lit ? "_on" : ""));
            blockModels.modelOutput.accept(id, () -> {
                JsonArray elements = new JsonArray();
                for (float[] box : cell.boxes()) {
                    elements.add(element(box, true, showsFace));
                }
                return model(block, elements, lit);
            });
            return id;
        }

        /**
         * The whole drill in one block, for the item in your hand.
         *
         * <p>A single block of a nine-block drill reads as a metal plate. The geometry is read out
         * of the shape a second time instead - every cell turned by its own turn, moved to where
         * that cell sits, and scaled until the drill's longest side is one block - so the item is
         * a miniature of the machine and cannot fall out of step with it.
         */
        private Identifier inventoryModel(BlockModelGenerators blockModels, MinerBlock block,
                MachineShape shape) {
            Identifier id = ModelLocationUtils.getModelLocation(block, "_inventory");
            blockModels.modelOutput.accept(id, () -> {
                int longest = Math.max(shape.width(), Math.max(shape.height(), shape.depth()));
                float scale = 1.0F / longest;
                float shiftX = (16.0F - 16.0F * shape.width() / longest) / 2.0F;
                float shiftZ = (16.0F - 16.0F * shape.depth() / longest) / 2.0F;

                String faceModel = shape.cell(shape.placement()).model();
                JsonArray elements = new JsonArray();
                for (MachineCell cell : shape.cells()) {
                    boolean showsFace = cell.model().equals(faceModel);
                    for (float[] box : Boxes.rotate(cell.boxes(), cell.turns())) {
                        elements.add(element(new float[] {
                            shiftX + scale * (box[0] + 16 * cell.x()),
                            scale * (box[1] + 16 * cell.y()),
                            shiftZ + scale * (box[2] + 16 * cell.z()),
                            shiftX + scale * (box[3] + 16 * cell.x()),
                            scale * (box[4] + 16 * cell.y()),
                            shiftZ + scale * (box[5] + 16 * cell.z()),
                        }, false, showsFace));
                    }
                }
                return model(block, elements, false);
            });
            return id;
        }

        /** The drill's own textures, which are the ones this mod already shipped. */
        private JsonObject model(MinerBlock block, JsonArray elements, boolean lit) {
            String name = block.tier().id();
            JsonObject textures = new JsonObject();
            textures.addProperty("side", texture(name + "_side"));
            textures.addProperty("top", texture(name + "_top"));
            textures.addProperty("front", texture(name + "_front" + (lit ? "_on" : "")));
            // Without a particle texture a broken or walked-on block throws up the missing one.
            textures.addProperty("particle", texture(name + "_side"));

            JsonObject model = new JsonObject();
            // block/block, not block/cube: it carries the display transforms an item needs and no
            // geometry of its own.
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        }

        private static String texture(String name) {
            return NauvisMining.MODID + ":block/" + name;
        }

        /**
         * One box, textured the way a drill is: a face on the front, the top on top, sides else.
         *
         * <p>{@code cullface} only where a face lies exactly on a block boundary, and only on a
         * block model - that is what keeps the nine blocks of a drill from drawing eight interior
         * walls. It must never go on a face hanging outside its own block, and the miniature is a
         * whole drill inside one block, so it gets none.
         */
        private static JsonObject element(float[] box, boolean cull, boolean showsFace) {
            JsonObject element = new JsonObject();
            element.add("from", vector(box[0], box[1], box[2]));
            element.add("to", vector(box[3], box[4], box[5]));

            JsonObject faces = new JsonObject();
            for (Direction direction : Direction.values()) {
                JsonObject face = new JsonObject();
                face.addProperty("texture", switch (direction) {
                    case UP -> "#top";
                    case NORTH -> showsFace ? "#front" : "#side";
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

        private static VariantMutator turn(int turns) {
            return switch (Math.floorMod(turns, 4)) {
                case 1 -> BlockModelGenerators.Y_ROT_90;
                case 2 -> BlockModelGenerators.Y_ROT_180;
                case 3 -> BlockModelGenerators.Y_ROT_270;
                default -> BlockModelGenerators.NOP;
            };
        }
    }

    /** One drill per drill, however many blocks it is made of. */
    private static class DrillLoot extends BlockLootSubProvider {

        DrillLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            for (MachineTier tier : MachineTier.values()) {
                MinerBlock block = ModBlocks.DRILLS.get(tier).get();
                MachineShape shape = block.shape();
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block,
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1.0F))
                                .add(LootItem.lootTableItem(block).when(
                                        LootItemBlockStatePropertyCondition
                                                .hasBlockStateProperties(block)
                                                .setProperties(StatePropertiesPredicate.Builder
                                                        .properties()
                                                        .hasProperty(shape.part(),
                                                                shape.anchor())))))));
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModBlocks.DRILLS.values().stream().map(holder -> (Block) holder.get()).toList();
        }
    }
}
