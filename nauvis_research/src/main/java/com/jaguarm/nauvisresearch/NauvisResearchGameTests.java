package com.jaguarm.nauvisresearch;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.transfer.MachinePower;
import java.util.List;
import java.util.Set;

import com.jaguarm.nauvisresearch.lab.LabBlock;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.ResearchState;
import com.jaguarm.nauvisresearch.research.Technology;
import com.jaguarm.nauvisresearch.research.TechnologyLayout;
import com.jaguarm.facrafting.progress.MiningListeners;
import com.jaguarm.facrafting.queue.CraftTicker;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer} for the whole pack, or
 * {@code :nauvis_research:runGameTestServer} for this mod alone.
 *
 * <p>The one worth reading is {@code lab_sleeps}. A lab with nothing to do must cost nothing -
 * non-negotiable #5 - and the hard half is that a lab which stopped for want of power is not
 * ticking, so nothing it does can start it again. The wake has to arrive from whatever fills its
 * buffer.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class NauvisResearchGameTests {

    private NauvisResearchGameTests() {}

    /** A test whose structure is missing silently does not run. Minecraft ships an empty one. */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** Where every test puts its lab: one block up, so it is not inside the floor. */
    private static final BlockPos LAB = new BlockPos(0, 1, 0);

    /**
     * Room around each test.
     *
     * <p>A grid test builds outside the structure it is given, and a lab is three blocks across.
     * Without padding the machines of one test land in the next one along, and the failure turns
     * up in whichever ran second.
     */
    private static final int PADDING = 16;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisResearch.MODID);

    static {
        TEST_TYPES.register("lab_is_ten_blocks", () -> LabIsTenBlocksTest.CODEC);
        TEST_TYPES.register("lab_researches", () -> LabResearchesTest.CODEC);
        TEST_TYPES.register("lab_needs_power", () -> LabNeedsPowerTest.CODEC);
        TEST_TYPES.register("lab_sleeps", () -> LabSleepsTest.CODEC);
        TEST_TYPES.register("lab_keeps_its_packs", () -> LabKeepsItsPacksTest.CODEC);
        TEST_TYPES.register("lab_breaks_as_one", () -> LabBreaksAsOneTest.CODEC);
        TEST_TYPES.register("technology_tree_loads", () -> TechnologyTreeLoadsTest.CODEC);
        TEST_TYPES.register("research_unlocks_a_recipe", () -> ResearchUnlocksARecipeTest.CODEC);
        TEST_TYPES.register("lab_ignores_the_wrong_packs", () -> LabIgnoresTheWrongPacksTest.CODEC);
        TEST_TYPES.register("the_crafting_gate_is_installed", () -> CraftingGateIsInstalledTest.CODEC);
        TEST_TYPES.register("research_gates_the_early_machines", () -> ResearchGatesTheEarlyMachinesTest.CODEC);
        TEST_TYPES.register("research_command_moves_the_tree", () -> ResearchCommandTest.CODEC);
        TEST_TYPES.register("a_trigger_finishes_research", () -> TriggerFinishesResearchTest.CODEC);
        TEST_TYPES.register("a_mine_trigger_finishes_research", () -> MineTriggerFinishesResearchTest.CODEC);
        TEST_TYPES.register("pumping_oil_finishes_oil_processing", () -> PumpingOilFinishesOilProcessingTest.CODEC);
        TEST_TYPES.register("a_panel_craft_counts", () -> PanelCraftCountsTest.CODEC);
        TEST_TYPES.register("technology_layout_is_sound", () -> TechnologyLayoutTest.CODEC);
        TEST_TYPES.register("research_keeps_its_progress", () -> ResearchKeepsItsProgressTest.CODEC);
        TEST_TYPES.register("a_lab_spends_blue_science", () -> LabSpendsBlueScienceTest.CODEC);
        TEST_TYPES.register("bonuses_reach_the_world", () -> BonusesReachTheWorldTest.CODEC);
        TEST_TYPES.register("lab_takes_modules", () -> LabTakesModulesTest.CODEC);
    }

    /** Called from the mod constructor so the test types register with everything else. */
    public static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        /*
         * A second environment, which is a second *batch*, which is the only way a test here can
         * have the world's research to itself.
         *
         * <p>Padding separates blocks and nothing separates world state - one `ResearchState` is
         * shared by every test in a run, and the tests in one batch run at the same time. Every
         * other test here copes by naming technologies no other test names. `/research grant`
         * cannot: it completes the prerequisites too, and every costed technology's chain runs
         * back through `steam-power` and `automation`, which four other tests are researching.
         * Completing one clears the current research, and `lab_researches` failed saying a lab
         * had done no work, mentioning commands nowhere.
         *
         * <p>Batches run one after another, so a test alone in one cannot overlap anything. It
         * still puts the tree back afterwards, for whatever batch comes next.
         */
        Holder<TestEnvironmentDefinition<?>> alone = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "alone"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "lab_is_ten_blocks", LabIsTenBlocksTest::new, 20);
        register(event, environment, "lab_researches", LabResearchesTest::new, 300);
        register(event, environment, "lab_needs_power", LabNeedsPowerTest::new, 60);
        register(event, environment, "lab_sleeps", LabSleepsTest::new, 60);
        register(event, environment, "lab_keeps_its_packs", LabKeepsItsPacksTest::new, 40);
        register(event, environment, "lab_breaks_as_one", LabBreaksAsOneTest::new, 40);
        register(event, environment, "technology_tree_loads", TechnologyTreeLoadsTest::new, 20);
        register(event, environment, "research_unlocks_a_recipe", ResearchUnlocksARecipeTest::new, 20);
        register(event, environment, "lab_ignores_the_wrong_packs", LabIgnoresTheWrongPacksTest::new, 120);
        register(event, environment, "the_crafting_gate_is_installed", CraftingGateIsInstalledTest::new, 20);
        register(event, environment, "research_gates_the_early_machines",
                ResearchGatesTheEarlyMachinesTest::new, 20);
        register(event, environment, "a_trigger_finishes_research", TriggerFinishesResearchTest::new, 20);
        register(event, environment, "a_panel_craft_counts", PanelCraftCountsTest::new, 20);
        register(event, environment, "technology_layout_is_sound", TechnologyLayoutTest::new, 20);
        register(event, environment, "lab_takes_modules", LabTakesModulesTest::new, 20);

        // Alone in their batch. See above.
        register(event, alone, "research_command_moves_the_tree", ResearchCommandTest::new, 20);
        register(event, alone, "research_keeps_its_progress", ResearchKeepsItsProgressTest::new, 20);
        register(event, alone, "bonuses_reach_the_world", BonusesReachTheWorldTest::new, 20);

        /*
         * A third batch, for the two tests that complete oil gathering's whole chain and hold it
         * there while a machine runs. Everything in `alone` is synchronous and puts the tree back
         * within its own tick; a test that waits forty ticks with the tree moved cannot share a
         * batch with anything that reads it.
         */
        Holder<TestEnvironmentDefinition<?>> oil = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "oil"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, oil, "a_mine_trigger_finishes_research", MineTriggerFinishesResearchTest::new, 20);
        register(event, oil, "pumping_oil_finishes_oil_processing", PumpingOilFinishesOilProcessingTest::new, 100);

        /*
         * A fourth, for the one test that points the world's labs at a blue technology and runs a
         * lab through a whole unit of it. Every lab test in `default` points them at automation and
         * agrees with the others about it; this one would pull their labs onto a thirty-second unit.
         */
        Holder<TestEnvironmentDefinition<?>> blue = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "blue"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, blue, "a_lab_spends_blue_science", LabSpendsBlueScienceTest::new, 700);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory,
            int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, PADDING)));
    }

    /**
     * Puts a whole lab in, all ten blocks of it.
     *
     * <p>Not {@code helper.setBlock}: one block of a machine standing alone is destroyed by its
     * own teardown rule the moment anything beside it changes, so a test that placed one would
     * fail somewhere else entirely.
     */
    private static LabBlockEntity placeLab(GameTestHelper helper) {
        LabBlock block = ModBlocks.LAB.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(LAB),
                block.defaultBlockState());
        return helper.getBlockEntity(LAB, LabBlockEntity.class);
    }

    /** Fills the lab's buffer the way a pole would. */
    private static void charge(LabBlockEntity lab) {
        try (Transaction transaction = Transaction.openRoot()) {
            lab.gridView().insert(LabBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * Points the world at a technology, whatever it was doing before.
     *
     * <p>Research is per-world - see {@code ResearchState} - so it is <b>shared between every test
     * in a run</b>, which is a hazard worth naming: these tests can be batched together and a test
     * that quietly assumed nothing else touched the tree would fail once in a while and never
     * twice the same way. Each of them therefore resets what it is about to use, and the
     * assertions below are about a single lab's own counters rather than about the world's, except
     * where the world's is the point.
     */
    private static void research(GameTestHelper helper, String path) {
        MinecraftServer server = helper.getLevel().getServer();
        ResearchState state = Research.state(server);
        finish(server, state, ModTechnologies.key(path), true);
        Research.setCurrent(server, ModTechnologies.key(path));
    }

    /**
     * Completes everything a technology needs first, so a test can point a lab at it.
     *
     * <p>The tree has prerequisites now - nothing early is free - so a test that just set its
     * research would be refused by {@link Research#setCurrent} and would then fail somewhere else
     * entirely, reporting a lab that did no work.
     */
    private static void finish(MinecraftServer server, ResearchState state,
            ResourceKey<Technology> key, boolean skipSelf) {
        Technology technology = ModTechnologies.registry(server.registryAccess()).getValue(key);
        if (technology == null) {
            return;
        }
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            finish(server, state, prerequisite, false);
        }
        if (skipSelf) {
            state.forget(key);
        } else {
            state.complete(key);
        }
    }

    private static Technology technology(GameTestHelper helper, String path) {
        return ModTechnologies.registry(helper.getLevel().registryAccess())
                .getValue(ModTechnologies.key(path));
    }

    /** The key of the recipe file {@code gen_recipes.py} writes for an item. */
    private static ResourceKey<Recipe<?>> recipe(String namespace, String path) {
        return ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static boolean isScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(LAB), ModBlocks.LAB.get());
    }

    /** A lab is ten blocks in a 3x3, with one block entity, and every block knows where it is. */
    public static class LabIsTenBlocksTest extends GameTestInstance {

        public static final MapCodec<LabIsTenBlocksTest> CODEC =
                RecordCodecBuilder.<LabIsTenBlocksTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabIsTenBlocksTest::info))
                                .apply(i, LabIsTenBlocksTest::new));

        public LabIsTenBlocksTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            LabBlock block = ModBlocks.LAB.get();
            placeLab(helper);

            helper.assertValueEqual(LabShape.SHAPE.width(), 3, "tiles across");
            helper.assertValueEqual(LabShape.SHAPE.depth(), 3, "tiles deep");
            helper.assertValueEqual(LabShape.SHAPE.cellCount(), 10, "blocks in a lab");

            int entities = 0;
            for (int part = 0; part < LabShape.SHAPE.cellCount(); part++) {
                BlockPos cell = LabShape.SHAPE.cellPos(LAB, part, Direction.NORTH);
                helper.assertBlockPresent(block, cell);
                helper.assertValueEqual(
                        Multiblock.anchorPos(block, helper.getBlockState(cell),
                                helper.absolutePos(cell)),
                        helper.absolutePos(LAB), "anchor as seen from " + cell);
                if (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null) {
                    entities++;
                }
            }
            helper.assertValueEqual(entities, 1, "block entities in one lab");

            // Fed from a far corner, which is the point of having a footprint: an inserter can
            // stand anywhere along a lab rather than at one privileged block.
            ResourceHandler<ItemResource> corner = Capabilities.Item.BLOCK.getCapability(
                    helper.getLevel(), helper.absolutePos(LAB.offset(-1, 0, -1)), null, null,
                    Direction.WEST);
            helper.assertTrue(corner != null, "no item capability at a lab's corner");
            helper.assertValueEqual(insert(corner, ModItems.SCIENCE_PACK_1.get(), 3), 3,
                    "packs taken at the far corner");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab is ten blocks");
        }
    }

    /**
     * Given packs, power and something to research, a lab finishes a unit on time and the world
     * hears about it.
     *
     * <p>Both halves matter and they used to be one. The lab's own count is a readout for whoever
     * is looking at that machine; the world's is the research, and it is what makes twelve labs
     * worth more than one. A lab that counted its own cycles and told nobody would pass every
     * assertion this test made before the tree existed.
     */
    public static class LabResearchesTest extends GameTestInstance {

        public static final MapCodec<LabResearchesTest> CODEC =
                RecordCodecBuilder.<LabResearchesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabResearchesTest::info))
                                .apply(i, LabResearchesTest::new));

        public LabResearchesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // Automation: ten units of red science at ten seconds each, and Factorio's first
            // technology. The numbers come from the generated tree rather than from here, so a
            // regenerated dump moves the test with it instead of breaking it.
            research(helper, "automation");
            int unitTicks = technology(helper, "automation").ticksPerUnit();

            LabBlockEntity lab = placeLab(helper);
            charge(lab);
            helper.assertValueEqual(insert(lab.automationView(),
                    ModItems.SCIENCE_PACK_1.get(), 2), 2, "packs accepted");

            helper.assertValueEqual(lab.cycles(), 0, "research before it has done any");

            // One tick short: still working, and the pack not yet spent.
            helper.runAfterDelay(unitTicks - 1, () -> {
                helper.assertValueEqual(lab.cycles(), 0,
                        "research a tick before the unit is due");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 2,
                        "packs a tick before the unit is due");
            });

            helper.runAfterDelay(unitTicks + 2, () -> {
                helper.assertValueEqual(lab.cycles(), 1, "research after one unit");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 1,
                        "packs left after one unit");
                helper.assertTrue(lab.energyStored() < LabBlockEntity.ENERGY_CAPACITY,
                        "a lab that researched without spending any power");
                helper.assertTrue(Research.state(helper.getLevel().getServer()).units() >= 1,
                        "the world heard nothing about a unit the lab finished");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab researches");
        }
    }

    /**
     * With packs but no power, a lab does nothing at all.
     *
     * <p>Worth its own test because the failure is silent in the wrong direction: a lab that
     * researched on nothing would be strictly better than one with a wire to it, and nobody
     * would report that as a bug.
     */
    public static class LabNeedsPowerTest extends GameTestInstance {

        public static final MapCodec<LabNeedsPowerTest> CODEC =
                RecordCodecBuilder.<LabNeedsPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabNeedsPowerTest::info))
                                .apply(i, LabNeedsPowerTest::new));

        public LabNeedsPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // Everything but the wire: a technology chosen and the packs for it in the slots.
            // Without the research this would pass for the wrong reason.
            research(helper, "automation");

            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 4);

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(lab.progress(), 0, "progress on an unpowered lab");
                helper.assertValueEqual(lab.cycles(), 0, "research done on nothing");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 4,
                        "packs an unpowered lab ate");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab needs power");
        }
    }

    /**
     * An idle lab schedules nothing, and power arriving is what wakes it.
     *
     * <p>Non-negotiable #5, and the half that is easy to get wrong: a lab that stopped for want of
     * power is not ticking, so it cannot notice the grid coming back by itself. The wake comes
     * from {@code MachinePower}, on whatever thread of control filled the buffer. Delete that callback
     * and this is the test that goes red.
     */
    public static class LabSleepsTest extends GameTestInstance {

        public static final MapCodec<LabSleepsTest> CODEC =
                RecordCodecBuilder.<LabSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabSleepsTest::info))
                                .apply(i, LabSleepsTest::new));

        public LabSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            LabBlockEntity lab = placeLab(helper);

            // Empty and unpowered: after the tick it asks for on placement, it should stop.
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(isScheduled(helper), "an empty lab is still ticking");

                insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 1);
                helper.assertTrue(isScheduled(helper), "a pack arriving did not wake the lab");

                helper.runAfterDelay(5, () -> {
                    // Packs but no power: it stops again, and only energy can restart it.
                    helper.assertFalse(isScheduled(helper),
                            "a lab with no power is still ticking");

                    charge(lab);
                    helper.assertTrue(isScheduled(helper),
                            "power arriving did not wake the lab - see MachinePower");
                    helper.succeed();
                });
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab sleeps");
        }
    }

    /**
     * A hopper cannot pull the science back out of a lab.
     *
     * <p>A lab has no output, so its automation view has to be one-way. Without that rule a hopper
     * put under a lab to feed it would drain it instead, which looks like the lab eating nothing.
     */
    public static class LabKeepsItsPacksTest extends GameTestInstance {

        public static final MapCodec<LabKeepsItsPacksTest> CODEC =
                RecordCodecBuilder.<LabKeepsItsPacksTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabKeepsItsPacksTest::info))
                                .apply(i, LabKeepsItsPacksTest::new));

        public LabKeepsItsPacksTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 5);

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(
                        lab.automationView().extract(
                                ItemResource.of(ModItems.SCIENCE_PACK_1.get()), 5, transaction),
                        0, "packs taken back out of a lab");
                transaction.commit();
            }
            helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 5, "packs still in the lab");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab keeps its packs");
        }
    }

    /** Break any one of the ten and the whole lab comes down, giving back one lab and its packs. */
    public static class LabBreaksAsOneTest extends GameTestInstance {

        public static final MapCodec<LabBreaksAsOneTest> CODEC =
                RecordCodecBuilder.<LabBreaksAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabBreaksAsOneTest::info))
                                .apply(i, LabBreaksAsOneTest::new));

        public LabBreaksAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 7);

            // A corner, which has no block entity and no loot entry of its own.
            helper.getLevel().destroyBlock(helper.absolutePos(LAB.offset(-1, 0, -1)), true);

            helper.runAfterDelay(2, () -> {
                for (int part = 0; part < LabShape.SHAPE.cellCount(); part++) {
                    helper.assertBlockPresent(Blocks.AIR,
                            LabShape.SHAPE.cellPos(LAB, part, Direction.NORTH));
                }
                helper.assertItemEntityCountIs(ModItems.LAB.get(), LAB, 4.0, 1);
                helper.assertItemEntityCountIs(ModItems.SCIENCE_PACK_1.get(), LAB, 4.0, 7);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab breaks as one");
        }
    }

    /**
     * The generated tree is on disk, loads, and still says what Factorio says.
     *
     * <p>{@code checkTechnologies} already diffs the files against the generator, so what this
     * adds is that they <em>load</em>: a datapack registry entry whose codec rejects it does not
     * fail the build, it fails at world load with one line in a log nobody reads, and the
     * research list is then quietly short.
     *
     * <p>Automation is the one asserted by hand because it is the technology this pack's players
     * meet first - ten units of red science at ten seconds, unlocking the assembling machine and
     * the long-handed inserter - and because a mistake in it would be a mistake in every number
     * the generator produces.
     */
    public static class TechnologyTreeLoadsTest extends GameTestInstance {

        public static final MapCodec<TechnologyTreeLoadsTest> CODEC =
                RecordCodecBuilder.<TechnologyTreeLoadsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TechnologyTreeLoadsTest::info))
                                .apply(i, TechnologyTreeLoadsTest::new));

        public TechnologyTreeLoadsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            var registry = ModTechnologies.registry(helper.getLevel().registryAccess());
            helper.assertTrue(registry.size() >= 20,
                    "only " + registry.size() + " technologies loaded");

            // The first one, and the only kind that can start an empty world: no prerequisites,
            // no cost, and a trigger naming something a player can make with a pickaxe.
            Technology steam = technology(helper, "steam_power");
            helper.assertTrue(steam != null, "no nauvis_research:steam_power in the tree");
            helper.assertTrue(steam.isTriggered(), "steam power is not triggered");
            helper.assertTrue(steam.prerequisites().isEmpty(),
                    "steam power has prerequisites; nothing could ever start it");
            helper.assertValueEqual(steam.trigger().orElseThrow().target(),
                    Identifier.withDefaultNamespace("iron_ingot"), "what steam power watches for");
            helper.assertValueEqual(steam.trigger().orElseThrow().count(), 50,
                    "how many steam power watches for");
            helper.assertTrue(steam.unlocks().contains(recipe("nauvis_power", "boiler")),
                    "steam power does not unlock the boiler");

            // And one paid for the ordinary way, which is what a lab is for.
            Technology automation = technology(helper, "automation");
            helper.assertTrue(automation != null, "no nauvis_research:automation in the tree");
            helper.assertFalse(automation.isTriggered(), "automation is triggered; it has a cost");
            helper.assertValueEqual(automation.units(), 10, "units of automation");
            helper.assertValueEqual(automation.ticksPerUnit(), 200, "ticks a unit of automation");
            helper.assertValueEqual(automation.packs().size(), 1, "kinds of pack automation wants");
            helper.assertValueEqual(automation.packs().get(0),
                    Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "science_pack_1"),
                    "the pack automation wants");
            helper.assertTrue(
                    automation.unlocks().contains(recipe("nauvis_machines", "assembling_machine_1")),
                    "automation does not unlock the assembling machine");

            // Every technology is one or the other. Neither means it can never be finished.
            for (Holder.Reference<Technology> holder : registry.listElements().toList()) {
                helper.assertTrue(
                        holder.value().isTriggered() != (holder.value().units() > 0),
                        holder.key().identifier() + " has both a cost and a trigger, or neither");
            }

            // The tree is a graph and every edge has to land somewhere. A dangling prerequisite
            // is a technology nobody can ever start, and it shows in the list looking normal.
            for (Holder.Reference<Technology> holder : registry.listElements().toList()) {
                for (ResourceKey<Technology> prerequisite : holder.value().prerequisites()) {
                    helper.assertTrue(registry.get(prerequisite).isPresent(),
                            holder.key().identifier() + " needs " + prerequisite.identifier()
                                    + ", which is not in the tree");
                }
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("the technology tree loads");
        }
    }

    /**
     * Finishing a technology unlocks its recipes, and says so loudly enough for an open panel.
     *
     * <p>Two assertions, and the second is the one this pack has been bitten by twice. An unlock
     * that is only ever tested by asking a player who already has it is not tested: what actually
     * happens is that a technology completes <b>while the crafting panel is open</b>, and the
     * newly unlocked recipe has to appear without the screen being closed and reopened. Nothing
     * headless can look at a panel, but the mechanism it watches is
     * {@code Research#revision}, and this asserts that it moves.
     *
     * <p>Steel processing rather than automation, deliberately: research is per-world and the
     * world is shared with every other test in the run, so completing a technology some other test
     * has made its current research would clear that research out from under it. See
     * {@link #research}.
     */
    public static class ResearchUnlocksARecipeTest extends GameTestInstance {

        public static final MapCodec<ResearchUnlocksARecipeTest> CODEC =
                RecordCodecBuilder.<ResearchUnlocksARecipeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ResearchUnlocksARecipeTest::info))
                                .apply(i, ResearchUnlocksARecipeTest::new));

        public ResearchUnlocksARecipeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> steel = ModTechnologies.key("steel_processing");
            ResourceKey<Recipe<?>> steelPlate = recipe("neoprogressivematerials", "steel_plate");
            ResourceKey<Recipe<?>> belt = recipe("nauvis_logistics", "transport_belt");

            Research.state(server).forget(steel);
            Research.invalidate();

            helper.assertFalse(Research.isUnlocked(server, steelPlate),
                    "steel plate is craftable before steel processing is researched");

            // A recipe no technology mentions is not gated by anything. That is Factorio's rule
            // and it is what makes a belt craftable in the first minute; a lock that defaulted
            // to closed would leave a new world with nothing to build at all.
            helper.assertTrue(Research.isUnlocked(server, belt),
                    "a belt, which no technology unlocks, is locked");

            int before = Research.revision();
            Research.state(server).complete(steel);
            Research.changedExternally(server);

            helper.assertTrue(Research.isUnlocked(server, steelPlate),
                    "steel plate is still locked after researching steel processing");
            helper.assertTrue(Research.revision() != before,
                    "the revision did not move, so an open crafting panel would never notice");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("research unlocks a recipe");
        }
    }

    /**
     * A lab eats the packs the technology asks for and nothing else.
     *
     * <p>Before there was a tree, a cycle consumed one of every kind of item the lab happened to
     * be holding, which was right when the only item that could be in there was a science pack.
     * It is wrong now, and wrong in the expensive direction: a lab fed anything at all would eat
     * it. This puts a redstone in a lab researching automation and expects it to sit there.
     */
    public static class LabIgnoresTheWrongPacksTest extends GameTestInstance {

        public static final MapCodec<LabIgnoresTheWrongPacksTest> CODEC =
                RecordCodecBuilder.<LabIgnoresTheWrongPacksTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabIgnoresTheWrongPacksTest::info))
                                .apply(i, LabIgnoresTheWrongPacksTest::new));

        public LabIgnoresTheWrongPacksTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            research(helper, "automation");

            LabBlockEntity lab = placeLab(helper);
            charge(lab);
            insert(lab.automationView(), Items.REDSTONE, 4);

            // Well past a unit of automation would take if it were counting this as science.
            helper.runAfterDelay(60, () -> {
                helper.assertValueEqual(lab.cycles(), 0, "research done on redstone");
                helper.assertValueEqual(lab.progress(), 0, "progress on a lab with no packs");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 4,
                        "redstone a lab ate");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab ignores the wrong packs");
        }
    }

    /**
     * Facrafting is actually asking us, and gets the right answer.
     *
     * <p>Everything else here tests {@code Research} directly, which would keep passing if
     * {@code FacraftingLock.install()} were deleted from the mod constructor - and then nothing at
     * all would be gated, in a way no test and no log line would mention. This asserts the wiring:
     * a locked recipe put to <em>Facrafting's</em> lock comes back locked, and an ungated one
     * comes back craftable.
     *
     * <p>It reaches across a mod boundary on purpose and it is the only thing here that does.
     * Facrafting is runtimeOnly and compileOnly for this mod - see build.gradle - so this test
     * compiles and runs wherever the mod itself does.
     *
     * <p><b>Logistics rather than automation, and that is not arbitrary.</b> Research is per-world
     * and the world is shared with every other test in the run, so completing a technology that
     * another test has set as its current research clears that research out from under it -
     * {@code ResearchState#complete} does exactly that, and the test that broke reported a lab
     * having done no work rather than anything about research. Logistics is nobody's current
     * research here. See {@link #research}.
     */
    /**
     * {@code /research} grants and forgets whole chains, and the tree it leaves is consistent.
     *
     * <p>Run through the dispatcher rather than by calling the command's methods, because half of
     * what could be wrong is not in those methods: a command that never registered, an argument
     * that will not parse a datapack registry key, a permission check that keeps the server's own
     * source out. All three would leave every method here passing.
     *
     * <p>The claim worth asserting is the cascade. {@code grant automation-2} has to bring
     * {@code steel-processing}, {@code science-pack-2}, {@code automation} and their prerequisites
     * with it, and forgetting one of those has to take {@code automation-2} back out - a tree that
     * says a technology is researched while its prerequisite is not is a state nothing else in the
     * pack produces, so producing it here would make every odd screen afterwards have two causes.
     *
     * <p>Like every research test it names technologies no other test names, and puts the world
     * back afterwards: one {@code SavedData} is shared by every test in the run. See PITFALLS.md.
     */
    public static class ResearchCommandTest extends GameTestInstance {

        public static final MapCodec<ResearchCommandTest> CODEC =
                RecordCodecBuilder.<ResearchCommandTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ResearchCommandTest::info))
                                .apply(i, ResearchCommandTest::new));

        public ResearchCommandTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);

            ResourceKey<Technology> target = ModTechnologies.key("automation_2");
            ResourceKey<Technology> steel = ModTechnologies.key("steel_processing");
            ResourceKey<Technology> green = ModTechnologies.key("science_pack_2");

            // **Snapshotted, not just cleared.** One SavedData is shared by every test in the
            // run, and `grant` cascades - granting automation-2 completes automation, which is
            // the technology lab_researches points a lab at. Forgetting only the three named
            // below left that one researched and failed a test that mentions research nowhere.
            // Putting the tree back exactly is the only cleanup that survives a command whose
            // whole point is that it moves more than you named. See PITFALLS.md.
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());

            List<ResourceKey<Technology>> mine = List.of(target, steel, green);
            mine.forEach(state::forget);
            Research.changedExternally(server);

            run(server, "research grant " + target.identifier());

            helper.assertTrue(state.isCompleted(target), "automation-2 after granting it");
            helper.assertTrue(state.isCompleted(steel),
                    "steel processing - grant is supposed to bring the prerequisites");
            helper.assertTrue(state.isCompleted(green),
                    "green science - grant is supposed to bring the prerequisites");

            run(server, "research forget " + steel.identifier());

            helper.assertFalse(state.isCompleted(steel), "steel processing after forgetting it");
            helper.assertFalse(state.isCompleted(target),
                    "automation-2 still researched after its prerequisite was forgotten - forget "
                            + "is supposed to take the dependants with it");
            // Green science does not depend on steel, so it stays. A forget that took the whole
            // tree would pass every assertion above.
            helper.assertTrue(state.isCompleted(green),
                    "green science, which does not depend on steel processing, went with it");

            for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                if (!before.contains(key)) {
                    state.forget(key);
                }
            }
            before.forEach(state::complete);
            Research.changedExternally(server);
            helper.succeed();
        }

        /** Through the dispatcher, from the server's own source, which is a gamemaster. */
        private static void run(MinecraftServer server, String command) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("research command moves the tree");
        }
    }

    public static class CraftingGateIsInstalledTest extends GameTestInstance {

        public static final MapCodec<CraftingGateIsInstalledTest> CODEC =
                RecordCodecBuilder.<CraftingGateIsInstalledTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(CraftingGateIsInstalledTest::info))
                                .apply(i, CraftingGateIsInstalledTest::new));

        public CraftingGateIsInstalledTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> logistics = ModTechnologies.key("logistics");
            ResourceKey<Recipe<?>> splitter = recipe("nauvis_logistics", "splitter");
            ResourceKey<Recipe<?>> belt = recipe("nauvis_logistics", "transport_belt");

            Research.state(server).forget(logistics);
            Research.changedExternally(server);

            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

            helper.assertFalse(RecipeLocks.isOpen(),
                    "no recipe lock is installed - see NauvisResearch's ModList check");

            // The other half of the same wiring, and the half that fails silently: without it a
            // craft in Facrafting's panel tells research nothing, so the technologies triggered by
            // crafting - red science among them - could never finish. Everything else would keep
            // working, which is why it is asserted rather than assumed.
            helper.assertTrue(com.jaguarm.facrafting.queue.CraftListeners.installed() > 0,
                    "no craft listener is installed, so crafting cannot finish a triggered "
                            + "technology - see FacraftingLock.install");
            helper.assertFalse(RecipeLocks.isUnlocked(player, splitter),
                    "the splitter is craftable before Logistics is researched");
            helper.assertTrue(RecipeLocks.isUnlocked(player, belt),
                    "a belt, which no technology unlocks, is locked");

            Research.state(server).complete(logistics);
            Research.changedExternally(server);
            helper.assertTrue(RecipeLocks.isUnlocked(player, splitter),
                    "the splitter is still locked after researching Logistics");

            // Put it back: research is per-world and this world is shared with every other test.
            Research.state(server).forget(logistics);
            Research.changedExternally(server);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("the crafting gate is installed");
        }
    }

    /**
     * The pack's own gating, and the guard that keeps it from locking a world out of research.
     *
     * <h2>Both halves are the point</h2>
     *
 * <p><b>The electric mining drill is the one that was reported</b> - craftable on a fresh world,
     * and it should not have been. It has a technology of its own now.
     *
     * <p>The other half is the guard, and it is the half worth keeping. Gating is one edit away
     * from a world that can never research anything: what is left free has to be enough to mine by
     * hand, smelt, and reach the first trigger. The generator walks that graph and refuses a tree
     * it cannot bootstrap; <b>this asserts the same thing at run time</b>, against the recipes
     * actually loaded, because the generator's answer and the server's could drift and only one of
     * them is the one a player meets.
     */
    public static class ResearchGatesTheEarlyMachinesTest extends GameTestInstance {

        public static final MapCodec<ResearchGatesTheEarlyMachinesTest> CODEC =
                RecordCodecBuilder.<ResearchGatesTheEarlyMachinesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ResearchGatesTheEarlyMachinesTest::info))
                                .apply(i, ResearchGatesTheEarlyMachinesTest::new));

        public ResearchGatesTheEarlyMachinesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> drillTech = ModTechnologies.key("electric_mining_drill");
            ResourceKey<Recipe<?>> drill = recipe("nauvis_mining", "electric_mining_drill");

            Research.state(server).forget(drillTech);
            Research.changedExternally(server);

            helper.assertFalse(Research.isUnlocked(server, drill),
                    "the electric drill is craftable with nothing researched");

            // What a new world can still do, and it has to be enough to start: mine by hand, put
            // ore in a furnace, and make the handful of things nothing gates. If one of these were
            // ever gated the world could never reach its first trigger - and every other test here
            // would still pass, because they all begin with a lab already placed.
            for (String[] free : new String[][] {
                    {"nauvis_logistics", "transport_belt"}, {"nauvis_logistics", "burner_inserter"},
                    {"nauvis_logistics", "chest"}, {"nauvis_machines", "stone_furnace"},
                    {"neoprogressivematerials", "iron_gear_wheel"},
                    {"neoprogressivematerials", "iron_ingot"}}) {
                helper.assertTrue(Research.isUnlocked(server, recipe(free[0], free[1])),
                        free[0] + ":" + free[1] + " is gated, but nothing in the tree unlocks it, "
                                + "so a new world could never craft it at all");
            }

            Research.state(server).complete(drillTech);
            Research.changedExternally(server);
            helper.assertTrue(Research.isUnlocked(server, drill),
                    "the electric drill is still locked after researching it");

            Research.state(server).forget(drillTech);
            Research.changedExternally(server);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("research gates the early machines");
        }
    }

    /**
     * Making something finishes the technology that was waiting for it.
     *
     * <h2>This is what makes the opening possible at all</h2>
     *
     * <p>The first technologies have no cost and no science: <em>craft fifty iron plates</em> and
     * the boiler and the steam engine are yours. Without that a new world would have to build a
     * lab to research the things a lab is built out of, which is a circle - and the pack would be
     * back to handing everything over at the start, which is the complaint this whole tree exists
     * to answer.
     *
     * <p>It is also the one part of research that is <b>not</b> driven by a machine, so nothing
     * else here would catch it breaking. A lab test cannot: a triggered technology never reaches
     * a lab.
     *
     * <p>Steam power is used deliberately - it has no prerequisites, so this test needs no setup
     * beyond forgetting it, and nothing else in the run sets it as current research.
     */
    public static class TriggerFinishesResearchTest extends GameTestInstance {

        public static final MapCodec<TriggerFinishesResearchTest> CODEC =
                RecordCodecBuilder.<TriggerFinishesResearchTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TriggerFinishesResearchTest::info))
                                .apply(i, TriggerFinishesResearchTest::new));

        public TriggerFinishesResearchTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> steam = ModTechnologies.key("steam_power");
            ResourceKey<Recipe<?>> boiler = recipe("nauvis_power", "boiler");
            Technology.Trigger trigger = technology(helper, "steam_power").trigger().orElseThrow();

            ResearchState state = Research.state(server);
            state.forget(steam);
            state.recordMade(trigger.target(), -state.made(trigger.target()));
            Research.changedExternally(server);

            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler is craftable before steam power");

            // One short. The count is the whole of the condition, so being able to stop one below
            // it is what says the number is read rather than ignored.
            Research.recordMade(helper.getLevel(), trigger.target(), trigger.count() - 1);
            helper.assertFalse(Research.state(server).isCompleted(steam),
                    "steam power finished one item short of its trigger");
            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler unlocked one item short of steam power's trigger");

            Research.recordMade(helper.getLevel(), trigger.target(), 1);
            helper.assertTrue(Research.state(server).isCompleted(steam),
                    "steam power did not finish when its trigger was met - a new world would have "
                            + "nothing it could ever research");
            helper.assertTrue(Research.isUnlocked(server, boiler),
                    "the boiler is still locked after steam power finished");

            // A lab cannot be pointed at one of these: it finishes by being made, not by science.
            helper.assertFalse(Research.setCurrent(server, steam),
                    "a triggered technology was accepted as a lab's current research");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a trigger finishes research");
        }
    }

    /**
     * A craft finished in Facrafting's panel reaches the trigger with the item still in it.
     *
     * <h2>The bug this exists for looked exactly like nothing</h2>
     *
     * <p>{@code CraftTicker} used to hand the listeners the same {@code ItemStack} it had just
     * passed to {@code placeItemBackInInventory} - which empties it, slot by slot, as it puts it
     * away. So every listener was told an empty stack, {@code ResearchTriggers} dropped it on its
     * first line, and <b>"craft one lab" sat at zero done for ever</b> while the lab itself
     * appeared in the inventory. Nothing logged, nothing failed, and the panel is where this pack
     * does nearly all of its crafting.
     *
     * <p>{@link CraftingGateIsInstalledTest} could not catch it: the listener really was
     * installed. What was wrong was what it got handed. So this drives the delivery itself and
     * asserts the world's counter moved - the smallest thing that is true only if the whole seam
     * works.
     *
     * <p><b>Iron plates rather than a lab</b>, deliberately, and it is the same hazard every test
     * here works around. Research is per-world and shared with every other test in the run: one
     * lab would finish Science pack 1 and clear whatever another test had set as its current
     * research. Steam power's trigger is fifty plates deep, so two of them move a counter and
     * finish nothing - and the count is put back afterwards either way.
     *
     * <p>It is also the only trigger item this mod can reach on its own. The other three belong to
     * sibling mods, and {@code :nauvis_research:runGameTestServer} runs this mod alone.
     */
    public static class PanelCraftCountsTest extends GameTestInstance {

        public static final MapCodec<PanelCraftCountsTest> CODEC =
                RecordCodecBuilder.<PanelCraftCountsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PanelCraftCountsTest::info))
                                .apply(i, PanelCraftCountsTest::new));

        public PanelCraftCountsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            Technology.Trigger trigger = technology(helper, "steam_power").trigger().orElseThrow();
            Item plate = BuiltInRegistries.ITEM.getValue(trigger.target());
            helper.assertTrue(plate != Items.AIR,
                    "no item is registered as " + trigger.target() + ", so the trigger watches for "
                            + "something nobody can make");

            ResearchState state = Research.state(server);
            int before = state.made(trigger.target());

            // Not a mock *server* player, which is the one a test would reach for first: putting
            // a stack away sends a slot packet, and a mock server player has no connection to
            // send it down. A plain mock in a server level skips the packet and is still on the
            // server, which is all a trigger asks about.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            try {
                CraftTicker.deliver(player, new ItemStack(plate, 2));

                int held = 0;
                for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                    if (stack.is(plate)) {
                        held += stack.getCount();
                    }
                }
                helper.assertTrue(held == 2,
                        "a finished craft put " + held + " items in the player's inventory, not 2");
                helper.assertTrue(Research.state(server).made(trigger.target()) == before + 2,
                        "a craft finished in the panel counted "
                                + (state.made(trigger.target()) - before) + " towards its trigger "
                                + "instead of 2 - a triggered technology can never finish, and "
                                + "nothing says so");
            } finally {
                // Put it back: research is per-world and this world is shared with every other
                // test in the run.
                state.recordMade(trigger.target(), before - state.made(trigger.target()));
                Research.changedExternally(server);
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a panel craft counts towards a trigger");
        }
    }

    /**
     * The tech tree's geometry, checked without anything drawing it.
     *
     * <h2>This is the half of a tech tree that can be tested</h2>
     *
     * <p>Nothing in this repository can look at a screen, and pretending otherwise has cost this
     * pack three bugs. So {@code TechnologyLayout} computes positions and edges as ordinary
     * server-reachable code and the screen only paints them - which means the parts that are
     * <em>true or false</em> rather than <em>nice or ugly</em> are asserted here, and a playtest
     * is left to judge the things only a person can.
     *
     * <p>It checks <b>every technology's own view</b>, not one: the picture is drawn around
     * whatever is selected, so a layout that only holds for the technology somebody happened to
     * open is not a layout.
     *
     * <ul>
     *   <li><b>the selection is in its own picture</b>, and everything it needs is there with it,
     *       to the left of it. That is the whole claim the screen makes;</li>
     *   <li><b>no two nodes share a cell</b>, or one is drawn on top of another and simply cannot
     *       be clicked;</li>
     *   <li><b>every arrow points right and is a real prerequisite</b>, and every prerequisite
     *       between two technologies in the picture is drawn or implied by others. An arrow the
     *       data does not support is the screen making something up; one silently dropped is a
     *       cost a player cannot see;</li>
     *   <li><b>{@code outside} counts what is missing.</b> A descendant that needs three other
     *       technologies must say so, or the picture promises something it cannot give;</li>
     *   <li><b>two arrows share a lane only when their runs cannot touch.</b> A lane is where an
     *       elbow turns, and two that overlap in one are drawn as a single line - which is what
     *       this tree looked like before lanes existed;</li>
     *   <li><b>hiding the researched ancestors hides exactly those</b> and keeps the selection;</li>
     *   <li><b>the same view lays out the same way twice.</b> A layout that shuffled between
     *       openings would be unusable however good it looked, and nothing about a single frame
     *       would reveal it.</li>
     * </ul>
     */
    public static class TechnologyLayoutTest extends GameTestInstance {

        public static final MapCodec<TechnologyLayoutTest> CODEC =
                RecordCodecBuilder.<TechnologyLayoutTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TechnologyLayoutTest::info))
                                .apply(i, TechnologyLayoutTest::new));

        public TechnologyLayoutTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            var access = helper.getLevel().registryAccess();
            List<Holder.Reference<Technology>> all = ModTechnologies.all(access);
            helper.assertTrue(!all.isEmpty(), "no technologies to lay out");

            java.util.Set<ResourceKey<Technology>> everything = new java.util.HashSet<>();
            for (Holder.Reference<Technology> holder : all) {
                everything.add(holder.key());
            }

            for (Holder.Reference<Technology> holder : all) {
                check(helper, all, holder.key());

                // The same view with every ancestor researched and hidden. What is left is the
                // work outstanding, which is the point of the toggle, so the selection has to
                // survive it and no ancestor may.
                TechnologyLayout.Layout thinned =
                        TechnologyLayout.around(all, holder.key(), everything, true);
                helper.assertTrue(thinned.at(holder.key()) != null,
                        "hiding the researched ancestors of " + holder.key().identifier()
                                + " took the selected technology with them");
                for (TechnologyLayout.Placed placed : thinned.nodes()) {
                    helper.assertTrue(placed.kind() != TechnologyLayout.Kind.ANCESTOR,
                            placed.key().identifier() + " is a researched ancestor that was asked "
                                    + "to be hidden and is drawn anyway");
                }
            }
            helper.succeed();
        }

        /** Everything one view has to be true about. */
        private static void check(GameTestHelper helper, List<Holder.Reference<Technology>> all,
                ResourceKey<Technology> selected) {

            TechnologyLayout.Layout layout =
                    TechnologyLayout.around(all, selected, Set.of(), false);
            java.util.Map<ResourceKey<Technology>, Technology> byKey = new java.util.HashMap<>();
            for (Holder.Reference<Technology> holder : all) {
                byKey.put(holder.key(), holder.value());
            }

            TechnologyLayout.Placed centre = layout.at(selected);
            helper.assertTrue(centre != null,
                    selected.identifier() + " is not in its own view");
            helper.assertTrue(centre.kind() == TechnologyLayout.Kind.SELECTED,
                    selected.identifier() + " is not the selection of its own view");

            java.util.Set<Long> cells = new java.util.HashSet<>();
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                helper.assertTrue(cells.add((long) placed.column() << 32 | placed.row()),
                        placed.key().identifier() + " shares a cell with something else in the "
                                + "view of " + selected.identifier() + ", so one of the two is "
                                + "drawn underneath and cannot be clicked");
                helper.assertTrue(placed.column() >= 0 && placed.row() >= 0,
                        placed.key().identifier() + " is placed off the grid");
            }

            // Everything the selection needs, however far back, is in front of the player.
            for (ResourceKey<Technology> ancestor : ancestors(byKey, selected)) {
                TechnologyLayout.Placed placed = layout.at(ancestor);
                helper.assertTrue(placed != null,
                        selected.identifier() + " needs " + ancestor.identifier()
                                + ", which its view does not draw at all");
                helper.assertTrue(placed.kind() == TechnologyLayout.Kind.ANCESTOR,
                        ancestor.identifier() + " leads to " + selected.identifier()
                                + " and is not drawn as one of its ancestors");
                helper.assertTrue(placed.column() < centre.column(),
                        ancestor.identifier() + " is not left of " + selected.identifier()
                                + " - the picture is lying about what comes first");
            }

            for (TechnologyLayout.Placed placed : layout.nodes()) {
                if (placed.kind() == TechnologyLayout.Kind.DESCENDANT) {
                    helper.assertTrue(placed.column() > centre.column(),
                            placed.key().identifier() + " comes after " + selected.identifier()
                                    + " and is not drawn to the right of it");
                    helper.assertTrue(
                            placed.column() - centre.column() <= TechnologyLayout.DESCENDANT_DEPTH,
                            placed.key().identifier() + " is further past " + selected.identifier()
                                    + " than the view is supposed to reach");
                }

                // What is not in the picture, said on the node that wants it.
                int missing = 0;
                for (ResourceKey<Technology> prerequisite
                        : placed.technology().prerequisites()) {
                    if (byKey.containsKey(prerequisite) && layout.at(prerequisite) == null) {
                        missing++;
                    }
                }
                helper.assertValueEqual(placed.outside(), missing,
                        "prerequisites of " + placed.key().identifier()
                                + " outside the view of " + selected.identifier());
            }

            for (TechnologyLayout.Edge edge : layout.edges()) {
                TechnologyLayout.Placed from = layout.at(edge.from());
                TechnologyLayout.Placed to = layout.at(edge.to());
                helper.assertTrue(from != null && to != null,
                        "an arrow points at a technology that was never placed");
                helper.assertTrue(from.column() < to.column(),
                        edge.to().identifier() + " is not right of " + edge.from().identifier()
                                + " - the arrow points backwards");
                helper.assertTrue(to.technology().prerequisites().contains(edge.from()),
                        edge.to().identifier() + " is drawn as needing " + edge.from().identifier()
                                + ", which is not a prerequisite of it");
            }

            // Nothing goes missing: a prerequisite joining two drawn technologies is drawn, or
            // another pair of arrows already says it.
            java.util.Set<String> drawn = new java.util.HashSet<>();
            for (TechnologyLayout.Edge edge : layout.edges()) {
                drawn.add(edge.from() + " -> " + edge.to());
            }
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                for (ResourceKey<Technology> prerequisite : placed.technology().prerequisites()) {
                    if (layout.at(prerequisite) == null
                            || drawn.contains(prerequisite + " -> " + placed.key())) {
                        continue;
                    }
                    helper.assertTrue(implied(layout, placed.key(), prerequisite),
                            placed.key().identifier() + " needs " + prerequisite.identifier()
                                    + " and, in the view of " + selected.identifier()
                                    + ", that arrow is neither drawn nor implied by others");
                }
            }

            lanes(helper, layout, selected);

            helper.assertValueEqual(
                    TechnologyLayout.around(all, selected, Set.of(), false).nodes(),
                    layout.nodes(),
                    "the view of " + selected.identifier() + " is not deterministic - it would "
                            + "shuffle between openings");
        }

        /** Two arrows may share a channel only where their vertical runs cannot overlap. */
        private static void lanes(GameTestHelper helper, TechnologyLayout.Layout layout,
                ResourceKey<Technology> selected) {

            java.util.Map<ResourceKey<Technology>, int[]> spans = new java.util.HashMap<>();
            for (TechnologyLayout.Edge edge : layout.edges()) {
                helper.assertTrue(edge.lane() >= 0 && edge.lane() < edge.lanes(),
                        edge.from().identifier() + " turns in lane " + edge.lane() + " of "
                                + edge.lanes() + ", which is not a lane that exists");
                TechnologyLayout.Placed from = layout.at(edge.from());
                TechnologyLayout.Placed to = layout.at(edge.to());
                int[] span = spans.computeIfAbsent(edge.from(),
                        ignored -> new int[] {from.row(), from.row(), from.column(), edge.lane()});
                span[0] = Math.min(span[0], to.row());
                span[1] = Math.max(span[1], to.row());
                helper.assertValueEqual(span[3], edge.lane(),
                        "one lane for every arrow out of " + edge.from().identifier());
            }
            for (java.util.Map.Entry<ResourceKey<Technology>, int[]> one : spans.entrySet()) {
                for (java.util.Map.Entry<ResourceKey<Technology>, int[]> two : spans.entrySet()) {
                    int[] a = one.getValue();
                    int[] b = two.getValue();
                    if (one.getKey().equals(two.getKey()) || a[2] != b[2] || a[3] != b[3]) {
                        continue;
                    }
                    helper.assertTrue(a[1] < b[0] || b[1] < a[0],
                            one.getKey().identifier() + " and " + two.getKey().identifier()
                                    + " share lane " + a[3] + " in the view of "
                                    + selected.identifier() + " and overlap - the two would be "
                                    + "drawn as one line");
                }
            }
        }

        /** Everything a technology needs, transitively. */
        private static Set<ResourceKey<Technology>> ancestors(
                java.util.Map<ResourceKey<Technology>, Technology> byKey,
                ResourceKey<Technology> key) {

            java.util.Set<ResourceKey<Technology>> found = new java.util.HashSet<>();
            java.util.Deque<ResourceKey<Technology>> frontier =
                    new java.util.ArrayDeque<>(List.of(key));
            while (!frontier.isEmpty()) {
                Technology technology = byKey.get(frontier.removeFirst());
                if (technology == null) {
                    continue;
                }
                for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
                    if (byKey.containsKey(prerequisite) && !prerequisite.equals(key)
                            && found.add(prerequisite)) {
                        frontier.add(prerequisite);
                    }
                }
            }
            return found;
        }

        /** Whether {@code prerequisite} is reached from {@code key} through drawn arrows. */
        private static boolean implied(TechnologyLayout.Layout layout,
                ResourceKey<Technology> key, ResourceKey<Technology> prerequisite) {
            java.util.Set<ResourceKey<Technology>> seen = new java.util.HashSet<>(Set.of(key));
            java.util.Deque<ResourceKey<Technology>> frontier =
                    new java.util.ArrayDeque<>(List.of(key));
            while (!frontier.isEmpty()) {
                ResourceKey<Technology> at = frontier.removeFirst();
                for (TechnologyLayout.Edge edge : layout.edges()) {
                    if (edge.to().equals(at) && seen.add(edge.from())) {
                        if (edge.from().equals(prerequisite)) {
                            return true;
                        }
                        frontier.add(edge.from());
                    }
                }
            }
            return false;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("the technology layout is sound");
        }
    }

    /**
     * Research put down and picked up again is where it was left.
     *
     * <p>This is the bug a playtest found, and it was a decision rather than an accident: units
     * were one counter beside the current technology and switching zeroed it, which was written
     * down as Factorio's rule and is not - Factorio keeps a per-technology progress and hands it
     * back when you return. A player who clicked something else to read its tooltip could throw
     * away an hour of labs, and nothing said so before or after.
     *
     * <p>Alone in its batch, because there is one current research per world and every lab test in
     * the other batch is pointing it at something of its own.
     */
    public static class ResearchKeepsItsProgressTest extends GameTestInstance {

        public static final MapCodec<ResearchKeepsItsProgressTest> CODEC =
                RecordCodecBuilder.<ResearchKeepsItsProgressTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ResearchKeepsItsProgressTest::info))
                                .apply(i, ResearchKeepsItsProgressTest::new));

        public ResearchKeepsItsProgressTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> automation = ModTechnologies.key("automation");
            ResourceKey<Technology> logistics = ModTechnologies.key("logistics");

            // Both cost ten units of red science and neither is anywhere near that after two, so
            // nothing here completes and the numbers stay comparable.
            research(helper, "automation");
            Research.addUnit(helper.getLevel());
            Research.addUnit(helper.getLevel());
            helper.assertValueEqual(state.units(), 2, "units on the research being worked on");

            research(helper, "logistics");
            helper.assertValueEqual(state.units(), 0, "units on a research just started");
            helper.assertValueEqual(state.units(automation), 2,
                    "units kept on the research the labs were pointed away from");

            Research.addUnit(helper.getLevel());
            helper.assertValueEqual(state.units(logistics), 1, "units on the second research");
            helper.assertValueEqual(state.units(automation), 2,
                    "units on the first research, while a second one is being worked on");

            Research.setCurrent(server, automation);
            helper.assertValueEqual(state.units(), 2, "units picked up where they were left");

            // And finishing one takes it out of the tally rather than leaving it there to be
            // handed back if the technology is ever forgotten and researched again.
            for (int i = state.units(); i < technology(helper, "automation").units(); i++) {
                Research.addUnit(helper.getLevel());
            }
            helper.assertTrue(state.isCompleted(automation), "automation finished");
            helper.assertValueEqual(state.units(automation), 0,
                    "units left over on a finished technology");

            state.forget(automation);
            state.forget(logistics);
            state.setCurrent(null);
            Research.changedExternally(server);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("research keeps its progress across a switch");
        }
    }

    /**
     * Factorio's other trigger: oil processing finishes when crude oil has been mined once.
     *
     * <p>The mechanism, without a machine: the tree has {@code oil_processing} as a mine trigger on
     * {@code nauvis_fluids:crude_oil}, one report finishes it, and the refinery unlocks. And the
     * two tallies are separate - <em>crafting</em> something with that id must not count, because
     * the id is also the creative item that places a well.
     *
     * <p>Completes the whole chain up to oil gathering first, and puts the tree back exactly
     * afterwards; see {@code ResearchCommandTest} for why a snapshot and not a clear.
     */
    public static class MineTriggerFinishesResearchTest extends GameTestInstance {

        public static final MapCodec<MineTriggerFinishesResearchTest> CODEC =
                RecordCodecBuilder.<MineTriggerFinishesResearchTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(MineTriggerFinishesResearchTest::info))
                                .apply(i, MineTriggerFinishesResearchTest::new));

        public MineTriggerFinishesResearchTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> oilProcessing = ModTechnologies.key("oil_processing");
            Technology technology = technology(helper, "oil_processing");
            helper.assertTrue(technology != null, "oil processing is not in the tree");
            helper.assertTrue(technology.isTriggered(), "oil processing has a cost; Factorio 2.0 triggers it");

            Technology.Trigger trigger = technology.trigger().orElseThrow();
            helper.assertValueEqual(trigger.kind(), Technology.Trigger.Kind.MINE, "oil processing's trigger kind");
            helper.assertValueEqual(trigger.target(), Identifier.fromNamespaceAndPath("nauvis_fluids", "crude_oil"),
                    "what oil processing waits for");
            helper.assertValueEqual(trigger.count(), 1, "how many times - Factorio's mine-entity: crude-oil, 1");

            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            finish(server, state, oilProcessing, true);
            state.recordMined(trigger.target(), -state.mined(trigger.target()));
            Research.changedExternally(server);

            ResourceKey<Recipe<?>> refinery = recipe("nauvis_fluids", "oil_refinery");
            helper.assertFalse(Research.isUnlocked(server, refinery), "the refinery is craftable before oil processing");

            // Crafting the thing with the well's id is not mining the well.
            Research.recordMade(helper.getLevel(), trigger.target(), 1);
            helper.assertFalse(state.isCompleted(oilProcessing),
                    "oil processing finished on a craft of nauvis_fluids:crude_oil - the two tallies have merged");

            Research.recordMined(helper.getLevel(), trigger.target(), 1);
            helper.assertTrue(state.isCompleted(oilProcessing),
                    "oil processing did not finish when crude oil was mined once");
            helper.assertTrue(Research.isUnlocked(server, refinery), "the refinery is still locked after oil processing");

            for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                if (!before.contains(key)) {
                    state.forget(key);
                }
            }
            before.forEach(state::complete);
            state.recordMade(trigger.target(), -state.made(trigger.target()));
            state.recordMined(trigger.target(), -state.mined(trigger.target()));
            Research.changedExternally(server);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a mine trigger finishes research");
        }
    }

    /**
     * The whole chain, end to end: a real pumpjack on a real well, powered, finishes oil processing.
     *
     * <p>Three mods meet here and none names another: the pumpjack reports its cycle to
     * Facrafting's {@code MiningListeners}, this mod hears it, and the technology completes. The
     * well and the machine are found by id, so this mod compiles against neither - and when
     * {@code nauvis_fluids} is not installed, as in this mod's own standalone run, there is nothing
     * to test and the test says so by passing.
     *
     * <p>The pumpjack is placed the way a player places one, through its item, so the placement rule
     * that centres it on the well is exercised too.
     */
    public static class PumpingOilFinishesOilProcessingTest extends GameTestInstance {

        public static final MapCodec<PumpingOilFinishesOilProcessingTest> CODEC =
                RecordCodecBuilder.<PumpingOilFinishesOilProcessingTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpingOilFinishesOilProcessingTest::info))
                                .apply(i, PumpingOilFinishesOilProcessingTest::new));

        private static final BlockPos WELL = new BlockPos(2, 1, 2);

        public PumpingOilFinishesOilProcessingTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Block well = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "crude_oil"));
            Block pumpjack = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "pumpjack"));
            if (well == Blocks.AIR || pumpjack == Blocks.AIR) {
                // Standalone: no oil here to pump. The mechanism is covered by the test above.
                helper.succeed();
                return;
            }

            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> oilProcessing = ModTechnologies.key("oil_processing");
            Identifier target = technology(helper, "oil_processing").trigger().orElseThrow().target();
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            finish(server, state, oilProcessing, true);
            state.recordMined(target, -state.mined(target));
            Research.changedExternally(server);

            helper.setBlock(WELL, well);

            // Placed as a player would: click the top of the well with the item in hand.
            BlockPos clicked = helper.absolutePos(WELL);
            ItemStack stack = new ItemStack(pumpjack.asItem());
            ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
            BlockPos anchor = helper.absolutePos(WELL.above());
            helper.assertTrue(helper.getLevel().getBlockState(anchor).is(pumpjack),
                    "a pumpjack placed by clicking a well did not land on it");

            // Powered the way a pole powers it: through the energy capability on any of its blocks.
            EnergyHandler power = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, anchor, null);
            helper.assertTrue(power != null, "a pumpjack offers no energy capability");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(power.insert(1200, transaction) > 0, "a pumpjack took no electricity");
                transaction.commit();
            }

            helper.assertTrue(MiningListeners.installed() > 0,
                    "nothing is listening to Facrafting's MiningListeners, so no machine can finish a mine trigger");

            // One cycle is a second. Forty ticks is two, with room for the tick it wakes on.
            helper.runAfterDelay(40, () -> {
                var pumpjackEntity = helper.getLevel().getBlockEntity(anchor);
                helper.assertTrue(state.isCompleted(oilProcessing),
                        "a pumpjack ran on a well for two seconds and oil processing did not finish - "
                                + "the report is not reaching research. Mined tally: " + state.mined(target)
                                + "; prerequisites outstanding: " + technology(helper, "oil_processing").prerequisites()
                                        .stream().filter(key -> !state.isCompleted(key)).toList()
                                + "; the machine: " + (pumpjackEntity == null ? "no block entity"
                                        : pumpjackEntity.saveWithoutMetadata(helper.getLevel().registryAccess())));
                // At least the cycle that finished it. A second one ends on tick 41, and on a well as
                // rich as the gametest world's - millions of blocks from the origin - it would find
                // the tank still full of the first.
                helper.assertTrue(state.mined(target) >= 1, "the pumpjack reported no cycle at all");
                for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                    if (!before.contains(key)) {
                        state.forget(key);
                    }
                }
                before.forEach(state::complete);
                state.recordMined(target, -state.mined(target));
                Research.changedExternally(server);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumping oil finishes oil processing");
        }
    }

    /**
     * A lab spends chemical science on a technology that asks for it, one of each pack a unit.
     *
     * <p>The lab code did not change for blue science, and that is the claim: a lab takes whatever
     * packs the world's research names, so the third pack is an item and a recipe and nothing
     * else. What is asserted is the rule that makes it a third <em>gate</em> rather than a third
     * flavour - red and green in the slots and no blue is not two thirds of a unit, it is no unit
     * at all, which is what stops a science farm from running on whatever it has most of.
     *
     * <p>Electric engine, at Factorio 2.0's fifty units of thirty seconds: the unit is three times
     * as long as the lab's buffer, so the test tops the lab up every tick the way a pole would.
     */
    public static class LabSpendsBlueScienceTest extends GameTestInstance {

        public static final MapCodec<LabSpendsBlueScienceTest> CODEC =
                RecordCodecBuilder.<LabSpendsBlueScienceTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabSpendsBlueScienceTest::info))
                                .apply(i, LabSpendsBlueScienceTest::new));

        public LabSpendsBlueScienceTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> engine = ModTechnologies.key("electric_engine");
            Technology technology = technology(helper, "electric_engine");
            helper.assertTrue(technology != null, "electric engine is not in the tree");

            Identifier blue = Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "science_pack_3");
            helper.assertValueEqual(technology.packs().size(), 3, "kinds of pack electric engine wants");
            helper.assertTrue(technology.packs().contains(blue), "electric engine does not cost blue science");
            helper.assertTrue(technology.isResearchable(),
                    "electric engine cannot be researched - a pack it wants is not a registered item");
            helper.assertValueEqual(technology.ticksPerUnit(), 600, "ticks a unit of electric engine");

            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            ResourceKey<Technology> current = state.current();
            research(helper, "electric_engine");
            helper.assertValueEqual(state.current(), engine, "the world's current research");

            LabBlockEntity lab = placeLab(helper);
            helper.onEachTick(() -> charge(lab));
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 2);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_2.get(), 2);

            // Two of the three: not a unit. The lab is fed and powered and must sit still.
            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(lab.progress(), 0,
                        "progress on a lab holding red and green but no blue science");
                helper.assertValueEqual(lab.cycles(), 0, "units done without blue science");

                insert(lab.automationView(), ModItems.SCIENCE_PACK_3.get(), 2);

                // A fed lab with nothing to research looks again once a second, and the pack
                // arriving cannot bring that look forward - a scheduled tick is kept, not moved -
                // so the unit starts up to a recheck late. The window allows for the whole of one.
                int unitTicks = technology.ticksPerUnit() + LabBlockEntity.IDLE_RECHECK_TICKS;

                helper.runAfterDelay(unitTicks + 3, () -> {
                    try {
                        helper.assertValueEqual(lab.cycles(), 1, "units after one unit's worth of ticks");
                        for (Item pack : List.of(ModItems.SCIENCE_PACK_1.get(),
                                ModItems.SCIENCE_PACK_2.get(), ModItems.SCIENCE_PACK_3.get())) {
                            helper.assertValueEqual(count(lab, pack), 1,
                                    pack + " left after one unit - a unit is one of each");
                        }
                        helper.assertValueEqual(state.units(engine), 1,
                                "units the world has towards electric engine");
                    } finally {
                        // Put the tree back for whatever batch runs next.
                        for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                            if (!before.contains(key)) {
                                state.forget(key);
                            }
                        }
                        before.forEach(state::complete);
                        state.forget(engine);
                        state.setCurrent(current);
                        Research.changedExternally(server);
                    }
                    helper.succeed();
                });
            });
        }

        private static int count(LabBlockEntity lab, Item item) {
            int total = 0;
            for (int slot = 0; slot < lab.inventory().size(); slot++) {
                if (lab.inventory().getAmountAsInt(slot) > 0 && lab.inventory().getResource(slot).is(item)) {
                    total += lab.inventory().getAmountAsInt(slot);
                }
            }
            return total;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab spends blue science");
        }
    }

    /**
     * A technology's modifiers are summed for the world, and reach a machine through the library.
     *
     * <p>Two research-speed technologies at a fifth and three tenths are half again as fast, and a
     * lab's unit is shorter by exactly that. The inserter capacity line is the other one anything
     * reads: the stack inserter's technology and its first two bonus levels make three, and the
     * ordinary inserter's single bonus arrives with the second level and not before.
     *
     * <p>Asked through {@code Bonuses} as well as {@code Research}, because the wiring between the
     * two is a line in the mod constructor that nothing else here would notice going missing - and
     * with it gone every inserter in the pack would hold one item for ever, saying nothing.
     *
     * <p>Alone in its batch: it completes chains that run back through automation, which is the
     * technology the lab tests point their labs at. Puts the tree back exactly afterwards.
     */
    public static class BonusesReachTheWorldTest extends GameTestInstance {

        public static final MapCodec<BonusesReachTheWorldTest> CODEC =
                RecordCodecBuilder.<BonusesReachTheWorldTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BonusesReachTheWorldTest::info))
                                .apply(i, BonusesReachTheWorldTest::new));

        public BonusesReachTheWorldTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            ResourceKey<Technology> current = state.current();
            try {
                helper.assertTrue(Bonuses.installed(),
                        "nothing has installed itself as the world's bonuses - see NauvisResearch");

                state.forget(ModTechnologies.key("research_speed_1"));
                state.forget(ModTechnologies.key("research_speed_2"));
                state.forget(ModTechnologies.key("inserter_capacity_bonus_2"));
                Research.changedExternally(server);
                helper.assertTrue(Research.bonus(server, LabBlockEntity.LABORATORY_SPEED) == 0,
                        "research speed with neither speed technology researched");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 0,
                        "an ordinary inserter's bonus with nothing researched");

                finish(server, state, ModTechnologies.key("research_speed_2"), false);
                Research.changedExternally(server);
                double speed = Research.bonus(server, LabBlockEntity.LABORATORY_SPEED);
                helper.assertTrue(Math.abs(speed - 0.5) < 1e-9,
                        "research speed after both speed technologies: " + speed + ", not 0.5");
                helper.assertTrue(Math.abs(Bonuses.of(helper.getLevel(), LabBlockEntity.LABORATORY_SPEED) - 0.5) < 1e-9,
                        "the library answers differently from research - the hook is not wired");
                Technology engine = technology(helper, "electric_engine");
                helper.assertValueEqual(LabBlockEntity.cycleTicksFor(engine, helper.getLevel()), 400,
                        "ticks a 600-tick unit takes at half again the research speed");

                // Stack inserter, capacity 1, capacity 2: one from each for the stack inserter's
                // hand, and the ordinary inserter's single bonus from the second level only.
                finish(server, state, ModTechnologies.key("inserter_capacity_bonus_1"), false);
                Research.changedExternally(server);
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "bulk-inserter-capacity-bonus"), 2,
                        "stack inserter bonus after its technology and the first capacity level");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 0,
                        "an ordinary inserter's bonus before the second capacity level");

                finish(server, state, ModTechnologies.key("inserter_capacity_bonus_2"), false);
                Research.changedExternally(server);
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "bulk-inserter-capacity-bonus"), 3,
                        "stack inserter bonus after the second capacity level");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 1,
                        "an ordinary inserter's bonus after the second capacity level");
            } finally {
                for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                    if (!before.contains(key)) {
                        state.forget(key);
                    }
                }
                before.forEach(state::complete);
                state.setCurrent(current);
                Research.changedExternally(server);
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("bonuses reach the world");
        }
    }

    /**
     * A lab has two module slots, and speed modules in them shorten a unit.
     *
     * <p>The modules are the machines mod's items and this mod does not name it, so the test finds
     * them by id and passes when they are not there - which is the standalone run. With them, two
     * speed modules make a lab two fifths faster: a 600-tick unit is 429 ticks, on top of whatever
     * research speed the world has, and the lab draws half again as much twice over.
     */
    public static class LabTakesModulesTest extends GameTestInstance {

        public static final MapCodec<LabTakesModulesTest> CODEC =
                RecordCodecBuilder.<LabTakesModulesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LabTakesModulesTest::info))
                                .apply(i, LabTakesModulesTest::new));

        public LabTakesModulesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Item speed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_machines", "speed_module"));
            LabBlockEntity lab = placeLab(helper);
            helper.assertValueEqual(lab.modules().size(), LabBlockEntity.MODULE_SLOTS, "module slots on a lab");
            if (speed == Items.AIR) {
                // Standalone: no modules to put in. The slots exist, which is what this mod owns.
                helper.succeed();
                return;
            }

            helper.assertFalse(lab.modules().isValid(0, ItemResource.of(Items.REDSTONE)),
                    "a lab's module slot took something that is not a module");
            for (int slot = 0; slot < LabBlockEntity.MODULE_SLOTS; slot++) {
                try (Transaction transaction = Transaction.openRoot()) {
                    helper.assertValueEqual(lab.modules().insert(slot, ItemResource.of(speed), 2, transaction), 1,
                            "speed modules taken by one slot - a slot holds one module");
                    transaction.commit();
                }
            }

            Technology engine = technology(helper, "electric_engine");
            int bare = LabBlockEntity.cycleTicksFor(engine, helper.getLevel());
            int modded = LabBlockEntity.cycleTicksFor(engine, helper.getLevel(), lab.modules().effect());
            helper.assertValueEqual(modded, (int) Math.round(bare / 1.4),
                    "ticks a unit takes with two speed modules, against " + bare + " without");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a lab takes modules");
        }
    }
}
