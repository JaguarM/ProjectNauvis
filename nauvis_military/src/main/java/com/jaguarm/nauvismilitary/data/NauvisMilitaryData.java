package com.jaguarm.nauvismilitary.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jaguarm.nauvislib.multiblock.Boxes;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.registry.ModBlocks;
import com.jaguarm.nauvismilitary.registry.ModItems;
import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
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
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Generates the turret's models, blockstate and loot table, the items' models, the tags and the
 * language file. Run with {@code ./gradlew :nauvis_military:runClientData} and {@code runServerData}.
 */
@EventBusSubscriber(modid = NauvisMilitary.MODID)
public final class NauvisMilitaryData {

    private NauvisMilitaryData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(Models::new);
        event.createProvider(Lang::new);
    }

    @SubscribeEvent
    static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider((PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) ->
                new LootTableProvider(output, Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(TurretLoot::new, LootContextParamSets.BLOCK)),
                        lookup));
        event.createProvider(BlockTagsData::new);
    }

    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, NauvisMilitary.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add("itemGroup.nauvis_military", "Project Nauvis: Military");
            add("pack.nauvis_military.crafting_table", "Nauvis Military: bench recipes (skips research)");
            // Facrafting's crafting-panel tab for Factorio's combat category, which every recipe
            // here carries; see the other mods' lang files for the other three.
            add("tab.facrafting.group.combat", "Combat");

            addBlock(ModBlocks.GUN_TURRET, "Gun turret");
            addItem(ModItems.PISTOL, "Pistol");
            addItem(ModItems.SUBMACHINE_GUN, "Submachine gun");
            addItem(ModItems.FIREARM_MAGAZINE, "Firearm magazine");
            addItem(ModItems.PIERCING_ROUNDS_MAGAZINE, "Piercing rounds magazine");
            addItem(ModItems.GRENADE, "Grenade");
            addItem(ModItems.LIGHT_ARMOR, "Light armor");
            addItem(ModItems.HEAVY_ARMOR, "Heavy armor");
            add("entity.nauvis_military.grenade", "Grenade");

            add("tooltip.nauvis_military.gun.empty", "Not loaded");
            add("tooltip.nauvis_military.gun.loaded", "Loaded: %s, %s rounds");
            add("status.nauvis_military.turret.no_ammo", "No ammunition");
            add("status.nauvis_military.turret.watching", "Watching, %s rounds");
            add("status.nauvis_military.turret.firing", "Firing, %s rounds");
            add("readout.nauvis_military.pollution", "Pollution %s");
            add("jade.nauvis_military.turret.health", "Health %s/%s - a repair pack mends it");

            add("commands.nauvis_military.pollution.here", "Pollution here: %s (%s made in this world so far)");
            add("commands.nauvis_military.pollution.set", "Pollution here set to %s");
            add("commands.nauvis_military.pollution.source", "Last breathed out at %s, %s, %s - what an attack walks at");

            add("death.attack.nauvis_military.bullet", "%1$s was shot");
            add("death.attack.nauvis_military.bullet.player", "%1$s was shot by %2$s");
            add("death.attack.nauvis_military.turret", "%1$s was gunned down by a turret");
            add("death.attack.nauvis_military.turret.player", "%1$s was gunned down by a turret while fighting %2$s");
            add("death.attack.nauvis_military.grenade", "%1$s was blown up by a grenade");
            add("death.attack.nauvis_military.grenade.player", "%1$s was blown up by %2$s's grenade");
        }
    }

    /** Every block this mod registers is pickaxe work. */
    private static class BlockTagsData extends BlockTagsProvider {

        BlockTagsData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, NauvisMilitary.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
            ModBlocks.BLOCKS.getEntries().forEach(block -> pickaxe.add(block.getKey()));
        }
    }

    /** The turret's four blocks and its miniature, and the items on their own textures. */
    private static class Models extends ModelProvider {

        Models(PackOutput output) {
            super(output, NauvisMilitary.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators items) {
            turret(blockModels, ModBlocks.GUN_TURRET.get());
            // Held like a sword, muzzle forward: vanilla's handheld transform points a texture's
            // top-right corner away from the player, and the guns are drawn to it. A flat item
            // is held like a card and pointed its barrel across the hand.
            for (Item gun : List.of(ModItems.PISTOL.get(), ModItems.SUBMACHINE_GUN.get())) {
                items.generateFlatItem(gun, ModelTemplates.FLAT_HANDHELD_ITEM);
            }
            for (Item item : List.of(ModItems.FIREARM_MAGAZINE.get(), ModItems.PIERCING_ROUNDS_MAGAZINE.get(),
                    ModItems.GRENADE.get(), ModItems.LIGHT_ARMOR.get(), ModItems.HEAVY_ARMOR.get())) {
                items.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
            }
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return ModBlocks.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return ModItems.ITEMS.getEntries().stream();
        }

        /**
         * Every block of the turret, plus the miniature that goes in the player's hand: which
         * block this is and which way the turret faces, on one blockstate, with the cell's own
         * turn and the facing added together - the same sum {@link MachineCell} applies to the
         * collision boxes. See the drills' generator for why it is a sum and not a set.
         */
        private void turret(BlockModelGenerators blockModels, GunTurretBlock block) {
            MachineShape shape = block.shape();
            Map<String, Identifier> models = new HashMap<>();
            for (MachineCell cell : shape.cells()) {
                models.computeIfAbsent(cell.model(), name -> cellModel(blockModels, block, cell));
            }

            PropertyDispatch.C2<MultiVariant, Integer, Direction> dispatch =
                    PropertyDispatch.initial(shape.part(), GunTurretBlock.FACING);
            for (int index = 0; index < shape.cellCount(); index++) {
                MachineCell cell = shape.cell(index);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    dispatch = dispatch.select(index, facing, BlockModelGenerators
                            .plainVariant(models.get(cell.model()))
                            .with(turn(cell.turns() + Boxes.quarterTurns(facing))));
                }
            }
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
            blockModels.registerSimpleItemModel(block, inventoryModel(blockModels, block, shape));
        }

        private Identifier cellModel(BlockModelGenerators blockModels, Block block, MachineCell cell) {
            Identifier id = ModelLocationUtils.getModelLocation(block, "_" + cell.model());
            blockModels.modelOutput.accept(id, () -> {
                JsonArray elements = new JsonArray();
                for (float[] box : cell.boxes()) {
                    elements.add(element(box, true));
                }
                return model(elements);
            });
            return id;
        }

        /** The whole turret in one block, every cell turned and moved to where it sits, at half scale. */
        private Identifier inventoryModel(BlockModelGenerators blockModels, Block block, MachineShape shape) {
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
                return model(elements);
            });
            return id;
        }

        private static JsonObject model(JsonArray elements) {
            JsonObject textures = new JsonObject();
            textures.addProperty("side", texture("gun_turret_side"));
            textures.addProperty("top", texture("gun_turret_top"));
            textures.addProperty("particle", texture("gun_turret_side"));
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/block");
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        }

        private static String texture(String name) {
            return NauvisMilitary.MODID + ":block/" + name;
        }

        /** One box: the top on top, the side everywhere else. Culled only on a block boundary, and only on a block. */
        private static JsonObject element(float[] box, boolean cull) {
            JsonObject element = new JsonObject();
            element.add("from", vector(box[0], box[1], box[2]));
            element.add("to", vector(box[3], box[4], box[5]));
            JsonObject faces = new JsonObject();
            for (Direction direction : Direction.values()) {
                JsonObject face = new JsonObject();
                face.addProperty("texture", direction == Direction.UP ? "#top" : "#side");
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

    /** One turret per turret, however many blocks it is made of: only the anchor carries the entry. */
    private static class TurretLoot extends BlockLootSubProvider {

        TurretLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.VANILLA_SET, registries);
        }

        @Override
        protected void generate() {
            GunTurretBlock block = ModBlocks.GUN_TURRET.get();
            MachineShape shape = block.shape();
            add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition
                            .hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                    .hasProperty(shape.part(), shape.anchor())))))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.GUN_TURRET.get());
        }
    }
}
