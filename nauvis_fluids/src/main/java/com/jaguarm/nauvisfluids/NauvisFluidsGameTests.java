package com.jaguarm.nauvisfluids;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlockEntity;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpItem;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpStatus;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.CrudeOilField;
import com.jaguarm.nauvisfluids.oil.CrudeOilFieldFeature;
import com.jaguarm.nauvisfluids.oil.OilProgress;
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
import com.jaguarm.nauvisfluids.water.NaturalWaterFeature;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
        TEST_TYPES.register("pumpjack_reports_what_it_mines", () -> PumpjackReportsWhatItMinesTest.CODEC);
        TEST_TYPES.register("pumpjack_caps_a_cycle_at_its_tank", () -> PumpjackCapsACycleTest.CODEC);
        TEST_TYPES.register("oil_command_places_a_field", () -> OilCommandPlacesAFieldTest.CODEC);
        TEST_TYPES.register("natural_water_is_bucketed_as_water", () -> NaturalWaterIsBucketedAsWaterTest.CODEC);
        TEST_TYPES.register("natural_water_makes_no_new_source", () -> NaturalWaterMakesNoNewSourceTest.CODEC);
        TEST_TYPES.register("worldgen_water_becomes_natural", () -> WorldgenWaterBecomesNaturalTest.CODEC);
        TEST_TYPES.register("offshore_pump_stands_only_at_water", () -> OffshorePumpStandsOnlyAtWaterTest.CODEC);
        TEST_TYPES.register("offshore_pump_turns_to_the_water", () -> OffshorePumpTurnsToTheWaterTest.CODEC);
        TEST_TYPES.register("offshore_pump_floats_on_a_lake", () -> OffshorePumpFloatsOnALakeTest.CODEC);
        TEST_TYPES.register("offshore_pump_pumps_at_factorio_rate", () -> OffshorePumpPumpsAtFactorioRateTest.CODEC);
        TEST_TYPES.register("offshore_pump_fills_a_pipe", () -> OffshorePumpFillsAPipeTest.CODEC);
        TEST_TYPES.register("offshore_pump_sleeps", () -> OffshorePumpSleepsTest.CODEC);
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
        registerSpaced(event, environment, "pumpjack_reports_what_it_mines",
                PumpjackReportsWhatItMinesTest::new, 100, PADDING);
        registerSpaced(event, environment, "pumpjack_caps_a_cycle_at_its_tank", PumpjackCapsACycleTest::new, 60, PADDING);
        registerSpaced(event, environment, "oil_command_places_a_field", OilCommandPlacesAFieldTest::new, 40, WIDE_PADDING);
        registerSpaced(event, environment, "natural_water_is_bucketed_as_water",
                NaturalWaterIsBucketedAsWaterTest::new, 20, PADDING);
        registerSpaced(event, environment, "natural_water_makes_no_new_source",
                NaturalWaterMakesNoNewSourceTest::new, 100, PADDING);
        registerSpaced(event, environment, "worldgen_water_becomes_natural",
                WorldgenWaterBecomesNaturalTest::new, 20, PADDING);
        registerSpaced(event, environment, "offshore_pump_stands_only_at_water",
                OffshorePumpStandsOnlyAtWaterTest::new, 40, PADDING);
        registerSpaced(event, environment, "offshore_pump_turns_to_the_water",
                OffshorePumpTurnsToTheWaterTest::new, 40, PADDING);
        registerSpaced(event, environment, "offshore_pump_floats_on_a_lake",
                OffshorePumpFloatsOnALakeTest::new, 40, PADDING);
        registerSpaced(event, environment, "offshore_pump_pumps_at_factorio_rate",
                OffshorePumpPumpsAtFactorioRateTest::new, 60, PADDING);
        registerSpaced(event, environment, "offshore_pump_fills_a_pipe", OffshorePumpFillsAPipeTest::new, 100, PADDING);
        registerSpaced(event, environment, "offshore_pump_sleeps", OffshorePumpSleepsTest::new, 100, PADDING);
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

    /** Where the offshore pump tests put the shore, the machine, and the water its intake reaches. */
    private static final BlockPos PUMP = new BlockPos(2, 2, 3);
    private static final BlockPos INTAKE_WATER = new BlockPos(2, 1, 2);

    /** Natural water: the still water the world makes, and the only kind an offshore pump draws. */
    private static BlockState naturalWater() {
        return ModBlocks.WATER.get().defaultBlockState();
    }

    /**
     * A stone platform at y 1, so a machine has something to stand on and water something to lie
     * in - and stone under it at y 0, so water set into the platform has a bed and stays where it
     * was put rather than pouring into the space below and turning up, flowing, two blocks under
     * an intake.
     */
    private static void platform(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    /** An offshore pump anchored at {@code anchor}, facing north: its intake is one block north, its outlet the body's south face. */
    private static OffshorePumpBlockEntity offshorePump(GameTestHelper helper, BlockPos anchor) {
        OffshorePumpBlock block = ModBlocks.OFFSHORE_PUMP.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(OffshorePumpBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, OffshorePumpBlockEntity.class);
    }

    /** Takes water off the pump the way a pipe would: through the extract-only outlet view. */
    private static int drawWater(OffshorePumpBlockEntity pump, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = pump.output().extract(FluidResource.of(Fluids.WATER), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /**
     * What the offshore pump would place as if a player clicked the top of {@code ground}, asked
     * exactly the way a right-click asks it. No player, so the facing is north and the intake
     * lands one block north of the body.
     */
    private static @Nullable BlockState pumpPlacement(GameTestHelper helper, BlockPos ground) {
        BlockPos below = helper.absolutePos(ground);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.OFFSHORE_PUMP.get()), hit);
        return ModBlocks.OFFSHORE_PUMP.get().getStateForPlacement(context);
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
            for (String name : new String[] {"steam", "crude_oil", "water", "flowing_water"}) {
                Identifier id = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name);
                helper.assertTrue(BuiltInRegistries.FLUID.getValue(id) != Fluids.EMPTY,
                        "nauvis_fluids:" + name + " is not registered");
            }
            for (String name : new String[] {"pipe", "crude_oil", "pumpjack", "water", "offshore_pump"}) {
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

    /**
     * A pumpjack says what it mined, once per cycle, naming the well.
     *
     * <p>This is the report that finishes Factorio's oil processing - {@code mine-entity:
     * crude-oil, 1} - through Facrafting's {@code MiningListeners} and into research, neither of
     * which this mod names. What can be asserted here is this mod's half: one report per cycle,
     * for this well, of one, whatever the yield. Other pumpjacks in the run report too, so the
     * listener keeps only what came from this test's well.
     */
    public static class PumpjackReportsWhatItMinesTest extends GameTestInstance {

        public static final MapCodec<PumpjackReportsWhatItMinesTest> CODEC =
                RecordCodecBuilder.<PumpjackReportsWhatItMinesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackReportsWhatItMinesTest::info))
                                .apply(i, PumpjackReportsWhatItMinesTest::new));

        public PumpjackReportsWhatItMinesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // The seam to research goes through Facrafting, and with Facrafting present the
            // adapter that forwards reports must be installed - see FacraftingProgress.
            if (ModList.get().isLoaded("facrafting")) {
                helper.assertTrue(OilProgress.installed() > 0,
                        "Facrafting is loaded and nothing forwards what a pumpjack mines to it");
            }

            BlockPos wellPos = helper.absolutePos(WELL);
            List<String> reports = new ArrayList<>();
            OilProgress.add((level, well, resource, cycles) -> {
                if (well.equals(wellPos)) {
                    reports.add(resource + " x" + cycles);
                }
            });

            well(helper, WELL, 4 * CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.runAfterDelay(50, () -> {
                // Two cycles at 400%: forty units each, and still one report of one per cycle.
                helper.assertValueEqual(reports, List.of("nauvis_fluids:crude_oil x1", "nauvis_fluids:crude_oil x1"),
                        "what the pumpjack reported over two cycles");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack reports what it mines");
        }
    }

    /**
     * A cycle produces at most a tankful, however rich the well.
     *
     * <p>Factorio caps a pumpjack's cycle at its fluid box volume, and so does this one - which is
     * also what keeps a pumpjack working at all on a well far from the origin, where the
     * distance factor makes a cycle worth more than the tank holds. Without the cap such a machine
     * finished its first cycle, found the oil would not fit, and said <em>Full</em> over an empty
     * tank for ever. The gametest world is millions of blocks out, which is how this was found.
     */
    public static class PumpjackCapsACycleTest extends GameTestInstance {

        public static final MapCodec<PumpjackCapsACycleTest> CODEC =
                RecordCodecBuilder.<PumpjackCapsACycleTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PumpjackCapsACycleTest::info))
                                .apply(i, PumpjackCapsACycleTest::new));

        /** Two thousand a cycle, uncapped: two hundred times normal. */
        private static final long MONSTER = 200 * CrudeOilBlockEntity.NORMAL;

        public PumpjackCapsACycleTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            CrudeOilBlockEntity well = well(helper, WELL, MONSTER);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(pumpjack.stored(), PumpjackBlockEntity.TANK_CAPACITY,
                        "what one cycle on a monster well banked - a tankful, no more and not nothing");
                helper.assertValueEqual(well.amount(), MONSTER - CrudeOilBlockEntity.DEPLETION,
                        "a capped cycle still takes one cycle off the well");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pumpjack caps a cycle at its tank");
        }
    }

    /**
     * {@code /oil field} puts a field where the command is run, the way worldgen would have.
     *
     * <p>The tool for a superflat world, which runs no features and so has no oil, and for a
     * playtest. It goes through the dispatcher from a source standing on the platform, so what is
     * asserted is the command as typed: that it exists, that it needs no arguments, and that a
     * field of wells is there afterwards on the ground the source stood on.
     */
    public static class OilCommandPlacesAFieldTest extends GameTestInstance {

        public static final MapCodec<OilCommandPlacesAFieldTest> CODEC =
                RecordCodecBuilder.<OilCommandPlacesAFieldTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OilCommandPlacesAFieldTest::info))
                                .apply(i, OilCommandPlacesAFieldTest::new));

        private static final int SIZE = 24;

        public OilCommandPlacesAFieldTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                }
            }
            MinecraftServer server = helper.getLevel().getServer();
            BlockPos standing = helper.absolutePos(new BlockPos(SIZE / 2, 2, SIZE / 2));
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withLevel(helper.getLevel())
                            .withPosition(Vec3.atBottomCenterOf(standing)).withSuppressedOutput(),
                    "oil field");

            int found = 0;
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    if (helper.getBlockState(new BlockPos(x, 1, z)).is(ModBlocks.CRUDE_OIL.get())) {
                        found++;
                    }
                }
            }
            helper.assertTrue(found >= CrudeOilFieldFeature.MIN_WELLS,
                    "/oil field left " + found + " wells in the ground, fewer than a field has");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("oil command places a field");
        }
    }

    // --- natural water --------------------------------------------------------------------

    /**
     * A bucket lifts natural water as water, and pours it back as vanilla's.
     *
     * <p>The rule that makes a lake a place rather than a supply: what you carry away is
     * ordinary water, what you pour out is ordinary water, and neither is what an offshore pump
     * draws from. Water on the move is not lifted at all, exactly as vanilla's is not.
     */
    public static class NaturalWaterIsBucketedAsWaterTest extends GameTestInstance {

        public static final MapCodec<NaturalWaterIsBucketedAsWaterTest> CODEC =
                RecordCodecBuilder.<NaturalWaterIsBucketedAsWaterTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(NaturalWaterIsBucketedAsWaterTest::info))
                                .apply(i, NaturalWaterIsBucketedAsWaterTest::new));

        public NaturalWaterIsBucketedAsWaterTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 5);
            BlockPos at = new BlockPos(2, 2, 2);
            BlockPos absolute = helper.absolutePos(at);
            LiquidBlock water = ModBlocks.WATER.get();

            helper.setBlock(at, naturalWater());
            ItemStack lifted = water.pickupBlock(null, helper.getLevel(), absolute, helper.getBlockState(at));
            helper.assertTrue(lifted.is(Items.WATER_BUCKET),
                    "a bucket of natural water is " + lifted + ", not a water bucket");
            helper.assertTrue(helper.getBlockState(at).isAir(), "the water was lifted and is still there");

            ((BucketItem) Items.WATER_BUCKET).emptyContents(null, helper.getLevel(), absolute, null);
            helper.assertTrue(helper.getBlockState(at).is(Blocks.WATER),
                    "a poured bucket put down " + helper.getBlockState(at) + ", not vanilla's water");

            helper.setBlock(at, naturalWater().setValue(LiquidBlock.LEVEL, 2));
            helper.assertTrue(water.pickupBlock(null, helper.getLevel(), absolute, helper.getBlockState(at)).isEmpty(),
                    "flowing natural water was lifted by a bucket");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("natural water is bucketed as water");
        }
    }

    /**
     * Two natural sources a block apart never make a third; two of vanilla's do.
     *
     * <p>Minecraft's infinite water is this one rule, and it is the rule Factorio does not have:
     * water is where the map put it. Both troughs are built the same and only the water differs,
     * so the vanilla one is the control - if the game rule ever stopped vanilla water converting,
     * the natural trough would pass for the wrong reason and the control would say so.
     */
    public static class NaturalWaterMakesNoNewSourceTest extends GameTestInstance {

        public static final MapCodec<NaturalWaterMakesNoNewSourceTest> CODEC =
                RecordCodecBuilder.<NaturalWaterMakesNoNewSourceTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(NaturalWaterMakesNoNewSourceTest::info))
                                .apply(i, NaturalWaterMakesNoNewSourceTest::new));

        private static final int NATURAL_ROW = 1;
        private static final int VANILLA_ROW = 5;

        public NaturalWaterMakesNoNewSourceTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            trough(helper, NATURAL_ROW, naturalWater());
            trough(helper, VANILLA_ROW, Blocks.WATER.defaultBlockState());

            helper.runAfterDelay(40, () -> {
                FluidState natural = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 2, NATURAL_ROW)));
                helper.assertTrue(natural.getType() == ModFluids.FLOWING_WATER.get(),
                        "the gap between two natural sources holds " + natural.getType() + ", not flowing natural water");
                helper.assertFalse(natural.isSource(),
                        "natural water made a new source, which is the one thing it must never do");

                FluidState vanilla = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 2, VANILLA_ROW)));
                helper.assertTrue(vanilla.isSource() && vanilla.getType() == Fluids.WATER,
                        "vanilla water no longer makes a source between two, so the comparison proves nothing");
                helper.succeed();
            });
        }

        /** A stone channel three long at y 2, a source at each end and air between. */
        private static void trough(GameTestHelper helper, int row, BlockState water) {
            for (int x = 0; x < 5; x++) {
                for (int z = row - 1; z <= row + 1; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                }
            }
            helper.setBlock(new BlockPos(2, 2, row), Blocks.AIR);
            helper.setBlock(new BlockPos(1, 2, row), water);
            helper.setBlock(new BlockPos(3, 2, row), water);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("natural water makes no new source");
        }
    }

    /**
     * Worldgen's water becomes natural water, level for level, and nothing else is touched.
     *
     * <p>The feature is handed a live chunk rather than a generating one - the sections are the
     * same objects - holding what a sea floor holds: still water, a flow, a fall, a waterlogged
     * block and ice. The three waters change block and keep their level; the slab keeps the
     * vanilla water inside it, because that water is the slab's and not the world's; the ice
     * stays ice.
     */
    public static class WorldgenWaterBecomesNaturalTest extends GameTestInstance {

        public static final MapCodec<WorldgenWaterBecomesNaturalTest> CODEC =
                RecordCodecBuilder.<WorldgenWaterBecomesNaturalTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(WorldgenWaterBecomesNaturalTest::info))
                                .apply(i, WorldgenWaterBecomesNaturalTest::new));

        private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
        private static final BlockPos FLOW = new BlockPos(2, 2, 1);
        private static final BlockPos FALL = new BlockPos(3, 2, 1);
        private static final BlockPos SLAB = new BlockPos(1, 2, 3);
        private static final BlockPos ICE = new BlockPos(2, 2, 3);

        public WorldgenWaterBecomesNaturalTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // A basin: a floor, a ring of stone at water level, and the contents.
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    if (x == 0 || x == 4 || z == 0 || z == 4) {
                        helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    }
                }
            }
            helper.setBlock(SOURCE, Blocks.WATER.defaultBlockState());
            helper.setBlock(FLOW, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
            helper.setBlock(FALL, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
            helper.setBlock(SLAB, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            helper.setBlock(ICE, Blocks.ICE.defaultBlockState());

            // The basin may straddle a chunk border; every chunk it touches gets the treatment,
            // as every chunk does in a generating world.
            Set<ChunkAccess> chunks = new HashSet<>();
            for (BlockPos pos : List.of(SOURCE, FLOW, FALL, SLAB, ICE)) {
                chunks.add(helper.getLevel().getChunk(helper.absolutePos(pos)));
            }
            int replaced = 0;
            for (ChunkAccess chunk : chunks) {
                replaced += NaturalWaterFeature.replace(chunk);
            }
            helper.assertValueEqual(replaced, 3, "water blocks the feature reported replacing");

            LiquidBlock natural = ModBlocks.WATER.get();
            helper.assertTrue(helper.getBlockState(SOURCE).is(natural), "a source stayed " + helper.getBlockState(SOURCE));
            helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(LiquidBlock.LEVEL), 0, "a source's level");
            helper.assertTrue(helper.getBlockState(FLOW).is(natural), "a flow stayed " + helper.getBlockState(FLOW));
            helper.assertValueEqual(helper.getBlockState(FLOW).getValue(LiquidBlock.LEVEL), 3, "a flow's level");
            helper.assertTrue(helper.getBlockState(FALL).is(natural), "a fall stayed " + helper.getBlockState(FALL));
            helper.assertValueEqual(helper.getBlockState(FALL).getValue(LiquidBlock.LEVEL), 8, "a fall's level");
            helper.assertTrue(helper.getBlockState(SLAB).is(Blocks.OAK_SLAB)
                            && helper.getBlockState(SLAB).getValue(BlockStateProperties.WATERLOGGED),
                    "a waterlogged slab became " + helper.getBlockState(SLAB));
            helper.assertTrue(helper.getBlockState(ICE).is(Blocks.ICE), "ice became " + helper.getBlockState(ICE));
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("worldgen water becomes natural");
        }
    }

    // --- the offshore pump ----------------------------------------------------------------

    /**
     * An offshore pump stands where its intake finds still natural water, and nowhere else.
     *
     * <p>Not on dry land, not at a bucket's water, and not at the flowing edge of a lake - the
     * three ways a player would otherwise get infinite water back. Under the intake counts, and
     * so does beside it: a pump on a beach reaches down, a pump in the shallows reaches sideways.
     */
    public static class OffshorePumpStandsOnlyAtWaterTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpStandsOnlyAtWaterTest> CODEC =
                RecordCodecBuilder.<OffshorePumpStandsOnlyAtWaterTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpStandsOnlyAtWaterTest::info))
                                .apply(i, OffshorePumpStandsOnlyAtWaterTest::new));

        public OffshorePumpStandsOnlyAtWaterTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 6);
            BlockPos shore = PUMP.below();
            BlockPos beside = INTAKE_WATER.above().west();

            helper.assertTrue(pumpPlacement(helper, shore) == null, "an offshore pump stands on dry land");

            helper.setBlock(INTAKE_WATER, naturalWater());
            BlockState placed = pumpPlacement(helper, shore);
            helper.assertTrue(placed != null, "an offshore pump refuses a shore with natural water ahead of it");
            helper.assertValueEqual(placed.getValue(OffshorePumpBlock.FACING), Direction.NORTH,
                    "the way a pump placed with no player faces");

            helper.setBlock(INTAKE_WATER, Blocks.WATER.defaultBlockState());
            helper.assertTrue(pumpPlacement(helper, shore) == null,
                    "an offshore pump accepts a bucket's water, so water is infinite again");
            helper.assertValueEqual(OffshorePumpBlock.bestIntake(helper.getLevel(), helper.absolutePos(PUMP)),
                    OffshorePumpBlock.Intake.OTHER, "what the refusal says of a bucket's water");

            helper.setBlock(INTAKE_WATER, naturalWater().setValue(LiquidBlock.LEVEL, 3));
            helper.assertTrue(pumpPlacement(helper, shore) == null, "an offshore pump accepts water on the move");

            helper.setBlock(INTAKE_WATER, Blocks.STONE);
            helper.assertValueEqual(OffshorePumpBlock.bestIntake(helper.getLevel(), helper.absolutePos(PUMP)),
                    OffshorePumpBlock.Intake.NONE, "what the refusal says of dry land");

            helper.setBlock(beside, naturalWater());
            helper.assertTrue(pumpPlacement(helper, shore) != null,
                    "an offshore pump refuses natural water beside its intake");
            helper.setBlock(beside, Blocks.AIR);

            // A bank a block above the water: the intake reaches two down.
            helper.setBlock(INTAKE_WATER.below(), naturalWater());
            helper.assertTrue(pumpPlacement(helper, shore) != null,
                    "an offshore pump on a bank one block above the water refuses to stand there");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("offshore pump stands only at water");
        }
    }

    /**
     * Forty water a tick, for nothing.
     *
     * <p>Factorio's pump gives twenty boilers' worth and needs no power, and both are asserted:
     * the tank rises by exactly two ticks' worth between two readings taken two ticks apart,
     * from a machine that has been given no electricity and no fuel because it has nowhere to
     * put either.
     */
    public static class OffshorePumpPumpsAtFactorioRateTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpPumpsAtFactorioRateTest> CODEC =
                RecordCodecBuilder.<OffshorePumpPumpsAtFactorioRateTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpPumpsAtFactorioRateTest::info))
                                .apply(i, OffshorePumpPumpsAtFactorioRateTest::new));

        private int firstReading;

        public OffshorePumpPumpsAtFactorioRateTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 5);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);

            helper.startSequence()
                    .thenExecuteAfter(2, () -> firstReading = pump.stored())
                    .thenExecuteAfter(2, () -> {
                        helper.assertValueEqual(pump.stored() - firstReading, 2 * OffshorePumpBlockEntity.WATER_PER_TICK,
                                "water banked over two ticks");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.PUMPING, "status while pumping");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("offshore pump pumps at Factorio's rate");
        }
    }

    /**
     * What comes out of the back is water - vanilla's, the water of the pipes - and only the back
     * offers it.
     *
     * <p>A pipe at the outlet reaches into the machine and its run fills with
     * {@code minecraft:water} until run and tank are both full and the pump reports so. A pipe on
     * the flank connects to nothing and carries nothing.
     */
    public static class OffshorePumpFillsAPipeTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpFillsAPipeTest> CODEC =
                RecordCodecBuilder.<OffshorePumpFillsAPipeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpFillsAPipeTest::info))
                                .apply(i, OffshorePumpFillsAPipeTest::new));

        public OffshorePumpFillsAPipeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 5);
            BlockPos outletPipe = PUMP.south();
            BlockPos flankPipe = PUMP.east();

            // Pipes first, for the reason the pumpjack test gives: a block put down by anything
            // but a player never runs getStateForPlacement, so a pipe placed beside a machine
            // already there would show no connection.
            pipe(helper, outletPipe);
            pipe(helper, flankPipe);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(helper.getBlockState(outletPipe).getValue(PipeBlock.NORTH),
                                "a pipe at the outlet does not reach into the offshore pump");
                        helper.assertFalse(helper.getBlockState(flankPipe).getValue(PipeBlock.WEST),
                                "a pipe on the flank connects to an offshore pump, so the outlet means nothing");
                    })
                    .thenExecuteAfter(40, () -> {
                        FluidNetwork run = networkAt(helper, outletPipe, "the outlet pipe has no run");
                        helper.assertValueEqual(run.fluid().getFluid(), Fluids.WATER,
                                "what the outlet pipe is carrying");
                        helper.assertValueEqual(run.amount(), run.capacity(), "a run fed by an offshore pump fills up");
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY,
                                "the tank behind a full run");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.OUTPUT_FULL,
                                "status with a full tank and a full run");
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
            return Component.literal("offshore pump fills a pipe");
        }
    }

    /**
     * A full offshore pump asks for no ticks; a draw wakes it; the water going, or turning out to
     * be a bucket's, stops it again; the lake coming back restarts it.
     *
     * <p>Non-negotiable #5, asserted through {@code hasScheduledTick} for each of the three
     * reasons the machine can stop and the two ways it can be woken. Delete the wake in
     * {@code FluidOutputAccess} or the one in {@code neighborChanged} and one of these lines goes red.
     */
    public static class OffshorePumpSleepsTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpSleepsTest> CODEC =
                RecordCodecBuilder.<OffshorePumpSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpSleepsTest::info))
                                .apply(i, OffshorePumpSleepsTest::new));

        public OffshorePumpSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 5);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);
            Block block = ModBlocks.OFFSHORE_PUMP.get();

            helper.startSequence()
                    .thenExecuteAfter(15, () -> {
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY, "a tank left alone");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.OUTPUT_FULL, "status when full");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "a full offshore pump is still asking for ticks");
                        drawWater(pump, 50);
                        helper.assertTrue(isScheduled(helper, PUMP, block), "drawing from a full offshore pump did not wake it");
                    })
                    .thenExecuteAfter(10, () -> {
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY, "the tank after a draw");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "a refilled offshore pump is still asking for ticks");
                        helper.setBlock(INTAKE_WATER, Blocks.STONE);
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.NO_WATER, "status with the lake gone");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "an offshore pump with no water is still asking for ticks");
                        helper.setBlock(INTAKE_WATER, Blocks.WATER.defaultBlockState());
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.WRONG_WATER, "status at a bucket's water");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "an offshore pump at the wrong water is still asking for ticks");
                        drawWater(pump, OffshorePumpBlockEntity.TANK_CAPACITY);
                        helper.setBlock(INTAKE_WATER, naturalWater());
                    })
                    .thenExecuteAfter(3, () -> {
                        helper.assertTrue(pump.stored() > 0, "the lake coming back did not restart the pump");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.PUMPING, "status with the lake back");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("offshore pump sleeps");
        }
    }

    /**
     * A pump turns to the water. The click faces north and the lake is to the east, and the pump
     * placed faces east, intake over the lake.
     *
     * <p>Factorio's ghost snaps to the shoreline; this is the nearest a block can come. The
     * player's own facing is tried first, so a pump that could face the way they look does, and
     * only one that could not turns.
     */
    public static class OffshorePumpTurnsToTheWaterTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpTurnsToTheWaterTest> CODEC =
                RecordCodecBuilder.<OffshorePumpTurnsToTheWaterTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpTurnsToTheWaterTest::info))
                                .apply(i, OffshorePumpTurnsToTheWaterTest::new));

        public OffshorePumpTurnsToTheWaterTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 6);
            BlockPos shore = PUMP.below();
            // Under where an east-facing intake would hang, and nowhere a north-facing one reaches.
            helper.setBlock(PUMP.east().below(), naturalWater());

            BlockState placed = pumpPlacement(helper, shore);
            helper.assertTrue(placed != null, "an offshore pump facing away from a lake beside it will not turn to it");
            helper.assertValueEqual(placed.getValue(OffshorePumpBlock.FACING), Direction.EAST,
                    "the way a pump clicked facing north turns when the water is to the east");

            // Water the way the player faces wins over water beside, so a pump faces as placed
            // whenever it can.
            helper.setBlock(INTAKE_WATER, naturalWater());
            BlockState straight = pumpPlacement(helper, shore);
            helper.assertTrue(straight != null, "a pump with water ahead of it will not stand");
            helper.assertValueEqual(straight.getValue(OffshorePumpBlock.FACING), Direction.NORTH,
                    "the way a pump faces when the water is where the player looks");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("offshore pump turns to the water");
        }
    }

    /**
     * A click on a lake puts the pump on the lake, not under it.
     *
     * <p>A block in hand looks through water, so the click lands on the bed and vanilla would
     * place just above it, on the bottom. The pump's item lifts the placement to the air over the
     * surface, and the block then finds the water under its intake and stands.
     */
    public static class OffshorePumpFloatsOnALakeTest extends GameTestInstance {

        public static final MapCodec<OffshorePumpFloatsOnALakeTest> CODEC =
                RecordCodecBuilder.<OffshorePumpFloatsOnALakeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OffshorePumpFloatsOnALakeTest::info))
                                .apply(i, OffshorePumpFloatsOnALakeTest::new));

        private static final BlockPos BED = new BlockPos(2, 0, 2);
        private static final int DEPTH = 3;

        public OffshorePumpFloatsOnALakeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // A pool three deep and three across, on a stone bed.
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y <= DEPTH; y++) {
                        helper.setBlock(new BlockPos(x, y, z), naturalWater());
                    }
                }
            }

            BlockPos bed = helper.absolutePos(BED);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false);
            BlockPlaceContext clicked = new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.OFFSHORE_PUMP.get()), hit);
            helper.assertValueEqual(clicked.getClickedPos(), bed.above(),
                    "where vanilla would place a block clicked onto a lake bed");

            BlockPlaceContext lifted = ModItems.OFFSHORE_PUMP.get().updatePlacementContext(clicked);
            helper.assertTrue(lifted != null, "the pump's item refused the click altogether");
            helper.assertValueEqual(lifted.getClickedPos(), bed.above(DEPTH + 1),
                    "where the pump's item lifts a click on a lake bed to");

            BlockState placed = ModBlocks.OFFSHORE_PUMP.get().getStateForPlacement(lifted);
            helper.assertTrue(placed != null, "a pump lifted to the surface of a lake will not stand there");
            helper.assertValueEqual(placed.getValue(OffshorePumpShape.SHAPE.part()), OffshorePumpShape.BODY_CELL,
                    "the cell that lands on the lifted click");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("offshore pump floats on a lake");
        }
    }
}
