package com.jaguarm.nauvis;

import com.jaguarm.nauvis.ore.OreKind;
import com.jaguarm.nauvis.ore.OrePatch;
import com.jaguarm.nauvis.ore.OrePatchFeature;
import com.jaguarm.nauvis.ore.OrePatches;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.bus.api.IEventBus;

/** Tests that run inside a real server, headless, reporting pass or fail on exit. */
public final class NauvisGameTests {

    private NauvisGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(Nauvis.MODID, modEventBus);
        tests.add("pack_loads", info -> new RegistryPresenceTest(info, List.of( "nauvis_materials:iron_gear_wheel", "nauvis_materials:electronic_circuit", "nauvis_mining:burner_mining_drill", "nauvis_military:gun_turret", "nauvis_machines:assembling_machine_1", "nauvis_logistics:burner_inserter", "nauvis_logistics:inserter", "nauvis_logistics:iron_chest", "nauvis_fluids:pipe", "nauvis_power:steam_engine", "nauvis_power:small_electric_pole")), 20);
        tests.add("power_reaches_a_machine", PowerReachesAMachineTest::new, 200, 24);
        tests.add("steam_travels_down_a_pipe", SteamTravelsDownAPipeTest::new, 200, 24);
        tests.add("accumulator_carries_the_night", AccumulatorCarriesTheNightTest::new, 300, 24, true);
        tests.add("vanilla_recipes_are_replaced", VanillaRecipesAreReplacedTest::new, 20);

