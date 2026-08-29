package com.jaguarm.nauvisresearch;

import java.util.List;

import com.jaguarm.nauvisresearch.lab.LabBlock;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvisresearch.multiblock.Multiblock;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.ResearchState;
import com.jaguarm.nauvisresearch.research.Technology;
import com.jaguarm.nauvisresearch.research.TechnologyLayout;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
        TEST_TYPES.register("a_trigger_finishes_research", () -> TriggerFinishesResearchTest.CODEC);
        TEST_TYPES.register("technology_layout_is_sound", () -> TechnologyLayoutTest.CODEC);
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
        register(event, environment, "technology_layout_is_sound", TechnologyLayoutTest::new, 20);
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
     * from {@code LabPower}, on whatever thread of control filled the buffer. Delete that callback
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
                            "power arriving did not wake the lab - see LabPower");
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
            helper.assertValueEqual(steam.trigger().orElseThrow().item(),
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
            Research.changedForTest(server);

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
            Research.changedForTest(server);

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
            Research.changedForTest(server);
            helper.assertTrue(RecipeLocks.isUnlocked(player, splitter),
                    "the splitter is still locked after researching Logistics");

            // Put it back: research is per-world and this world is shared with every other test.
            Research.state(server).forget(logistics);
            Research.changedForTest(server);
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
            ResourceKey<Recipe<?>> drill = recipe("neoprogressiveautomation", "electric_drill");

            Research.state(server).forget(drillTech);
            Research.changedForTest(server);

            helper.assertFalse(Research.isUnlocked(server, drill),
                    "the electric drill is craftable with nothing researched");

            // What a new world can still do, and it has to be enough to start: mine by hand, put
            // ore in a furnace, and make the handful of things nothing gates. If one of these were
            // ever gated the world could never reach its first trigger - and every other test here
            // would still pass, because they all begin with a lab already placed.
            for (String[] free : new String[][] {
                    {"nauvis_logistics", "transport_belt"}, {"nauvis_logistics", "burner_inserter"},
                    {"nauvis_logistics", "chest"}, {"nauvis_machines", "furnace"},
                    {"neoprogressivematerials", "iron_gear_wheel"}}) {
                helper.assertTrue(Research.isUnlocked(server, recipe(free[0], free[1])),
                        free[0] + ":" + free[1] + " is gated, but nothing in the tree unlocks it, "
                                + "so a new world could never craft it at all");
            }

            Research.state(server).complete(drillTech);
            Research.changedForTest(server);
            helper.assertTrue(Research.isUnlocked(server, drill),
                    "the electric drill is still locked after researching it");

            Research.state(server).forget(drillTech);
            Research.changedForTest(server);
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
            state.recordMade(trigger.item(), -state.made(trigger.item()));
            Research.changedForTest(server);

            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler is craftable before steam power");

            // One short. The count is the whole of the condition, so being able to stop one below
            // it is what says the number is read rather than ignored.
            Research.recordMade(helper.getLevel(), trigger.item(), trigger.count() - 1);
            helper.assertFalse(Research.state(server).isCompleted(steam),
                    "steam power finished one item short of its trigger");
            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler unlocked one item short of steam power's trigger");

            Research.recordMade(helper.getLevel(), trigger.item(), 1);
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
     * <p>Four properties, and each one is a way the picture would be wrong rather than plain:
     *
     * <ul>
     *   <li><b>every node is right of every prerequisite.</b> This is the whole claim a tech tree
     *       makes. An edge pointing backwards is a diagram that lies about what comes first;</li>
     *   <li><b>no two nodes share a cell</b>, or one is drawn on top of another and simply cannot
     *       be clicked;</li>
     *   <li><b>every technology is placed exactly once</b> - a tree that quietly omits a node is
     *       the failure a player would never report, because they cannot miss what they cannot
     *       see;</li>
     *   <li><b>the same tree lays out the same way twice.</b> A layout that shuffled between
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
            TechnologyLayout.Layout layout = TechnologyLayout.of(access);
            int expected = ModTechnologies.registry(access).size();

            helper.assertValueEqual(layout.nodes().size(), expected,
                    "technologies placed; a tree that omits one is a gap nobody can report");

            java.util.Set<Long> cells = new java.util.HashSet<>();
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                helper.assertTrue(cells.add((long) placed.column() << 32 | placed.row()),
                        placed.key().identifier() + " shares a cell with something else, so one of "
                                + "the two is drawn underneath and cannot be clicked");
                helper.assertTrue(placed.column() >= 0 && placed.row() >= 0,
                        placed.key().identifier() + " is placed off the grid");
            }

            // The claim the whole picture rests on.
            for (TechnologyLayout.Edge edge : layout.edges()) {
                TechnologyLayout.Placed from = layout.at(edge.from());
                TechnologyLayout.Placed to = layout.at(edge.to());
                helper.assertTrue(from != null && to != null,
                        "an edge points at a technology that was never placed");
                helper.assertTrue(from.column() < to.column(),
                        edge.to().identifier() + " is not right of its prerequisite "
                                + edge.from().identifier() + " - the arrow points backwards and "
                                + "the diagram is lying about what comes first");
            }

            // Every prerequisite that exists is drawn. An edge silently dropped is a technology
            // whose real cost is invisible, which is the one thing a player opens this to learn.
            int wanted = 0;
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                for (ResourceKey<Technology> prerequisite : placed.technology().prerequisites()) {
                    if (layout.at(prerequisite) != null) {
                        wanted++;
                    }
                }
            }
            helper.assertValueEqual(layout.edges().size(), wanted, "prerequisite arrows drawn");

            TechnologyLayout.Layout again = TechnologyLayout.of(access);
            helper.assertValueEqual(again.nodes(), layout.nodes(),
                    "the layout is not deterministic - it would shuffle between openings");
            helper.succeed();
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
}
