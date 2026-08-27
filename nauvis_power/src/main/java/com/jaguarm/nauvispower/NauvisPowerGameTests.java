package com.jaguarm.nauvispower;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.grid.PowerNetwork;
import com.jaguarm.nauvispower.grid.PowerNetworkManager;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless.
 *
 * <p>The one that matters most is {@code power_chain_sleeps}. Making energy is easy to check and
 * hard to get wrong; <em>stopping</em> is neither. Non-negotiable #5 across a chain means an engine
 * with a full buffer stops drawing steam, which lets the boiler's buffer fill, which stops it
 * burning coal - three blocks that each have to decide to do nothing, in order, from the far end.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class NauvisPowerGameTests {

    private NauvisPowerGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final BlockPos BOILER = new BlockPos(0, 1, 0);
    private static final BlockPos ENGINE = new BlockPos(1, 1, 0);

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisPower.MODID);

    static {
        TEST_TYPES.register("boiler_makes_steam", () -> BoilerMakesSteamTest.CODEC);
        TEST_TYPES.register("steam_engine_makes_power", () -> SteamEngineMakesPowerTest.CODEC);
        TEST_TYPES.register("power_chain_sleeps", () -> PowerChainSleepsTest.CODEC);
        TEST_TYPES.register("steam_engine_takes_no_power", () -> SteamEngineTakesNoPowerTest.CODEC);
        TEST_TYPES.register("pole_network_merges_and_splits", () -> PoleNetworkMergesAndSplitsTest.CODEC);
        TEST_TYPES.register("pole_finds_a_machine", () -> PoleFindsAMachineTest.CODEC);
        TEST_TYPES.register("pole_finds_a_later_machine", () -> PoleFindsALaterMachineTest.CODEC);
        TEST_TYPES.register("power_network_sleeps", () -> PowerNetworkSleepsTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "boiler_makes_steam", BoilerMakesSteamTest::new, 100);
        register(event, environment, "steam_engine_makes_power", SteamEngineMakesPowerTest::new, 100);
        register(event, environment, "power_chain_sleeps", PowerChainSleepsTest::new, 400);
        register(event, environment, "steam_engine_takes_no_power", SteamEngineTakesNoPowerTest::new, 60);
        registerSpaced(event, environment, "pole_network_merges_and_splits",
                PoleNetworkMergesAndSplitsTest::new, 200);
        registerSpaced(event, environment, "pole_finds_a_machine", PoleFindsAMachineTest::new, 100);
        registerSpaced(event, environment, "pole_finds_a_later_machine",
                PoleFindsALaterMachineTest::new, 200);
        registerSpaced(event, environment, "power_network_sleeps", PowerNetworkSleepsTest::new, 200);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE)));
    }

    /**
     * The same, with room around it.
     *
     * <p>A grid test builds outside the structure it was given - the empty structure is a point,
     * and a wire reaches 7.5 blocks. Without padding the poles of one test would be inside the
     * wire reach of the next one along, and the two would merge into a network neither expected.
     */
    private static void registerSpaced(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, 24)));
    }

    /** A boiler with coal in it, and an engine touching it. */
    private static void buildChain(GameTestHelper helper, boolean fuelled) {
        helper.setBlock(BOILER, ModBlocks.BOILER.get());
        helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get());
        if (fuelled) {
            insert(helper.getBlockEntity(BOILER, BoilerBlockEntity.class).fuelAccess(), Items.COAL, 1);
        }
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    private static PowerNetworkManager grid(GameTestHelper helper) {
        return PowerNetworkManager.of(helper.getLevel());
    }

    /** The network the pole at a test-relative position belongs to, or null if it has none. */
    private static @Nullable PowerNetwork networkAt(GameTestHelper helper, BlockPos pos) {
        return grid(helper).networkAt(helper.absolutePos(pos));
    }

    private static PowerNetwork requireNetwork(GameTestHelper helper, BlockPos pos, String what) {
        PowerNetwork network = networkAt(helper, pos);
        helper.assertTrue(network != null, what);
        return network;
    }

    /** Coal in, steam out. */
    public static class BoilerMakesSteamTest extends GameTestInstance {

        public static final MapCodec<BoilerMakesSteamTest> CODEC =
                RecordCodecBuilder.<BoilerMakesSteamTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BoilerMakesSteamTest::info))
                                .apply(i, BoilerMakesSteamTest::new));

        public BoilerMakesSteamTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            helper.assertValueEqual(boiler.steam(), 0, "steam in a cold boiler");
            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.COAL, 1), 1, "coal accepted");

            helper.runAfterDelay(20, () -> {
                BoilerBlockEntity fired = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
                helper.assertTrue(fired.steam() > 0, "a boiler with coal in it made no steam");
                helper.assertTrue(fired.burnTime() > 0, "it made steam without burning anything");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("boiler makes steam");
        }
    }

    /** Steam in, electricity out - the first energy this pack has ever made. */
    public static class SteamEngineMakesPowerTest extends GameTestInstance {

        public static final MapCodec<SteamEngineMakesPowerTest> CODEC =
                RecordCodecBuilder.<SteamEngineMakesPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineMakesPowerTest::info))
                                .apply(i, SteamEngineMakesPowerTest::new));

        public SteamEngineMakesPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);

            helper.runAfterDelay(20, () -> {
                SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);
                helper.assertTrue(engine.energyStored() > 0,
                        "a steam engine beside a burning boiler stored no energy");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine makes power");
        }
    }

    /**
     * <b>Non-negotiable #5, across three blocks.</b>
     *
     * <p>Nobody is drawing, so the engine fills and stops. Having stopped it draws no more steam,
     * so the boiler fills and stops too, and stops burning coal. Each of those is a separate
     * decision made at a different end of the chain, and a mistake in any one of them looks exactly
     * like a factory that works - right up until there are a thousand of them.
     */
    public static class PowerChainSleepsTest extends GameTestInstance {

        public static final MapCodec<PowerChainSleepsTest> CODEC =
                RecordCodecBuilder.<PowerChainSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerChainSleepsTest::info))
                                .apply(i, PowerChainSleepsTest::new));

        public PowerChainSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);

            // The engine fills in 100 ticks and the boiler in rather less once it stops being
            // drained. 250 leaves room for both and for the tick each spends discovering it.
            helper.runAfterDelay(250, () -> {
                SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);
                BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);

                helper.assertValueEqual(engine.energyStored(), SteamEngineBlockEntity.ENERGY_CAPACITY,
                        "the engine's charge");
                helper.assertValueEqual(boiler.steam(), BoilerBlockEntity.STEAM_CAPACITY,
                        "the boiler's steam");

                helper.assertFalse(isScheduled(helper, ENGINE, ModBlocks.STEAM_ENGINE.get()),
                        "a full steam engine is still scheduled to tick");
                helper.assertFalse(isScheduled(helper, BOILER, ModBlocks.BOILER.get()),
                        "a boiler nobody is drawing from is still scheduled to tick, so it is still "
                                + "burning coal into a buffer that cannot take it");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power chain sleeps");
        }
    }

    /** A generator is not a battery: the grid cannot push energy back into it. */
    public static class SteamEngineTakesNoPowerTest extends GameTestInstance {

        public static final MapCodec<SteamEngineTakesNoPowerTest> CODEC =
                RecordCodecBuilder.<SteamEngineTakesNoPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineTakesNoPowerTest::info))
                                .apply(i, SteamEngineTakesNoPowerTest::new));

        public SteamEngineTakesNoPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get());
            SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(engine.cableView().insert(1000, transaction), 0,
                        "energy pushed into a generator");
                transaction.commit();
            }
            helper.assertValueEqual(engine.energyStored(), 0, "charge in an engine nobody fuelled");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine takes no power");
        }
    }

    /**
     * <b>The graph, and the two operations that are expensive to get wrong.</b>
     *
     * <p>Three poles in a vertical line, four blocks apart. The outer two are eight apart, which
     * is past the 7.5 wire reach, so they are only ever connected through the middle one. Taking
     * the middle one out has to split one network into two, and putting it back has to merge them
     * again - and a merge that quietly leaves two objects behind, or a split that never happens,
     * both look exactly like a working grid until something asks which network a machine is on.
     */
    public static class PoleNetworkMergesAndSplitsTest extends GameTestInstance {

        public static final MapCodec<PoleNetworkMergesAndSplitsTest> CODEC =
                RecordCodecBuilder.<PoleNetworkMergesAndSplitsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleNetworkMergesAndSplitsTest::info))
                                .apply(i, PoleNetworkMergesAndSplitsTest::new));

        private static final BlockPos LOWER = new BlockPos(0, 1, 0);
        private static final BlockPos MIDDLE = new BlockPos(0, 5, 0);
        private static final BlockPos UPPER = new BlockPos(0, 9, 0);

        public PoleNetworkMergesAndSplitsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(LOWER, ModBlocks.SMALL_ELECTRIC_POLE.get());
            helper.setBlock(UPPER, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole has no network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole has no network");
                        helper.assertFalse(lower == upper,
                                "two poles eight blocks apart joined one network, so wire reach is "
                                        + "not being measured");
                        helper.assertValueEqual(lower.poleCount(), 1, "poles in the lower network");
                    })
                    .thenExecute(() -> helper.setBlock(MIDDLE, ModBlocks.SMALL_ELECTRIC_POLE.get()))
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole lost its network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole lost its network");
                        helper.assertTrue(lower == upper,
                                "a pole bridging two networks did not merge them");
                        helper.assertValueEqual(lower.poleCount(), 3, "poles in the merged network");
                    })
                    .thenExecute(() -> helper.setBlock(MIDDLE, Blocks.AIR))
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole lost its network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole lost its network");
                        helper.assertFalse(lower == upper,
                                "breaking the only pole joining two halves left them on one network");
                        helper.assertValueEqual(lower.poleCount(), 1, "poles in the lower half");
                        helper.assertValueEqual(upper.poleCount(), 1, "poles in the upper half");
                        helper.assertTrue(networkAt(helper, MIDDLE) == null,
                                "a pole that no longer exists is still in the index");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole network merges and splits");
        }
    }

    /** A pole finds the machines already standing in its supply area when it loads. */
    public static class PoleFindsAMachineTest extends GameTestInstance {

        public static final MapCodec<PoleFindsAMachineTest> CODEC =
                RecordCodecBuilder.<PoleFindsAMachineTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleFindsAMachineTest::info))
                                .apply(i, PoleFindsAMachineTest::new));

        private static final BlockPos POLE = new BlockPos(3, 1, 0);
        private static final BlockPos OUT_OF_RANGE = new BlockPos(0, 5, 0);

        public PoleFindsAMachineTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // The engine is two blocks from the pole, inside the 5x5 supply area. The second one
            // is four away, outside it - a supply area that was really the wire reach in disguise
            // would pick up both, and nothing else would notice.
            helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get());
            helper.setBlock(OUT_OF_RANGE, ModBlocks.STEAM_ENGINE.get());
            helper.setBlock(POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.endpointCount(), 1,
                        "machines a pole found in its supply area");
                helper.assertTrue(network.hasEndpoint(helper.absolutePos(ENGINE).asLong()),
                        "the machine two blocks from the pole is not the one it found");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole finds a machine");
        }
    }

    /**
     * A machine built <em>after</em> the pole is found too.
     *
     * <p>This is the case with no obvious hook. The machine is two blocks away, so it is in
     * nobody's neighbourhood; it lives in another mod, so it cannot announce itself without that
     * mod learning what a pole is; and a pole that went looking on a schedule would be the
     * per-tick scan the whole design exists to avoid. Deleting the {@code NeighborNotifyEvent}
     * subscription in {@code PowerGridEvents} fails this test and nothing else.
     */
    public static class PoleFindsALaterMachineTest extends GameTestInstance {

        public static final MapCodec<PoleFindsALaterMachineTest> CODEC =
                RecordCodecBuilder.<PoleFindsALaterMachineTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleFindsALaterMachineTest::info))
                                .apply(i, PoleFindsALaterMachineTest::new));

        private static final BlockPos POLE = new BlockPos(3, 1, 0);

        public PoleFindsALaterMachineTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(10, () -> helper.assertValueEqual(
                            requireNetwork(helper, POLE, "the pole has no network").endpointCount(),
                            0,
                            "machines found beside a pole standing on its own"))
                    .thenExecute(() -> helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get()))
                    .thenExecuteAfter(10, () -> helper.assertValueEqual(
                            requireNetwork(helper, POLE, "the pole lost its network").endpointCount(),
                            1,
                            "machines found after one was built two blocks from the pole"))
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole finds a later machine");
        }
    }

    /**
     * <b>Non-negotiable #5, for a graph.</b>
     *
     * <p>A boiler, an engine and a pole, and nothing that wants electricity. The engine fills and
     * stops, the boiler fills and stops - {@code power_chain_sleeps} covers that - and the network
     * has to make the same decision: there is energy to move and nowhere to move it, so it drops
     * out of the set that is visited twenty times a second.
     *
     * <p>This is the assertion that a network of ten thousand poles is free while the factory is
     * idle, and it is the one a design that ticks every pole cannot make at all.
     */
    public static class PowerNetworkSleepsTest extends GameTestInstance {

        public static final MapCodec<PowerNetworkSleepsTest> CODEC =
                RecordCodecBuilder.<PowerNetworkSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerNetworkSleepsTest::info))
                                .apply(i, PowerNetworkSleepsTest::new));

        private static final BlockPos POLE = new BlockPos(2, 1, 0);

        public PowerNetworkSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);
            helper.setBlock(POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(150, () -> {
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.endpointCount(), 1, "machines on the network");
                helper.assertValueEqual(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored(),
                        SteamEngineBlockEntity.ENERGY_CAPACITY,
                        "the engine's charge");
                helper.assertFalse(grid(helper).isActive(network),
                        "a network with a full generator and nothing to spend it on is still being "
                                + "ticked every tick");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power network sleeps");
        }
    }
}
