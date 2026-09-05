package com.jaguarm.nauvisfluids;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvisfluids.multiblock.MachineShape;
import com.jaguarm.nauvisfluids.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.CrudeOilField;
import com.jaguarm.nauvisfluids.oil.CrudeOilFieldFeature;
import com.jaguarm.nauvisfluids.pipe.FluidNetwork;
import com.jaguarm.nauvisfluids.pipe.FluidNetworkManager;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackStatus;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import com.jaguarm.nauvisfluids.registry.ModItems;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless.
 *
 * <p>Two groups. The pipe tests assert the <em>graph</em>: that a line of pipes is one object,
 * that it splits and merges when the line does, and that what was in it is divided rather than
 * duplicated or lost. Whether steam actually reaches an engine through a pipe is asserted in the
 * pack mod, because it takes a boiler and an engine and this mod may not compile against the one
 * that owns them.
 *
 * <p>The oil tests assert Factorio's numbers: a well is unmovable, a pumpjack stands only over
 * one and snaps to it, it pumps ten a second from a 100% well and takes ten off the well a cycle,
 * the well stops at its floor, and the machine sleeps for each of the three reasons it can.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class NauvisFluidsGameTests {

    private NauvisFluidsGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /**
     * How much empty world to leave around a test that builds a machine. A pumpjack is three
     * tiles across and a field of wells is twenty; without room, one test's blocks land in the
     * next test along.
     */
    private static final int PADDING = 24;

    /** Room for the field test, which builds a platform twenty-four blocks square. */
    private static final int WIDE_PADDING = 40;

    /** Where a well sits in the machine tests, and where the pumpjack over it is anchored. */
    private static final BlockPos WELL = new BlockPos(2, 1, 2);
    private static final BlockPos PUMPJACK = WELL.above();

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisFluids.MODID);

    static {
        TEST_TYPES.register("pipe_run_is_one_object", () -> PipeRunIsOneObjectTest.CODEC);
        TEST_TYPES.register("pipe_run_splits_and_merges", () -> PipeRunSplitsAndMergesTest.CODEC);
        TEST_TYPES.register("pipe_connects_to_what_offers_fluid", () -> PipeConnectsTest.CODEC);
        TEST_TYPES.register("fluids_are_registered", () -> FluidsAreRegisteredTest.CODEC);
        TEST_TYPES.register("pipe_reports_its_run", () -> PipeReportsItsRunTest.CODEC);
        TEST_TYPES.register("crude_oil_is_unmovable", () -> CrudeOilIsUnmovableTest.CODEC);
        TEST_TYPES.register("pumpjack_stands_only_on_a_well", () -> PumpjackStandsOnlyOnAWellTest.CODEC);
        TEST_TYPES.register("pumpjack_pumps_at_factorio_rate", () -> PumpjackPumpsAtFactorioRateTest.CODEC);
        TEST_TYPES.register("well_stops_at_its_floor", () -> WellStopsAtItsFloorTest.CODEC);
        TEST_TYPES.register("pumpjack_sleeps", () -> PumpjackSleepsTest.CODEC);
        TEST_TYPES.register("pumpjack_feeds_a_pipe", () -> PumpjackFeedsAPipeTest.CODEC);
        TEST_TYPES.register("oil_field_is_pumpable", () -> OilFieldIsPumpableTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "pipe_run_is_one_object", PipeRunIsOneObjectTest::new, 100);
        register(event, environment, "pipe_run_splits_and_merges", PipeRunSplitsAndMergesTest::new, 200);
        register(event, environment, "pipe_connects_to_what_offers_fluid", PipeConnectsTest::new, 100);
        register(event, environment, "fluids_are_registered", FluidsAreRegisteredTest::new, 20);
        register(event, environment, "pipe_reports_its_run", PipeReportsItsRunTest::new, 100);
        registerSpaced(event, environment, "crude_oil_is_unmovable", CrudeOilIsUnmovableTest::new, 40, PADDING);
        registerSpaced(event, environment, "pumpjack_stands_only_on_a_well",
                PumpjackStandsOnlyOnAWellTest::new, 40, PADDING);
        registerSpaced(event, environment, "pumpjack_pumps_at_factorio_rate",
                PumpjackPumpsAtFactorioRateTest::new, 100, PADDING);
        registerSpaced(event, environment, "well_stops_at_its_floor", WellStopsAtItsFloorTest::new, 100, PADDING);
        registerSpaced(event, environment, "pumpjack_sleeps", PumpjackSleepsTest::new, 200, PADDING);
        registerSpaced(event, environment, "pumpjack_feeds_a_pipe", PumpjackFeedsAPipeTest::new, 100, PADDING);
        registerSpaced(event, environment, "oil_field_is_pumpable", OilFieldIsPumpableTest::new, 60, WIDE_PADDING);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE)));
    }

    private static void registerSpaced(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory,
            int maxTicks, int padding) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, padding)));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static FluidNetworkManager grid(GameTestHelper helper) {
        return FluidNetworkManager.of(helper.getLevel());
    }

    private static FluidNetwork networkAt(GameTestHelper helper, BlockPos pos, String what) {
        FluidNetwork network = grid(helper).networkAt(helper.absolutePos(pos));
        helper.assertTrue(network != null, what);
        return network;
    }

    private static void pipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.PIPE.get());
    }

    /** A well with a known amount under it, so a test's numbers do not depend on the world seed. */
    private static CrudeOilBlockEntity well(GameTestHelper helper, BlockPos pos, long amount) {
        helper.setBlock(pos, ModBlocks.CRUDE_OIL.get());
        CrudeOilBlockEntity well = helper.getBlockEntity(pos, CrudeOilBlockEntity.class);
        well.reset(amount);
        return well;
    }

    /** A pumpjack anchored at {@code anchor}, facing north: its outlet is the north-east corner's north face. */
    private static PumpjackBlockEntity pumpjack(GameTestHelper helper, BlockPos anchor) {
        PumpjackBlock block = ModBlocks.PUMPJACK.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(PumpjackBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, PumpjackBlockEntity.class);
    }

    /** Fills the machine's buffer the way a pole would: through the insert-only grid view. */
    private static void charge(PumpjackBlockEntity pumpjack) {
        try (Transaction transaction = Transaction.openRoot()) {
            pumpjack.gridView().insert(PumpjackBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    /** Takes oil off the machine the way a pipe would: through the extract-only outlet view. */
    private static int draw(PumpjackBlockEntity pumpjack, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = pumpjack.output().extract(FluidResource.of(ModFluids.CRUDE_OIL.get()), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /**
     * How much a pumpjack has banked after this many cycles from a well that started at
     * {@code initial}: ten times the yield each cycle, with the fraction carried rather than
     * rounded. A 100% well gives ten, then 9.9997 - so nineteen after two cycles, not twenty, and
     * the missing three ten-thousandths turn up in a later cycle. That is Factorio's total to
     * within a unit, and the carry is what makes it exact over time.
     */
    private static int bankedAfter(long initial, int cycles) {
        long due = 0;
        long amount = initial;
        for (int cycle = 0; cycle < cycles; cycle++) {
            due += amount;
            amount -= CrudeOilBlockEntity.DEPLETION;
        }
        return (int) (due / PumpjackBlockEntity.UNIT_DIVISOR);
    }

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    /**
     * What the pumpjack would place as if a player clicked the top of {@code ground}, asked
     * exactly the way a right-click asks it. No player, so the facing is north.
     */
    private static @Nullable BlockState placement(GameTestHelper helper, BlockPos ground) {
        BlockPos below = helper.absolutePos(ground);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.PUMPJACK.get()), hit);
        return ModBlocks.PUMPJACK.get().getStateForPlacement(context);
    }

    // --- pipes --------------------------------------------------------------------------------

    /**
     * A line of pipes is one object, whatever its length.
     *
     * <p>The claim the whole design rests on, and the third time this pack has made it after the
     * belt note and the electric network. A pipe per block entity would work and would cost N ticks
     * a second for N pipes; this asserts there is exactly one thing to tick, and that its capacity
     * grew with the run rather than staying the size of one pipe.
     */
    public static class PipeRunIsOneObjectTest extends GameTestInstance {

        public static final MapCodec<PipeRunIsOneObjectTest> CODEC =
                RecordCodecBuilder.<PipeRunIsOneObjectTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PipeRunIsOneObjectTest::info))
                                .apply(i, PipeRunIsOneObjectTest::new));

        private static final int LENGTH = 5;

        public PipeRunIsOneObjectTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < LENGTH; x++) {
                pipe(helper, new BlockPos(x, 1, 0));
            }

            helper.runAfterDelay(5, () -> {
                FluidNetwork first = networkAt(helper, new BlockPos(0, 1, 0), "the first pipe has no run");
                FluidNetwork last = networkAt(helper, new BlockPos(LENGTH - 1, 1, 0),
                        "the last pipe has no run");

                helper.assertTrue(first == last,
                        "a straight line of pipes is more than one object, so a long run would cost "
                                + "a tick per pipe and take a tick per pipe to cross");
                helper.assertValueEqual(first.pipeCount(), LENGTH, "pipes in the run");
                helper.assertValueEqual(first.capacity(),
                        LENGTH * FluidNetwork.CAPACITY_PER_PIPE,
                        "how much the run can hold - a pipeline is one tank as long as itself");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pipe run is one object");
        }
    }

    /**
     * Breaking a line splits the run; joining it merges them again.
     *
     * <p>The expensive-to-get-wrong half, exactly as for poles. A split that never happens leaves
     * two disconnected halves sharing one tank, which is a pipeline that carries fluid through a
     * gap.
     */
    public static class PipeRunSplitsAndMergesTest extends GameTestInstance {

        public static final MapCodec<PipeRunSplitsAndMergesTest> CODEC =
                RecordCodecBuilder.<PipeRunSplitsAndMergesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PipeRunSplitsAndMergesTest::info))
                                .apply(i, PipeRunSplitsAndMergesTest::new));

        private static final BlockPos LEFT = new BlockPos(0, 1, 0);
        private static final BlockPos MIDDLE = new BlockPos(1, 1, 0);
        private static final BlockPos RIGHT = new BlockPos(2, 1, 0);

        public PipeRunSplitsAndMergesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            pipe(helper, LEFT);
            pipe(helper, MIDDLE);
            pipe(helper, RIGHT);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> helper.assertTrue(
                            networkAt(helper, LEFT, "no run") == networkAt(helper, RIGHT, "no run"),
                            "three pipes in a row are not one run"))
                    .thenExecute(() -> helper.setBlock(MIDDLE, Blocks.AIR))
                    .thenExecuteAfter(5, () -> {
                        FluidNetwork left = networkAt(helper, LEFT, "the left pipe lost its run");
                        FluidNetwork right = networkAt(helper, RIGHT, "the right pipe lost its run");
                        helper.assertFalse(left == right,
                                "breaking the pipe between two halves left them on one run, so "
                                        + "fluid would cross a gap that is not there");
                        helper.assertValueEqual(left.pipeCount(), 1, "pipes in the left half");
                    })
                    .thenExecute(() -> pipe(helper, MIDDLE))
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(
                                networkAt(helper, LEFT, "no run") == networkAt(helper, RIGHT, "no run"),
                                "putting the pipe back did not join the two halves");
                        helper.assertValueEqual(
                                networkAt(helper, LEFT, "no run").pipeCount(), 3,
                                "pipes in the rejoined run");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pipe run splits and merges");
        }
    }

    /**
     * A pipe reaches towards what offers fluid, and not towards what does not.
     *
     * <p>The connection is in the block state, so it is what the player sees. Reaching towards a
     * plain stone block would be a lie drawn in the world, and worse than no connection at all.
     */
    public static class PipeConnectsTest extends GameTestInstance {

        public static final MapCodec<PipeConnectsTest> CODEC =
                RecordCodecBuilder.<PipeConnectsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PipeConnectsTest::info))
                                .apply(i, PipeConnectsTest::new));

        private static final BlockPos PIPE = new BlockPos(1, 1, 0);
        private static final BlockPos NEIGHBOUR_PIPE = new BlockPos(2, 1, 0);
        private static final BlockPos STONE = new BlockPos(0, 1, 0);

        public PipeConnectsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(STONE, Blocks.STONE);
            pipe(helper, PIPE);
            pipe(helper, NEIGHBOUR_PIPE);

            helper.runAfterDelay(5, () -> {
                var state = helper.getLevel().getBlockState(helper.absolutePos(PIPE));
                helper.assertTrue(state.getValue(PipeBlock.EAST),
                        "a pipe does not reach towards the pipe beside it");
                helper.assertFalse(state.getValue(PipeBlock.WEST),
                        "a pipe reaches towards a stone block, which offers it nothing");
                helper.assertFalse(state.getValue(PipeBlock.UP),
                        "a pipe reaches towards thin air");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pipe connects to what offers fluid");
        }
    }

    /**
     * Steam and crude oil exist under the ids the mapping has always given them.
     *
     * <p>{@code nauvis_fluids:steam} is identity: {@code data/mapping.json} names it, and
     * {@code nauvis_power} finds it by that id rather than by importing it, so a rename here would
     * quietly stop every boiler in the pack from making anything. {@code nauvis_fluids:crude_oil}
     * is the same kind of fact for everything downstream of a pumpjack.
     */
    public static class FluidsAreRegisteredTest extends GameTestInstance {

        public static final MapCodec<FluidsAreRegisteredTest> CODEC =
                RecordCodecBuilder.<FluidsAreRegisteredTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FluidsAreRegisteredTest::info))
                                .apply(i, FluidsAreRegisteredTest::new));

        public FluidsAreRegisteredTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (String name : new String[] {"steam", "crude_oil"}) {
                Identifier id = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name);
                helper.assertTrue(BuiltInRegistries.FLUID.getValue(id) != Fluids.EMPTY,
                        "nauvis_fluids:" + name + " is not registered");
            }
            for (String name : new String[] {"pipe", "crude_oil", "pumpjack"}) {
                Block block = BuiltInRegistries.BLOCK.getValue(
                        Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name));
                helper.assertTrue(block != Blocks.AIR, "nauvis_fluids:" + name + " is not registered");
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("fluids are registered");
        }
    }

    /**
     * The numbers behind the readout, which cannot be asserted any other way.
     *
     * <p>Jade's tooltip is drawn on the client from data the server sends, and neither half can be
     * reached headlessly. What <em>can</em> be reached is what the server would send - the run's
     * extent and its capacity - and that is where the bug would be: a readout reporting the block
     * rather than the run would say a pipe holds nothing, for ever, and look perfectly reasonable
     * doing it.
     */
    public static class PipeReportsItsRunTest extends GameTestInstance {

        public static final MapCodec<PipeReportsItsRunTest> CODEC =
                RecordCodecBuilder.<PipeReportsItsRunTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PipeReportsItsRunTest::info))
                                .apply(i, PipeReportsItsRunTest::new));

        private static final int LENGTH = 6;

        public PipeReportsItsRunTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < LENGTH; x++) {
                pipe(helper, new BlockPos(x, 1, 0));
            }

            helper.runAfterDelay(5, () -> {
                // Asked from the far end, because every pipe in a run must answer for the whole
                // run - that is what makes the readout worth having.
                FluidNetwork run = networkAt(helper, new BlockPos(LENGTH - 1, 1, 0),
                        "the last pipe has no run");

                helper.assertValueEqual(run.pipeCount(), LENGTH,
                        "the extent the readout would print");
                helper.assertValueEqual(run.capacity(), LENGTH * FluidNetwork.CAPACITY_PER_PIPE,
                        "the capacity the readout would print");
                helper.assertValueEqual(run.amount(), 0, "what an unconnected run is holding");
                helper.assertTrue(run.fluid().isEmpty(),
                        "an empty run names a fluid, so the readout would claim to hold something");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pipe reports its run");
        }
    }

    // --- oil ----------------------------------------------------------------------------------

    /**
     * An oil well cannot be mined, pushed or dropped, and it is worth something the moment it
     * exists.
     *
     * <p>Factorio's {@code crude-oil} is a resource entity: nothing a player does moves it. Here
     * that is three block properties, each of which could be lost in a refactor of
     * {@code ModBlocks} without anything else noticing. The amount is the other half - a well
     * placed in the world works out how rich it is from where it is, so a well placed with nothing
     * written into it must still answer with Factorio's floor or better.
     */
    public static class CrudeOilIsUnmovableTest extends GameTestInstance {

        public static final MapCodec<CrudeOilIsUnmovableTest> CODEC =
                RecordCodecBuilder.<CrudeOilIsUnmovableTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(CrudeOilIsUnmovableTest::info))
                                .apply(i, CrudeOilIsUnmovableTest::new));

        public CrudeOilIsUnmovableTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(WELL, ModBlocks.CRUDE_OIL.get());
            BlockPos at = helper.absolutePos(WELL);
            BlockState state = helper.getBlockState(WELL);

            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), at) < 0,
                    "an oil well can be mined, so a player can pick up Factorio's one unmovable resource");
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK,
                    "an oil well can be pushed by a piston");
            helper.assertTrue(ModBlocks.CRUDE_OIL.get().getLootTable().isEmpty(),
                    "an oil well has a loot table, so something that breaks it gets a well back");

            CrudeOilBlockEntity well = helper.getBlockEntity(WELL, CrudeOilBlockEntity.class);
            helper.assertTrue(well.amount() >= CrudeOilField.ADDITIONAL_RICHNESS + CrudeOilField.SPREAD_MIN,
                    "a fresh well is poorer than Factorio's additional richness allows: " + well.amount());
            helper.assertTrue(well.amount() % CrudeOilBlockEntity.DEPLETION == 0,
                    "a well's amount is not a multiple of a cycle's depletion: " + well.amount());
            helper.assertValueEqual(well.initial(), well.amount(), "a fresh well's initial amount");
            helper.assertTrue(well.yieldPercent() >= 90,
                    "a well near the start reads under 90%: " + well.yieldPercent());
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("crude oil is unmovable");
        }
    }

    /**
     * A pumpjack goes down centred on a well, from a click anywhere over it, and nowhere else.
     *
     * <p>Factorio's rule and Factorio's snapping. Three clicks: the block over the well, which
     * must place with the centre pinned to it; the block over the well's diagonal neighbour, which
     * must place with a <em>corner</em> pinned to it so that the centre still lands over the well;
     * and a block two away, which is not over the machine's footprint and must refuse.
     */
    public static class PumpjackStandsOnlyOnAWellTest extends GameTestInstance {

        public static final MapCodec<PumpjackStandsOnlyOnAWellTest> CODEC =
                RecordCodecBuilder.<PumpjackStandsOnlyOnAWellTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackStandsOnlyOnAWellTest::info))
                                .apply(i, PumpjackStandsOnlyOnAWellTest::new));

        public PumpjackStandsOnlyOnAWellTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < 6; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                }
            }
            well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            MachineShape shape = PumpjackShape.SHAPE;

            BlockState centred = placement(helper, WELL);
            helper.assertTrue(centred != null, "a pumpjack refuses the block directly over a well");
            helper.assertValueEqual(centred.getValue(shape.part()), shape.placement(),
                    "the cell pinned to a click over the well");

            BlockPos diagonal = WELL.offset(1, 0, 1);
            BlockState snapped = placement(helper, diagonal);
            helper.assertTrue(snapped != null,
                    "a pumpjack refuses a click one block off the well, so placement does not snap");
            BlockPos anchor = shape.anchorPos(helper.absolutePos(diagonal.above()),
                    snapped.getValue(shape.part()), snapped.getValue(PumpjackBlock.FACING));
            helper.assertValueEqual(anchor, helper.absolutePos(PUMPJACK),
                    "where a pumpjack clicked one block off the well ends up centred");

            helper.assertTrue(placement(helper, WELL.offset(2, 0, 0)) == null,
                    "a pumpjack accepts a block two away from the well, which is not over one");
            helper.assertTrue(placement(helper, new BlockPos(5, 1, 5)) == null,
                    "a pumpjack accepts plain ground");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack stands only on a well");
        }
    }

    /**
     * Ten crude oil a second from a 100% well, and ten off the well each second.
     *
     * <p>The two numbers that are identity. One cycle is twenty ticks, so after thirty the first
     * cycle has banked and the second has not, and after fifty two have. The second cycle is
     * pumped from a well that is no longer quite 100%, and the machine carries the fraction rather
     * than rounding it, so two cycles bank nineteen - {@link #bankedAfter} is that arithmetic.
     */
    public static class PumpjackPumpsAtFactorioRateTest extends GameTestInstance {

        public static final MapCodec<PumpjackPumpsAtFactorioRateTest> CODEC =
                RecordCodecBuilder.<PumpjackPumpsAtFactorioRateTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackPumpsAtFactorioRateTest::info))
                                .apply(i, PumpjackPumpsAtFactorioRateTest::new));

        public PumpjackPumpsAtFactorioRateTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            CrudeOilBlockEntity well = well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.startSequence()
                    .thenExecuteAfter(30, () -> {
                        helper.assertValueEqual(pumpjack.stored(), PumpjackBlockEntity.UNITS_PER_CYCLE_AT_NORMAL,
                                "crude oil banked after one cycle from a 100% well");
                        helper.assertValueEqual(well.amount(),
                                CrudeOilBlockEntity.NORMAL - CrudeOilBlockEntity.DEPLETION,
                                "what one cycle takes off a well");
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.PUMPING, "status while pumping");
                    })
                    .thenExecuteAfter(20, () -> {
                        // Nineteen, not twenty: the second cycle ran at 99.997% and the fraction is
                        // carried, not rounded up. See bankedAfter.
                        helper.assertValueEqual(pumpjack.stored(), bankedAfter(CrudeOilBlockEntity.NORMAL, 2),
                                "crude oil banked after two cycles");
                        helper.assertValueEqual(well.amount(),
                                CrudeOilBlockEntity.NORMAL - 2 * CrudeOilBlockEntity.DEPLETION,
                                "what two cycles take off a well");
                        // 90 kW is twelve a tick for every tick spent pumping, the third cycle's
                        // ticks included - not a price per cycle. Two full cycles at least, and
                        // never more than the fifty ticks that have passed.
                        int spent = PumpjackBlockEntity.ENERGY_CAPACITY - pumpjack.energyStored();
                        helper.assertTrue(spent >= 2 * PumpjackBlockEntity.CYCLE_TICKS * PumpjackBlockEntity.ENERGY_PER_TICK
                                        && spent <= 50 * PumpjackBlockEntity.ENERGY_PER_TICK
                                        && spent % PumpjackBlockEntity.ENERGY_PER_TICK == 0,
                                "electricity spent over two cycles and a bit: " + spent);
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack pumps at Factorio's rate");
        }
    }

    /**
     * A well is pumped down to its floor and never past it, and at the floor it still pumps.
     *
     * <p>Factorio's minimum is 60000 - a fifth of normal, so two a second - or a fifth of what the
     * well started with, whichever is more. The well here starts one cycle above the minimum: the
     * first cycle reaches the floor, the second finds it there and takes nothing more, and both
     * cycles bank the same two units. The second well checks the other arm of the rule.
     */
    public static class WellStopsAtItsFloorTest extends GameTestInstance {

        public static final MapCodec<WellStopsAtItsFloorTest> CODEC =
                RecordCodecBuilder.<WellStopsAtItsFloorTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(WellStopsAtItsFloorTest::info))
                                .apply(i, WellStopsAtItsFloorTest::new));

        private static final BlockPos RICH_WELL = new BlockPos(8, 1, 2);

        public WellStopsAtItsFloorTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            CrudeOilBlockEntity well = well(helper, WELL,
                    CrudeOilBlockEntity.MINIMUM + CrudeOilBlockEntity.DEPLETION);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            CrudeOilBlockEntity rich = well(helper, RICH_WELL, 4 * CrudeOilBlockEntity.NORMAL);
            helper.assertValueEqual(rich.floor(), 4 * CrudeOilBlockEntity.NORMAL / 5,
                    "the floor of a 400% well - a fifth of what it started with");
            helper.assertValueEqual(well.floor(), CrudeOilBlockEntity.MINIMUM,
                    "the floor of a well that started near the minimum");

            helper.startSequence()
                    .thenExecuteAfter(30, () -> {
                        helper.assertValueEqual(well.amount(), CrudeOilBlockEntity.MINIMUM,
                                "a well pumped down to Factorio's minimum");
                        helper.assertTrue(well.isAtFloor(), "a well at the minimum does not say so");
                        helper.assertValueEqual(pumpjack.stored(), 2, "two a cycle at 20%");
                    })
                    .thenExecuteAfter(20, () -> {
                        helper.assertValueEqual(well.amount(), CrudeOilBlockEntity.MINIMUM,
                                "a well pumped past its floor - Factorio's oil never runs dry");
                        helper.assertValueEqual(pumpjack.stored(), 4, "a well at its floor still pumps");
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.PUMPING,
                                "a pumpjack on a floored well stopped");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("well stops at its floor");
        }
    }

    /**
     * A pumpjack costs nothing when it cannot work, and wakes for each of the three things that
     * give it work back.
     *
     * <p>Non-negotiable #5, three times over. Without a well it is not scheduled; put a well under
     * it and it wakes. Without power it is not scheduled; give it power and it wakes in the same
     * tick. With a full tank it is not scheduled; draw from the outlet and it wakes and banks the
     * cycle it was holding. Delete any of the three wake-ups and the matching step goes red.
     *
     * <p>The well is absurdly rich - five hundred a cycle - so the tank fills in two cycles rather
     * than a hundred. That is behaviour under test, not a Factorio number.
     */
    public static class PumpjackSleepsTest extends GameTestInstance {

        public static final MapCodec<PumpjackSleepsTest> CODEC =
                RecordCodecBuilder.<PumpjackSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackSleepsTest::info))
                                .apply(i, PumpjackSleepsTest::new));

        /** Five hundred units a cycle: 50 times normal. */
        private static final long GUSHER = 50 * CrudeOilBlockEntity.NORMAL;

        public PumpjackSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(WELL, Blocks.STONE);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            Block block = ModBlocks.PUMPJACK.get();
            CrudeOilBlockEntity[] well = new CrudeOilBlockEntity[1];

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.NO_WELL,
                                "a pumpjack on stone does not know it has no well");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with no well is still ticking");
                    })
                    .thenExecute(() -> {
                        well[0] = well(helper, WELL, GUSHER);
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "a well appearing under a pumpjack did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.NO_POWER,
                                "an unpowered pumpjack over a well does not say it lacks power");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with no power is still ticking");
                    })
                    .thenExecute(() -> {
                        charge(pumpjack);
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "power arriving did not wake the pumpjack in the same tick");
                    })
                    .thenExecuteAfter(70, () -> {
                        // Two cycles all but filled the tank - a hair under, since the second ran
                        // at a hair under fifty times normal - and the third finished and could
                        // not bank. A held cycle takes nothing off the well: two cycles' worth.
                        helper.assertTrue(pumpjack.stored() > PumpjackBlockEntity.TANK_CAPACITY - 500,
                                "the tank has room for a cycle and the pumpjack stopped anyway: " + pumpjack.stored());
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.OUTPUT_FULL,
                                "a pumpjack with a full tank does not say so");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with a full tank is still ticking");
                        helper.assertValueEqual(well[0].amount(), GUSHER - 2 * CrudeOilBlockEntity.DEPLETION,
                                "a cycle that could not bank took oil off the well anyway");
                    })
                    .thenExecute(() -> {
                        helper.assertValueEqual(draw(pumpjack, 500), 500, "oil drawn off a full pumpjack");
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "drawing from the outlet did not wake the pumpjack");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(well[0].amount(), GUSHER - 3 * CrudeOilBlockEntity.DEPLETION,
                                "the held cycle was not banked once there was room");
                        helper.assertTrue(pumpjack.stored() > PumpjackBlockEntity.TANK_CAPACITY - 500,
                                "the held cycle's oil is not in the tank: " + pumpjack.stored());
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack sleeps");
        }
    }

    /**
     * Oil leaves by the outlet and by nothing else, and a pipe there carries it away.
     *
     * <p>The outlet is the north-east corner's north face on a north-facing machine, which is
     * Factorio's corner. A pipe against the east flank must not connect - that a pipe in the wrong
     * place gets nothing is what makes the outlet a thing the player can be right about.
     */
    public static class PumpjackFeedsAPipeTest extends GameTestInstance {

        public static final MapCodec<PumpjackFeedsAPipeTest> CODEC =
                RecordCodecBuilder.<PumpjackFeedsAPipeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackFeedsAPipeTest::info))
                                .apply(i, PumpjackFeedsAPipeTest::new));

        public PumpjackFeedsAPipeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MachineShape shape = PumpjackShape.SHAPE;
            BlockPos outletCell = shape.cellPos(PUMPJACK, PumpjackShape.OUTLET_CELL, Direction.NORTH);
            BlockPos outletPipe = outletCell.north();
            BlockPos flankPipe = PUMPJACK.east(2);

            // Pipes first. A block put down by anything but a player never runs
            // getStateForPlacement - see PITFALLS.md - so a pipe placed beside a machine that is
            // already there would show no connection; placed first, the machine arriving is the
            // neighbour change that makes each pipe re-read the face towards it.
            pipe(helper, outletPipe);
            pipe(helper, flankPipe);
            well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(helper.getBlockState(outletPipe).getValue(PipeBlock.SOUTH),
                                "a pipe at the outlet does not reach into the pumpjack");
                        helper.assertFalse(helper.getBlockState(flankPipe).getValue(PipeBlock.WEST),
                                "a pipe on the flank connects to a pumpjack, so the outlet means nothing");
                    })
                    .thenExecuteAfter(45, () -> {
                        FluidNetwork run = networkAt(helper, outletPipe, "the outlet pipe has no run");
                        helper.assertValueEqual(run.fluid().getFluid(), ModFluids.CRUDE_OIL.get(),
                                "what the outlet pipe is carrying");
                        helper.assertTrue(run.amount() + pumpjack.stored() == bankedAfter(CrudeOilBlockEntity.NORMAL, 2),
                                "oil went missing between the pumpjack and the pipe: " + run.amount()
                                        + " in the run, " + pumpjack.stored() + " in the tank");
                        helper.assertTrue(run.amount() > 0, "the pipe run drew nothing from the pumpjack");
                        helper.assertValueEqual(networkAt(helper, flankPipe, "the flank pipe has no run").amount(), 0,
                                "what a pipe on the flank carries");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack feeds a pipe");
        }
    }

    /**
     * A generated field is a field: several wells, each far enough from the next for a pumpjack,
     * each on a levelled pad a pumpjack fits over.
     *
     * <p>The feature is asked to place a field on a flat platform, which is the case where the
     * levelling does nothing; the point is the spacing and the fit, which are what make a field
     * something you lay pumpjacks out on. Every well must accept a pumpjack centred on it.
     */
    public static class OilFieldIsPumpableTest extends GameTestInstance {

        public static final MapCodec<OilFieldIsPumpableTest> CODEC =
                RecordCodecBuilder.<OilFieldIsPumpableTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OilFieldIsPumpableTest::info))
                                .apply(i, OilFieldIsPumpableTest::new));

        private static final int SIZE = 24;

        public OilFieldIsPumpableTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.SHORT_GRASS);
                }
            }
            BlockPos origin = helper.absolutePos(new BlockPos(SIZE / 2, 2, SIZE / 2));

            List<BlockPos> wells = CrudeOilFieldFeature.placeField(helper.getLevel(),
                    RandomSource.create(20260905L), origin);

            helper.assertTrue(wells.size() >= CrudeOilFieldFeature.MIN_WELLS,
                    "a field of " + wells.size() + " wells, fewer than a field has");
            for (BlockPos well : wells) {
                helper.assertTrue(helper.getLevel().getBlockState(well).is(ModBlocks.CRUDE_OIL.get()),
                        "a well the feature reported is not there: " + well);
                for (BlockPos other : wells) {
                    if (other != well) {
                        int apart = Math.max(Math.abs(other.getX() - well.getX()), Math.abs(other.getZ() - well.getZ()));
                        helper.assertTrue(apart >= CrudeOilFieldFeature.WELL_SPACING,
                                "two wells " + apart + " apart, too close for two pumpjacks");
                    }
                }
                // The grass over the pad was cleared, so a pumpjack fits over every well.
                BlockPos relative = well.subtract(helper.absolutePos(BlockPos.ZERO));
                BlockState placed = placement(helper, relative);
                helper.assertTrue(placed != null, "a pumpjack does not fit over a generated well at " + well);
                CrudeOilBlockEntity entity = helper.getBlockEntity(relative, CrudeOilBlockEntity.class);
                helper.assertTrue(entity.amount() >= CrudeOilBlockEntity.MINIMUM,
                        "a generated well is below Factorio's minimum");
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("oil field is pumpable");
        }
    }
}
