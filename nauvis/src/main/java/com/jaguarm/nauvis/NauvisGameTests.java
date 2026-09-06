package com.jaguarm.nauvis;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>This is the half of testing that needs nobody watching. What the pack mod asserts is that
 * the pack itself holds together; how a machine behaves is asserted in the mod that owns the
 * machine, beside it. How any of it looks is asserted nowhere, and never will be.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}: it starts a server with every mod in
 * the pack on one classpath, runs every test in every one of them, and exits non-zero if any
 * failed.
 *
 * <h2>The world these run in is the world the pack ships, and that took arranging</h2>
 *
 * <p>{@code GameTestServer} selects <b>every available datapack</b> - vanilla's own line is
 * {@code new ArrayList<>(packRepository.getAvailableIds())} - so the {@code crafting_table} packs
 * every mod ships <em>switched off</em> used to be on here, and each carries a shapeless copy of a
 * recipe under the same id as the timed one. Sixteen of the pack's nineteen timed recipes were
 * ordinary bench recipes while the suite ran. Nothing failed; what was lost was the craft times.
 *
 * <p>Being switched off is no defence, because vanilla never asks. So the packs are not
 * <em>offered</em> at all under {@code -Djaguarm.benchRecipePacks=false}, which the
 * {@code gameTestServer} run sets - see any mod's {@code ModPacks} - and
 * {@code timed_recipes_are_timed} asks the running recipe manager what type each of them actually
 * is, so the day one of them slips back nothing has to be remembered.
 *
 * <p>The 26.2 shape is registry-driven and unlike every tutorial. See {@code docs/API-26.2.md}
 * — in particular, {@code FunctionGameTestInstance} is unavailable to mods, because the
 * registry its bodies live in is bootstrapped during {@code BuiltInRegistries} static
 * initialisation, before any mod exists. Subclassing {@link GameTestInstance} is the way in.
 */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class NauvisGameTests {

    private NauvisGameTests() {}

    /**
     * A test whose structure is missing silently does not run — {@code placeStructure} returns
     * false and reports nothing. Minecraft ships {@code minecraft:empty}, which is all a test
     * needing no terrain requires.
     */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /**
     * Test types are a registry like any other, and the codec is what a datapack would use to
     * deserialise one. Ours are registered in code and never serialised, but the registry
     * entry still has to exist for the type to be legal.
     */
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Nauvis.MODID);

    static {
        TEST_TYPES.register("registry_presence", () -> RegistryPresenceTest.CODEC);
        TEST_TYPES.register("power_reaches_a_machine", () -> PowerReachesAMachineTest.CODEC);
        TEST_TYPES.register("steam_travels_down_a_pipe", () -> SteamTravelsDownAPipeTest.CODEC);
        TEST_TYPES.register("vanilla_recipes_are_replaced", () -> VanillaRecipesAreReplacedTest.CODEC);
        TEST_TYPES.register("one_tool_does_everything", () -> OneToolDoesEverythingTest.CODEC);
        TEST_TYPES.register("every_machine_takes_a_pickaxe", () -> EveryMachineTakesAPickaxeTest.CODEC);
        TEST_TYPES.register("timed_recipes_are_timed", () -> TimedRecipesAreTimedTest.CODEC);
    }

    /** Called from the mod constructor so the test type registers with everything else. */
    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        // Our own environment rather than a lookup of minecraft:default, because the event
        // exposes no getter for one that already exists. An empty AllOf imposes no conditions,
        // which is exactly what minecraft:default is.
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "pack_loads"),
                new RegistryPresenceTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE),
                        List.of(
                                "neoprogressivematerials:iron_gear_wheel",
                                "neoprogressivematerials:electronic_circuit",
                                "nauvis_mining:burner_mining_drill",
                                "nauvis_machines:assembling_machine_1",
                                "nauvis_logistics:burner_inserter",
                                "nauvis_logistics:inserter",
                                "nauvis_logistics:iron_chest",
                                "nauvis_fluids:pipe",
                                "nauvis_power:steam_engine",
                                "nauvis_power:small_electric_pole")));

        // Padded: this one builds a factory eleven blocks long, well outside the point-sized
        // structure it is given, and a pole from the test next door would join its network.
        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "power_reaches_a_machine"),
                new PowerReachesAMachineTest(new TestData<>(environment, EMPTY_STRUCTURE, 200, 0,
                        true, Rotation.NONE, false, 1, 1, false, 24)));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "steam_travels_down_a_pipe"),
                new SteamTravelsDownAPipeTest(new TestData<>(environment, EMPTY_STRUCTURE, 200, 0,
                        true, Rotation.NONE, false, 1, 1, false, 24)));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "vanilla_recipes_are_replaced"),
                new VanillaRecipesAreReplacedTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE)));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "one_tool_does_everything"),
                new OneToolDoesEverythingTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE)));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "every_machine_takes_a_pickaxe"),
                new EveryMachineTakesAPickaxeTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE)));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "timed_recipes_are_timed"),
                new TimedRecipesAreTimedTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE)));
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
    public static class RegistryPresenceTest extends GameTestInstance {

        public static final MapCodec<RegistryPresenceTest> CODEC = RecordCodecBuilder.<RegistryPresenceTest>mapCodec(
                i -> i.group(
                        TestData.CODEC.forGetter(RegistryPresenceTest::info),
                        Codec.STRING.listOf().fieldOf("items")
                                .forGetter((RegistryPresenceTest test) -> test.items))
                        .apply(i, RegistryPresenceTest::new));

        private final List<String> items;

        public RegistryPresenceTest(TestData<Holder<TestEnvironmentDefinition<?>>> info, List<String> items) {
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

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("registry presence");
        }
    }

    /**
     * <b>Milestone 1, end to end: coal and a lake in one place, a machine running in another.</b>
     *
     * <p>An offshore pump at a block of natural water, a pipe to a boiler, a steam engine beside
     * it, two poles, and an assembler eight blocks from the generator. Every join in that chain is
     * between two mods that do not compile against each other - {@code nauvis_fluids} owns the
     * water and the pipe, {@code nauvis_logistics} could feed the boiler, {@code nauvis_power}
     * makes and carries the electricity, {@code nauvis_machines} spends it - and all of it is held
     * together by NeoForge's capabilities and nothing else. That is exactly the claim non-negotiable #3
     * makes and the one place it can actually be checked, which is why this test is in the pack
     * mod rather than in any of them.
     *
     * <p>Everything is named by id and reached through a capability, so this file still has no
     * compile-time dependency on anything.
     *
     * <p>The second assembler is the control. It is out of reach of both poles, and it stays at
     * zero - without it, a bug that handed energy to every machine in the level would pass.
     */
    public static class PowerReachesAMachineTest extends GameTestInstance {

        public static final MapCodec<PowerReachesAMachineTest> CODEC =
                RecordCodecBuilder.<PowerReachesAMachineTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerReachesAMachineTest::info))
                                .apply(i, PowerReachesAMachineTest::new));

        /**
         * The factory, laid out for machines that are the size Factorio made them.
         *
         * <p>Everything runs north to south, because that is the axis a steam engine's two steam
         * ends are on. A boiler is three by two and gives its steam at the back of the block under
         * its chimney; an engine is five by three and takes steam at the open end of its spine,
         * two tiles from its middle. So an engine fed by a boiler at the origin is anchored three
         * blocks behind it, and the poles stand off to the east, clear of both.
         *
         * <p>The old layout put these one block apart, which is what a one-block machine allowed.
         * It is worth knowing what that failed as: the engine was placed <em>inside</em> the
         * boiler, the boiler's teardown took it down mid-placement, and the crash that came out
         * was a facing property being read off air.
         */
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

        public PowerReachesAMachineTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

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

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power reaches a machine");
        }
    }

    /**
     * <b>The pipe pipes.</b> A boiler, four pipes, and an engine that runs on what came down them.
     *
     * <p>Until now a steam engine had to be built touching its boiler, which is not the
     * arrangement this pack is copying. The interesting part is that none of the three mods
     * involved compiles against another: {@code nauvis_fluids} owns steam and the pipe,
     * {@code nauvis_power} owns the boiler and the engine, and they meet at
     * {@code Capabilities.Fluid.BLOCK}. Only the pack mod can check that they actually do, and it
     * names all four blocks by id.
     *
     * <p>The engine is deliberately five blocks from the boiler - far enough that nothing but the
     * pipe run could be carrying anything.
     */
    public static class SteamTravelsDownAPipeTest extends GameTestInstance {

        public static final MapCodec<SteamTravelsDownAPipeTest> CODEC =
                RecordCodecBuilder.<SteamTravelsDownAPipeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamTravelsDownAPipeTest::info))
                                .apply(i, SteamTravelsDownAPipeTest::new));

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

        public SteamTravelsDownAPipeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

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

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam travels down a pipe");
        }
    }

    /**
     * The vanilla-replacement datapack does what it says: four of Minecraft's recipes are gone,
     * and what replaces them is there.
     *
     * <h2>Why this is a test and not a reading of the files</h2>
     *
     * <p>A removal is a recipe file whose only content is {@code neoforge:never}, and every part
     * of that is a thing that can be silently wrong. The condition name is one: there is no
     * {@code neoforge:false}, and a wrong name throws while parsing <em>one</em> recipe, which is
     * a line in a log and a hopper that is still craftable. The path is another - the file has to
     * be at {@code data/minecraft/recipe/<name>.json}, and a datapack that puts it anywhere else
     * loads perfectly and removes nothing. {@code tools/gen_removals.py} holds the ids to
     * Minecraft's own recipe list, but nothing outside a running server can say the mechanism
     * fires at all.
     *
     * <p>The control matters as much as the removals. {@code minecraft:crafting_table} is not on
     * the list and must still be craftable: a bug that emptied the recipe manager, or a datapack
     * applied too widely, would pass every assertion here that only checked for absence.
     */
    public static class VanillaRecipesAreReplacedTest extends GameTestInstance {

        public static final MapCodec<VanillaRecipesAreReplacedTest> CODEC =
                RecordCodecBuilder.<VanillaRecipesAreReplacedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(VanillaRecipesAreReplacedTest::info))
                                .apply(i, VanillaRecipesAreReplacedTest::new));

        public VanillaRecipesAreReplacedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

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
                    "neoprogressivematerials:iron_ingot", "nauvis:stone_bricks")) {
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

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("vanilla recipes are replaced");
        }
    }

    /**
     * A pickaxe is the axe, the shovel and the hoe as well.
     *
     * <h2>Two tags, and only one of them is the one people reach for</h2>
     *
     * <p>{@code incorrect_for_<material>_tool} decides whether a block <b>drops</b> - the tier
     * ladder, wood to netherite. {@code mineable/<tool>} decides whether the tool is the right
     * <em>kind</em>, and that is the tag that also carries <b>speed</b>. An earlier version of this
     * emptied the tier tags, which is a real change and completely the wrong one: it let a wooden
     * pickaxe mine obsidian and did nothing whatsoever about a pickaxe taking six seconds to dig
     * dirt. The tiers are back; what merges is which tool does which job.
     *
     * <p>So {@code mineable/pickaxe} gains the axe, shovel and hoe tags and a pickaxe is the only
     * tool anyone needs. It is <b>{@code "replace": false}</b>, and that is the opposite of what a
     * tag meant to empty something needs - here the merge is the whole point, and writing
     * {@code true} would swap the pickaxe's own list for three references and quietly stop
     * pickaxes mining stone.
     *
     * <p>Speed is asserted rather than correctness alone, because correctness is the half that was
     * never broken and speed is the half that was reported.
     */
    public static class OneToolDoesEverythingTest extends GameTestInstance {

        public static final MapCodec<OneToolDoesEverythingTest> CODEC =
                RecordCodecBuilder.<OneToolDoesEverythingTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OneToolDoesEverythingTest::info))
                                .apply(i, OneToolDoesEverythingTest::new));

        public OneToolDoesEverythingTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
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
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("one tool does everything");
        }
    }

    /**
     * Every timed recipe is still a timed recipe when a server has finished loading.
     *
     * <p><b>This is the test for a hole that swallowed sixteen of nineteen recipes.</b> Each mod
     * ships a {@code crafting_table} datapack of shapeless bench copies, switched off, and each
     * copy has the <em>same id</em> as the timed recipe it stands in for - so whichever pack is
     * applied last wins and nothing anywhere says which that was. {@code GameTestServer} enables
     * every pack it can see regardless of the switch, so for a long while the suite was quietly
     * exercising bench recipes.
     *
     * <p>Asked of the running recipe manager rather than of the files, for the same reason
     * {@code vanilla_recipes_are_replaced} is: what is on disk and what a server ends up holding
     * are different questions, and only the second one is what a player meets.
     *
     * <p>The type is compared by <em>name</em>. The pack mod depends on no other mod at compile
     * time - non-negotiable #3 - so it may not name {@code FacraftRecipe}, and an identifier is a
     * fact two mods can share without either one importing the other.
     *
     * <p>One from each mod that ships them, chosen small enough to have a bench copy: a recipe too
     * big for a 3x3 grid has no copy to be shadowed by and would pass this test in any world.
     */
    public static class TimedRecipesAreTimedTest extends GameTestInstance {

        public static final MapCodec<TimedRecipesAreTimedTest> CODEC =
                RecordCodecBuilder.<TimedRecipesAreTimedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TimedRecipesAreTimedTest::info))
                                .apply(i, TimedRecipesAreTimedTest::new));

        /** Facrafting's timed recipe type, by name. See the class comment for why by name. */
        private static final Identifier FACRAFT =
                Identifier.fromNamespaceAndPath("facrafting", "facraft");

        public TimedRecipesAreTimedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (String id : List.of(
                    "nauvis_logistics:transport_belt",
                    "nauvis_logistics:burner_inserter",
                    "nauvis_power:small_electric_pole",
                    "nauvis_fluids:pipe",
                    "nauvis_research:science_pack_1",
                    "neoprogressivematerials:iron_gear_wheel",
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

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("the timed recipes are timed recipes");
        }
    }

    /**
     * Every block the pack registers is mined with a pickaxe: it drops, and it drops quickly.
     *
     * <p>The bug this exists for shipped in every machine mod at once. A block that requires the
     * correct tool for its drops and is in no {@code mineable/} tag has no correct tool, so a
     * boiler mined with a pickaxe dropped nothing - and, tag or no tag, dug at bare-hand speed,
     * fifteen seconds for hardness three. Each mod now ships a tag provider; this walks every
     * block in every pack namespace so a mod that forgets its provider fails here rather than in
     * somebody's world. The oil well is skipped because it is unbreakable, which is the point of it.
     */
    public static class EveryMachineTakesAPickaxeTest extends GameTestInstance {

        public static final MapCodec<EveryMachineTakesAPickaxeTest> CODEC =
                RecordCodecBuilder.<EveryMachineTakesAPickaxeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(EveryMachineTakesAPickaxeTest::info))
                                .apply(i, EveryMachineTakesAPickaxeTest::new));

        public EveryMachineTakesAPickaxeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
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
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("every machine takes a pickaxe");
        }
    }
}
