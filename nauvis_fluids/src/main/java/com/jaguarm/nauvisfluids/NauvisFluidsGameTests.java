package com.jaguarm.nauvisfluids;

import java.util.List;

import com.jaguarm.nauvisfluids.pipe.FluidNetwork;
import com.jaguarm.nauvisfluids.pipe.FluidNetworkManager;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests that run inside a real server, headless.
 *
 * <p>What is asserted here is the <em>graph</em>: that a line of pipes is one object, that it
 * splits and merges when the line does, and that what was in it is divided rather than duplicated
 * or lost. Whether steam actually reaches an engine through a pipe is asserted in the pack mod,
 * because it takes a boiler and an engine and this mod may not compile against the one that owns
 * them.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class NauvisFluidsGameTests {

    private NauvisFluidsGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisFluids.MODID);

    static {
        TEST_TYPES.register("pipe_run_is_one_object", () -> PipeRunIsOneObjectTest.CODEC);
        TEST_TYPES.register("pipe_run_splits_and_merges", () -> PipeRunSplitsAndMergesTest.CODEC);
        TEST_TYPES.register("pipe_connects_to_what_offers_fluid", () -> PipeConnectsTest.CODEC);
        TEST_TYPES.register("steam_is_registered", () -> SteamIsRegisteredTest.CODEC);
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
        register(event, environment, "steam_is_registered", SteamIsRegisteredTest::new, 20);
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
                helper.assertTrue(
                        state.getValue(com.jaguarm.nauvisfluids.pipe.PipeBlock.EAST),
                        "a pipe does not reach towards the pipe beside it");
                helper.assertFalse(
                        state.getValue(com.jaguarm.nauvisfluids.pipe.PipeBlock.WEST),
                        "a pipe reaches towards a stone block, which offers it nothing");
                helper.assertFalse(
                        state.getValue(com.jaguarm.nauvisfluids.pipe.PipeBlock.UP),
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
     * Steam exists under the id the mapping has always given it.
     *
     * <p>{@code nauvis_fluids:steam} is identity: {@code data/mapping.json} names it, and
     * {@code nauvis_power} finds it by that id rather than by importing it, so a rename here would
     * quietly stop every boiler in the pack from making anything.
     */
    public static class SteamIsRegisteredTest extends GameTestInstance {

        public static final MapCodec<SteamIsRegisteredTest> CODEC =
                RecordCodecBuilder.<SteamIsRegisteredTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamIsRegisteredTest::info))
                                .apply(i, SteamIsRegisteredTest::new));

        public SteamIsRegisteredTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Identifier id = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "steam");
            helper.assertTrue(BuiltInRegistries.FLUID.getValue(id) != net.minecraft.world.level.material.Fluids.EMPTY,
                    "nauvis_fluids:steam is not registered, so every boiler in the pack is inert");

            Block pipe = BuiltInRegistries.BLOCK.getValue(
                    Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "pipe"));
            helper.assertTrue(pipe != Blocks.AIR, "nauvis_fluids:pipe is not registered");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam is registered");
        }
    }
}