        // A patch is a solid, wide slab of ore a few layers thick on a floor that rolls a little:
        // every column inside it holds ore, every ore block is within the patch's height, the
        // footprint is tens of blocks across, and nothing but ground was turned to ore.
        tests.add("ore_patch_is_a_solid_rolling_slab", 40, 28, helper -> {
            int size = 48;
            int floor = 0;
            int roof = 15;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    for (int y = floor; y <= roof; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
            BlockPos centre = helper.absolutePos(new BlockPos(size / 2, 7, size / 2));
            OrePatch patch = OrePatches.patch(RandomSource.create(20260908L), OreKind.IRON,
                    centre.getX(), centre.getZ(), centre.getY(), OrePatches.LAYERS_MAX);
            int placed = OrePatchFeature.place(helper.getLevel(), patch);

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int counted = 0;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    BlockPos column = helper.absolutePos(new BlockPos(x, 0, z));
                    boolean inside = patch.contains(column.getX(), column.getZ());
                    boolean any = false;
                    for (int y = floor; y <= roof; y++) {
                        if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.IRON_ORE)) {
                            continue;
                        }
                        any = true;
                        counted++;
                        int worldY = helper.absolutePos(new BlockPos(x, y, z)).getY();
                        helper.assertTrue(worldY >= patch.minY() && worldY <= patch.maxY(),
                                "ore at y " + worldY + " outside the patch's " + patch.minY() + ".." + patch.maxY());
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                        minY = Math.min(minY, worldY);
                        maxY = Math.max(maxY, worldY);
                    }
                    helper.assertValueEqual(any, inside, "ore in the column at " + x + ", " + z
                            + " against the patch saying it is " + (inside ? "inside" : "outside"));
                }
            }
            helper.assertValueEqual(counted, placed, "ore blocks found against ore blocks the feature reports");
            helper.assertTrue(placed >= 350, "a patch of only " + placed + " ore blocks");
            int wide = Math.max(maxX - minX, maxZ - minZ) + 1;
            int deep = Math.min(maxX - minX, maxZ - minZ) + 1;
            helper.assertTrue(wide >= 16 && wide <= 40, "a patch " + wide + " across on its long axis");
            helper.assertTrue(deep >= 9, "a patch " + deep + " across on its short axis");
            helper.assertTrue(maxY - minY + 1 <= OrePatches.LAYERS_MAX + 2 * patch.maxRelief(),
                    "a patch " + (maxY - minY + 1) + " blocks tall is not a slab");
            helper.assertTrue(maxY - minY + 1 >= OrePatches.LAYERS_MAX + 1,
                    "a patch " + (maxY - minY + 1) + " blocks tall lies dead flat");
            helper.assertTrue(helper.getBlockState(new BlockPos(0, 7, 0)).is(Blocks.STONE),
                    "the corner of the box, far outside the patch, is not stone any more");
            helper.succeed();
        });

        // Vanilla's noise ore veins, the snaking iron and copper bodies that no biome modifier
        // reaches, are off: asked of the overworld's noise settings as the generator would ask.
        tests.add("vanilla_ore_veins_are_off", 20, helper -> {
            var settings = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
            helper.assertFalse(settings.oreVeinsEnabled(), "the overworld still grows vanilla's ore veins");
            helper.succeed();
        });

        // The three starting patches: one of each ore, in the starting area, apart from each
        // other, high enough to reach; and no random patch inside the starting area.
        tests.add("starting_patches_are_one_of_each_ore", 20, helper -> {
            for (long seed = 1; seed <= 40; seed++) {
                List<OrePatch> patches = OrePatches.startingPatches(seed);
                helper.assertValueEqual(patches.size(), 3, "starting patches for seed " + seed);
                helper.assertValueEqual(patches.stream().map(OrePatch::kind).distinct().count(), 3L,
                        "distinct ores among the starting patches for seed " + seed);
                for (OrePatch patch : patches) {
                    double distance = Math.hypot(patch.centreX(), patch.centreZ());
                    helper.assertTrue(distance >= OrePatches.STARTING_NEAR && distance <= OrePatches.STARTING_FAR,
                            "a starting patch " + distance + " blocks from the origin, seed " + seed);
                    helper.assertTrue(patch.bottomY() >= OrePatches.STARTING_DEPTH_MIN
                            && patch.bottomY() <= OrePatches.STARTING_DEPTH_MAX,
                            "a starting patch at y " + patch.bottomY() + ", seed " + seed);
                    for (OrePatch other : patches) {
                        if (other != patch) {
                            helper.assertTrue(Math.hypot(other.centreX() - patch.centreX(), other.centreZ() - patch.centreZ())
                                    >= 2 * OrePatches.REACH, "two starting patches overlap, seed " + seed);
                        }
                    }
                }
                for (int cellX = -3; cellX <= 3; cellX++) {
                    for (int cellZ = -3; cellZ <= 3; cellZ++) {
                        OrePatches.patchInCell(seed, cellX, cellZ).ifPresent(patch -> helper.assertTrue(
                                Math.hypot(patch.centreX(), patch.centreZ()) >= OrePatches.STARTING_AREA,
                                "a random patch inside the starting area at " + patch.centreX() + ", " + patch.centreZ()));
                    }
                }
            }
            // The same cell answers the same twice, and a chunk sees the patch its neighbour's cell reaches into.
            helper.assertValueEqual(OrePatches.patchInCell(7L, 5, 9), OrePatches.patchInCell(7L, 5, 9), "one cell, asked twice");
            helper.succeed();
        });

        // Vanilla's scattered iron, copper and coal veins are gone from the overworld and the
        // patches are in: asked of the running biome, not of the files.
        tests.add("vanilla_ore_veins_are_gone", 20, helper -> {
            List<String> features = new java.util.ArrayList<>();
            for (var step : helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value().getGenerationSettings().features()) {
                for (var feature : step) {
                    feature.unwrapKey().ifPresent(key -> features.add(key.identifier().toString()));
                }
            }
            for (String vein : List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small",
                    "minecraft:ore_copper", "minecraft:ore_copper_large", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower")) {
                helper.assertFalse(features.contains(vein), vein + " still generates in the plains");
            }
            helper.assertTrue(features.contains("nauvis:ore_patch"), "the ore patches do not generate in the plains");
            helper.assertTrue(features.contains("minecraft:ore_gold"), "gold, which the pack leaves alone, is gone too");
            helper.succeed();
        });

        // A pickaxe is the axe, the shovel and the hoe as well.
        //
        // <h2>Two tags, and only one of them is the one people reach for</h2>
        //
        // incorrect_for_<material>_tool decides whether a block drops - the tier
        // ladder, wood to netherite. mineable/<tool> decides whether the tool is the right
        // kind, and that is the tag that also carries speed. An earlier version of this
        // emptied the tier tags, which is a real change and completely the wrong one: it let a wooden
        // pickaxe mine obsidian and did nothing whatsoever about a pickaxe taking six seconds to dig
        // dirt. The tiers are back; what merges is which tool does which job.
        //
        // So mineable/pickaxe gains the axe, shovel and hoe tags and a pickaxe is the only
        // tool anyone needs. It is "replace": false, and that is the opposite of what a
        // tag meant to empty something needs - here the merge is the whole point, and writing
        // true would swap the pickaxe's own list for three references and quietly stop
        // pickaxes mining stone.
        //
        // Speed is asserted rather than correctness alone, because correctness is the half that was
        // never broken and speed is the half that was reported.
        tests.add("one_tool_does_everything", 20, helper -> {
            ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);

            // One block from each of the three tags the pickaxe absorbed, plus stone to prove the
            // merge did not replace what was already there.
            for (Block block : List.of(Blocks.OAK_LOG, Blocks.DIRT, Blocks.HAY_BLOCK, Blocks.STONE)) {
                var state = block.defaultBlockState();
                helper.assertTrue(pickaxe.isCorrectToolForDrops(state),
                        "a pickaxe is not the right tool for "
                                + BuiltInRegistries.BLOCK.getKey(block));
                helper.assertTrue(pickaxe.getDestroySpeed(state) > 1.0f,
                        "a pickaxe digs " + BuiltInRegistries.BLOCK.getKey(block)
                                + " at bare-hand speed - it is in mineable/pickaxe for drops but "
                                + "not for speed, which means the tag merge did not take");
            }

            // And the ladder is untouched. Merging which tool does which job is not the same as
            // saying any tool is good enough, and an earlier version of this conflated the two.
            helper.assertFalse(
                    new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(
                            Blocks.OBSIDIAN.defaultBlockState()),
                    "a wooden pickaxe mines obsidian; the tier tags are being emptied again");
            helper.assertTrue(
                    new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(
                            Blocks.OBSIDIAN.defaultBlockState()),
                    "a diamond pickaxe cannot mine obsidian");
            helper.succeed();
        });

        // Every block the pack registers is mined with a pickaxe: it drops, and it drops quickly.
        //
        // The bug this exists for shipped in every machine mod at once. A block that requires the
        // correct tool for its drops and is in no mineable/ tag has no correct tool, so a
        // boiler mined with a pickaxe dropped nothing - and, tag or no tag, dug at bare-hand speed,
        // fifteen seconds for hardness three. Each mod now ships a tag provider; this walks every
        // block in every pack namespace so a mod that forgets its provider fails here rather than in
        // somebody's world. The oil well is skipped because it is unbreakable, which is the point of it.
        tests.add("every_machine_takes_a_pickaxe", 20, helper -> {
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            int checked = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (!id.getNamespace().startsWith("nauvis")) {
                    continue;
                }
                var state = block.defaultBlockState();
                if (state.getDestroySpeed(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) < 0) {
                    continue;  // unbreakable on purpose
                }
                if (state.liquid()) {
                    continue;  // natural water: a bucket's work, as every liquid is
                }
                helper.assertTrue(pickaxe.isCorrectToolForDrops(state),
                        id + " does not drop when mined with a pickaxe - it is in no mineable/ tag, "
                                + "so its mod is missing the tag provider every other mod has");
                helper.assertTrue(pickaxe.getDestroySpeed(state) > 1.0f,
                        id + " digs at bare-hand speed with a pickaxe");
                checked++;
            }
            helper.assertTrue(checked > 10, "only " + checked + " pack blocks were found to check");
            helper.succeed();
        });
        tests.add("timed_recipes_are_timed", TimedRecipesAreTimedTest::new, 20);
        tests.add("an_inserter_hears_a_far_cell_of_a_machine", InserterHearsAFarCellTest::new, 60, 24);
        tests.add("an_inserter_fills_a_silo_and_loses_nothing", InserterFillsASiloTest::new, 200, 24);
    }

    /**
     * Places a block the way a player does, {@code setPlacedBy} included.
     *
     * <p>A power pole is four blocks tall and puts the rest in from there, so a test that only
     * wrote one block state would be building a pole that cannot exist. Calling it for everything
     * costs nothing and needs no knowledge of which blocks care.
     */
    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        place(helper, pos, block, null);
    }

    private static void place(GameTestHelper helper, BlockPos pos, Block block,
            @Nullable Direction facing) {
        if (facing == null) {
            helper.setBlock(pos, block);
        } else {
            helper.setBlock(pos, block, facing);
        }
        BlockPos absolute = helper.absolutePos(pos);
        block.setPlacedBy(helper.getLevel(), absolute,
                helper.getLevel().getBlockState(absolute), null, ItemStack.EMPTY);
    }

    /** A block by id, so the pack mod can name another mod's block without depending on it. */
    private static Block block(GameTestHelper helper, String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        helper.assertTrue(block != Blocks.AIR, "expected " + id + " to be registered, got air");
        return block;
    }

    /**
     * Asserts that a list of item ids is registered once the server is up.
     *
     * <p>Gradle resolving the sibling jars onto the classpath is a different claim from their
     * items existing in the registry. This checks the second, which is the one that matters:
     * it fails if a mod silently declines to load.
     */
    public static class RegistryPresenceTest extends PackGameTest {

        private final List<String> items;

        RegistryPresenceTest(Info info, List<String> items) {
            super(info);
            this.items = items;
        }

        @Override
        public void run(GameTestHelper helper) {
            for (String id : items) {
                // An unregistered id resolves to air rather than throwing, so comparing
                // against AIR is the check. Anything looser passes while proving nothing.
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
            }
            helper.succeed();
        }

    }

    /**
     * <b>Milestone 1, end to end: coal and a lake in one place, a machine running in
     * another.</b>
     */
    public static class PowerReachesAMachineTest extends PackGameTest {

        /** The factory, laid out for machines that are the size Factorio made them. */
        private static final BlockPos BOILER = new BlockPos(0, 1, 0);
        private static final BlockPos ENGINE = new BlockPos(0, 1, 3);
        private static final BlockPos NEAR_POLE = new BlockPos(3, 1, 3);
        /** Six from the first pole, inside the 7.5 wire reach; eight from the engine. */
        private static final BlockPos FAR_POLE = new BlockPos(3, 1, 10);
        private static final BlockPos ASSEMBLER = new BlockPos(5, 1, 11);
        private static final BlockPos UNPOWERED_ASSEMBLER = new BlockPos(5, 8, 11);

        /**
         * Where the water comes from. A boiler takes water at the ends of its front row, which for
         * one facing north is the row in front of the anchor; its east end is one block east of
         * that, and a pipe there reaches an offshore pump facing east, whose outlet is the back
         * of its body and whose intake hangs over a block of natural water.
         */
        private static final BlockPos WATER_PIPE = new BlockPos(2, 1, -1);
        private static final BlockPos PUMP = new BlockPos(3, 1, -1);
        private static final BlockPos LAKE = new BlockPos(4, 0, -1);

        PowerReachesAMachineTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // The pipe first, so it re-reads its faces as each machine arrives beside it.
            place(helper, WATER_PIPE, block(helper, "nauvis_fluids:pipe"));
            place(helper, BOILER, block(helper, "nauvis_power:boiler"));
            helper.setBlock(LAKE, block(helper, "nauvis_fluids:water"));
            place(helper, PUMP, block(helper, "nauvis_fluids:offshore_pump"), Direction.EAST);
            // East, so the engine lies along the line to the boiler. A steam engine takes steam
            // through the two faces on its own axis, and the pack mod can say so without knowing
            // the property: setBlock applies a direction to whatever has a horizontal facing.
            place(helper, ENGINE, block(helper, "nauvis_power:steam_engine"), Direction.NORTH);
            place(helper, NEAR_POLE, block(helper, "nauvis_power:small_electric_pole"));
            place(helper, FAR_POLE, block(helper, "nauvis_power:small_electric_pole"));
            place(helper, ASSEMBLER, block(helper, "nauvis_machines:assembling_machine_1"));
            place(helper, UNPOWERED_ASSEMBLER, block(helper, "nauvis_machines:assembling_machine_1"));

            ResourceHandler<ItemResource> fuel = helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BOILER), null);
            helper.assertTrue(fuel != null, "the boiler published no item capability to fuel it through");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(fuel.insert(ItemResource.of(Items.COAL), 1, transaction), 1,
                        "coal accepted by the boiler");
                transaction.commit();
            }

            // A lake to water to steam to electricity to two poles to a machine. Forty ticks is
            // generous for a chain that moves a tick's worth per tick once it is running.
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(charge(helper, ASSEMBLER) > 0,
                        "an assembler two poles from a running steam engine has no charge, so the "
                                + "grid is not carrying anything");
                helper.assertValueEqual(charge(helper, UNPOWERED_ASSEMBLER), 0,
                        "charge in an assembler no pole can reach");
                helper.succeed();
            });
        }

        private static int charge(GameTestHelper helper, BlockPos pos) {
            EnergyHandler handler = helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), null);
            helper.assertTrue(handler != null, "no energy capability at " + pos);
            return handler.getAmountAsInt();
        }

    }

    /**
     * <b>The pipe pipes.</b> A boiler, four pipes, and an engine that runs on what came down
     * them.
     */
    public static class SteamTravelsDownAPipeTest extends PackGameTest {

        /**
         * A boiler, a run of pipe out of the back of it, and an engine at the far end.
         *
         * <p>North to south, and the numbers come off the shapes: the boiler's steam leaves the
         * south face of the block under its chimney, so the first pipe is one block south of the
         * anchor; the engine takes steam at the open end of its spine, which is two tiles from
         * its middle, so its anchor is three blocks past the last pipe.
         */
        private static final BlockPos BOILER = new BlockPos(0, 1, 0);
        private static final int PIPES = 4;
        /** Just past the last pipe, laid along the line so its ends face the run. */
        private static final BlockPos ENGINE = new BlockPos(0, 1, PIPES + 3);

        /** The same lake, pump and pipe as {@code power_reaches_a_machine}, at the boiler's east end. */
        private static final BlockPos WATER_PIPE = new BlockPos(2, 1, -1);
        private static final BlockPos PUMP = new BlockPos(3, 1, -1);
        private static final BlockPos LAKE = new BlockPos(4, 0, -1);

        SteamTravelsDownAPipeTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, WATER_PIPE, block(helper, "nauvis_fluids:pipe"));
            place(helper, BOILER, block(helper, "nauvis_power:boiler"));
            helper.setBlock(LAKE, block(helper, "nauvis_fluids:water"));
            place(helper, PUMP, block(helper, "nauvis_fluids:offshore_pump"), Direction.EAST);
            for (int z = 1; z <= PIPES; z++) {
                place(helper, new BlockPos(0, 1, z), block(helper, "nauvis_fluids:pipe"));
            }
            place(helper, ENGINE, block(helper, "nauvis_power:steam_engine"), Direction.NORTH);

            ResourceHandler<ItemResource> fuel = helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BOILER), null);
            helper.assertTrue(fuel != null, "the boiler published no item capability to fuel it through");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(fuel.insert(ItemResource.of(Items.COAL), 1, transaction), 1,
                        "coal accepted by the boiler");
                transaction.commit();
            }

            helper.runAfterDelay(80, () -> {
                EnergyHandler charge = helper.getLevel()
                        .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(ENGINE), null);
                helper.assertTrue(charge != null, "no energy capability on the steam engine");
                helper.assertTrue(charge.getAmountAsInt() > 0,
                        "an engine four pipes from a burning boiler made no power, so the pipe run "
                                + "is not carrying steam");
                helper.succeed();
            });
        }

    }

    /**
     * The vanilla-replacement datapack does what it says: four of Minecraft's recipes are gone,
     * and what replaces them is there.
     */
    public static class VanillaRecipesAreReplacedTest extends PackGameTest {

        VanillaRecipesAreReplacedTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // Gone: some because the pack prices them differently, some because vanilla was
            // doing a job the pack has its own answer for. See data/removals.json.
            for (String id : List.of("minecraft:chest", "minecraft:furnace",
                    "minecraft:hopper", "minecraft:hopper_minecart",
                    "minecraft:stone_pickaxe", "minecraft:wooden_pickaxe",
                    "minecraft:iron_ingot_from_smelting_raw_iron",
                    "minecraft:copper_ingot_from_blasting_raw_copper", "minecraft:stone_bricks")) {
                helper.assertFalse(hasRecipe(helper, id),
                        id + " is still craftable; the removal in the nauvis datapack did nothing");
            }

            // And replaced: the pack's own recipe for each thing it took away.
            for (String id : List.of("nauvis_logistics:chest", "nauvis_machines:stone_furnace",
                    "nauvis_logistics:burner_inserter", "nauvis:stone_pickaxe",
                    "nauvis_materials:iron_ingot", "nauvis:stone_bricks")) {
                helper.assertTrue(hasRecipe(helper, id),
                        id + " is missing, so the pack has taken something away and left nothing");
            }

            helper.assertTrue(hasRecipe(helper, "minecraft:crafting_table"),
                    "an ordinary vanilla recipe went missing too - the datapack is removing more "
                            + "than data/removals.json names");

            // Kept on purpose, though the pack makes iron ingots: a block coming back apart is
            // iron changing shape, not a second way of smelting it. See `kept` in removals.json.
            helper.assertTrue(hasRecipe(helper, "minecraft:iron_ingot_from_iron_block"),
                    "the iron block no longer comes apart into ingots; storage blocks are a one-way "
                            + "trip, which no removal was meant to do");

            // Planks are the one entry that is *mirrored* rather than replaced: the pack makes
            // them in the crafting panel at vanilla's own rate, and vanilla's recipe stays, so a
            // log still becomes planks in the 2x2 inventory grid. Both, deliberately.
            helper.assertTrue(hasRecipe(helper, "nauvis:oak_planks"),
                    "the pack has no plank recipe, so the opening cannot be done in the panel");
            helper.assertTrue(hasRecipe(helper, "minecraft:oak_planks"),
                    "vanilla's plank recipe was removed; it is a mirror, not a replacement, and "
                            + "without it a log in the 2x2 grid gives nothing");

            // The dropper is the nearest thing to a hopper that was deliberately kept: it needs a
            // clock to move anything, which is a build rather than a free ride. Asserted so that
            // removing it later is a decision somebody made rather than a list that crept.
            helper.assertTrue(hasRecipe(helper, "minecraft:dropper"),
                    "the dropper was removed; data/removals.json does not name it");
            helper.succeed();
        }

        private static boolean hasRecipe(GameTestHelper helper, String id) {
            return helper.getLevel().getServer().getRecipeManager()
                    .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)))
                    .isPresent();
        }

    }

    /** Every timed recipe is still a timed recipe when a server has finished loading. */
    public static class TimedRecipesAreTimedTest extends PackGameTest {

        /** Facrafting's timed recipe type, by name. See the class comment for why by name. */
        private static final Identifier FACRAFT =
                Identifier.fromNamespaceAndPath("facrafting", "facraft");

        TimedRecipesAreTimedTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (String id : List.of(
                    "nauvis_logistics:transport_belt",
                    "nauvis_logistics:burner_inserter",
                    "nauvis_power:small_electric_pole",
                    "nauvis_fluids:pipe",
                    "nauvis_research:automation_science_pack",
                    "nauvis_materials:iron_gear_wheel",
                    "nauvis_mining:burner_mining_drill")) {

                Identifier type = typeOf(helper, id);
                helper.assertTrue(type != null, id + " has no recipe at all");
                helper.assertTrue(FACRAFT.equals(type),
                        id + " is a " + type + " recipe rather than a timed one - a crafting_table "
                                + "pack is being applied over it. See ModPacks and PITFALLS.md");
            }
            helper.succeed();
        }

        /** The recipe type registered for {@code id}, or null if nothing is registered under it. */
        private static @Nullable Identifier typeOf(GameTestHelper helper, String id) {
            return helper.getLevel().getServer().getRecipeManager()
                    .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)))
                    .map(holder -> BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()))
                    .orElse(null);
        }

    }

    /** Solar by day, the accumulator by night: the grid's third case, end to end. */
    public static class AccumulatorCarriesTheNightTest extends PackGameTest {

        /** The panel's middle; its nine cells reach to x 1, inside the pole's area. */
        private static final BlockPos PANEL = new BlockPos(0, 1, 0);
        private static final BlockPos POLE = new BlockPos(3, 1, 0);
        /** Two by two from its north-west corner, east of the pole. */
        private static final BlockPos ACCUMULATOR = new BlockPos(4, 1, 1);
        /** Its middle; its south row is at z -2, the edge of the pole's area. */
        private static final BlockPos ASSEMBLER = new BlockPos(5, 1, -3);
        /** Over the panel's middle, which is where it looks for the sky. */
        private static final BlockPos ROOF = new BlockPos(0, 4, 0);

        AccumulatorCarriesTheNightTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, PANEL, block(helper, "nauvis_power:solar_panel"));
            place(helper, POLE, block(helper, "nauvis_power:small_electric_pole"));
            place(helper, ACCUMULATOR, block(helper, "nauvis_power:accumulator"));
            int[] peak = new int[1];

            helper.startSequence()
                    .thenExecuteAfter(60, () -> helper.assertTrue(charge(helper, ACCUMULATOR) > 0,
                            "an accumulator beside a solar panel at noon, with nothing else on the "
                                    + "network, took none of the surplus"))
                    .thenExecute(() -> helper.setBlock(ROOF, Blocks.STONE))
                    .thenExecuteAfter(40, () -> {
                        // Dark, and what was left in the panel's own buffer has gone into the
                        // accumulator: a roofed panel is still a generator with something to give
                        // until it is empty.
                        helper.assertValueEqual(charge(helper, PANEL), 0,
                                "charge left in a roofed panel with an accumulator to take it");
                        peak[0] = charge(helper, ACCUMULATOR);
                        place(helper, ASSEMBLER, block(helper, "nauvis_machines:assembling_machine_1"));
                    })
                    .thenExecuteAfter(40, () -> {
                        int assembler = charge(helper, ASSEMBLER);
                        int accumulator = charge(helper, ACCUMULATOR);
                        helper.assertTrue(assembler > 0,
                                "an assembler built at night beside a charged accumulator got "
                                        + "nothing - the accumulator is not covering the shortfall");
                        helper.assertTrue(accumulator < peak[0],
                                "the accumulator gave nothing up while the assembler charged");
                        helper.assertValueEqual(peak[0] - accumulator, assembler,
                                "what the accumulator lost, against what the assembler gained from "
                                        + "the only source on the network");
                    })
                    .thenSucceed();
        }

        private static int charge(GameTestHelper helper, BlockPos pos) {
            EnergyHandler handler = helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), null);
            helper.assertTrue(handler != null, "no energy capability at " + pos);
            return handler.getAmountAsInt();
        }

    }

    /**
     * An inserter feeding a silo stops at Factorio's insertion limit, twice a part's worth and well
     * short of a stack, and every item it took from the chest is in the silo or still in the chest.
     */
    public static class InserterFillsASiloTest extends PackGameTest {

        private static final BlockPos SILO = new BlockPos(0, 1, 0);
        private static final BlockPos INSERTER = new BlockPos(5, 1, 0);
        private static final BlockPos CHEST = new BlockPos(6, 1, 0);

        /** Four swings of the burner inserter, thirty ticks each, and room to wake and sleep. */
        private static final int SWINGS_TICKS = 4 * 30 + 40;

        InserterFillsASiloTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, SILO, block(helper, "nauvis_rocket:rocket_silo"));
            helper.setBlock(CHEST, Blocks.CHEST);
            Block inserter = block(helper, "nauvis_logistics:burner_inserter");
            helper.setBlock(INSERTER, inserter.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            Item structure = item(helper, "nauvis_materials:low_density_structure");

            ResourceHandler<ItemResource> silo = handler(helper, SILO);
            ResourceHandler<ItemResource> chest = handler(helper, CHEST);
            helper.assertValueEqual(insert(silo, structure, 17), 17, "low density structures the silo took by hand");
            helper.assertValueEqual(insert(chest, structure, 5), 5, "low density structures put in the chest");
            helper.assertValueEqual(insert(handler(helper, INSERTER), Items.COAL, 1), 1, "coal the inserter took");

            helper.runAfterDelay(SWINGS_TICKS, () -> {
                // The structures are the rocket part's second ingredient, so its second slot.
                int inSilo = silo.getAmountAsInt(1);
                int inChest = countIn(chest, structure);
                helper.assertValueEqual(inSilo, 20, "low density structures in the silo: twice a part's worth is the limit");
                helper.assertValueEqual(inChest, 2, "low density structures left in the chest");
                helper.assertValueEqual(inSilo + inChest, 22, "low density structures altogether: the inserter lost some");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(INSERTER), inserter),
                        "the inserter is still awake with a full silo in front of it");
                helper.succeed();
            });
        }

        private static ResourceHandler<ItemResource> handler(GameTestHelper helper, BlockPos pos) {
            ResourceHandler<ItemResource> handler = Capabilities.Item.BLOCK.getCapability(
                    helper.getLevel(), helper.absolutePos(pos), null, null, null);
            helper.assertTrue(handler != null, "no item capability at " + pos);
            return handler;
        }

        private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(ItemResource.of(item), count, transaction);
                transaction.commit();
                return inserted;
            }
        }

        private static int countIn(ResourceHandler<ItemResource> handler, Item item) {
            int total = 0;
            for (int index = 0; index < handler.size(); index++) {
                if (handler.getResource(index).getItem() == item) {
                    total += handler.getAmountAsInt(index);
                }
            }
            return total;
        }

        private static Item item(GameTestHelper helper, String id) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
            return item;
        }
    }

    /**
     * A machine's inventory change reaches the inserter at any of its cells, not only the six
     * blocks around its anchor.
     */
    public static class InserterHearsAFarCellTest extends PackGameTest {

        /** The silo's anchor, the middle of its pad; the pad runs four blocks each way from it. */
        private static final BlockPos SILO = new BlockPos(0, 1, 0);
        /** Just past the pad's east edge, one of the inserter's two ends on the edge cell. */
        private static final BlockPos INSERTER = new BlockPos(5, 1, 0);

        InserterHearsAFarCellTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, SILO, block(helper, "nauvis_rocket:rocket_silo"));
            Block inserter = block(helper, "nauvis_logistics:burner_inserter");
            helper.setBlock(INSERTER, inserter.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            Item structure = item(helper, "nauvis_materials:low_density_structure");

            // The burner inserter has no coal: its first tick finds nothing to do and it sleeps.
            helper.runAfterDelay(10, () -> {
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(INSERTER), inserter),
                        "the inserter is still awake, so the test proves nothing");

                ResourceHandler<ItemResource> silo = Capabilities.Item.BLOCK.getCapability(
                        helper.getLevel(), helper.absolutePos(SILO), null, null, null);
                helper.assertTrue(silo != null, "no item capability on the silo");
                int inserted;
                try (Transaction transaction = Transaction.openRoot()) {
                    inserted = silo.insert(ItemResource.of(structure), 1, transaction);
                    transaction.commit();
                }
                helper.assertValueEqual(inserted, 1, "low density structures the silo took");

                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(INSERTER), inserter),
                        "the silo's inventory changed and the inserter at its edge was not woken - "
                                + "Multiblock.announce is not reaching the far cells");
                helper.succeed();
            });
        }

        /** An item by id, so the pack mod can name another mod's item without depending on it. */
        private static Item item(GameTestHelper helper, String id) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
            return item;
        }

    }
}
