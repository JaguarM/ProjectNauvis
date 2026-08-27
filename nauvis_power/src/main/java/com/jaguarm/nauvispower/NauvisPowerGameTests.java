package com.jaguarm.nauvispower;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.BoilerMenu;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvispower.grid.PolePart;
import com.jaguarm.nauvispower.grid.PowerNetwork;
import com.jaguarm.nauvispower.grid.PowerNetworkManager;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlockEntity;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
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

    /**
     * Where the chain stands, now that a boiler is seven blocks and an engine is seventeen.
     *
     * <p>Both are anchored on the block that holds their block entity, and both face north. A
     * boiler's steam leaves the back of the block under its chimney; an engine takes steam at the
     * open end of its spine, which is two tiles from its middle, so the engine that a boiler at
     * the origin can feed is anchored three blocks behind it. Every one of those numbers comes off
     * {@link BoilerShape} and {@link SteamEngineShape} - which is the point of them being there -
     * and none of them is a guess.
     */
    private static final BlockPos BOILER = new BlockPos(0, 1, 0);
    private static final BlockPos ENGINE = new BlockPos(0, 1, 3);

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisPower.MODID);

    static {
        TEST_TYPES.register("boiler_turns_as_one", () -> BoilerTurnsAsOneTest.CODEC);
        TEST_TYPES.register("boiler_makes_steam", () -> BoilerMakesSteamTest.CODEC);
        TEST_TYPES.register("steam_engine_makes_power", () -> SteamEngineMakesPowerTest.CODEC);
        TEST_TYPES.register("power_chain_sleeps", () -> PowerChainSleepsTest.CODEC);
        TEST_TYPES.register("steam_engine_takes_no_power", () -> SteamEngineTakesNoPowerTest.CODEC);
        TEST_TYPES.register("pole_network_merges_and_splits", () -> PoleNetworkMergesAndSplitsTest.CODEC);
        TEST_TYPES.register("pole_finds_a_machine", () -> PoleFindsAMachineTest.CODEC);
        TEST_TYPES.register("pole_finds_a_later_machine", () -> PoleFindsALaterMachineTest.CODEC);
        TEST_TYPES.register("power_network_sleeps", () -> PowerNetworkSleepsTest.CODEC);
        TEST_TYPES.register("pole_stands_four_blocks_tall", () -> PoleStandsFourBlocksTallTest.CODEC);
        TEST_TYPES.register("pole_breaks_as_one", () -> PoleBreaksAsOneTest.CODEC);
        TEST_TYPES.register("pole_needs_headroom", () -> PoleNeedsHeadroomTest.CODEC);
        TEST_TYPES.register("pole_wires_link_up", () -> PoleWiresLinkUpTest.CODEC);
        TEST_TYPES.register("pole_wire_bounds_reach_both_ends", () -> PoleWireBoundsTest.CODEC);
        TEST_TYPES.register("boiler_opens_a_screen", () -> BoilerOpensAScreenTest.CODEC);
        TEST_TYPES.register("boiler_refuses_what_will_not_burn", () -> BoilerRefusesNonFuelTest.CODEC);
        TEST_TYPES.register("steam_engines_chain", () -> SteamEnginesChainTest.CODEC);
        TEST_TYPES.register("steam_engine_ignores_its_sides", () -> SteamEngineIgnoresSidesTest.CODEC);
        TEST_TYPES.register("steam_engine_connects_on_two_faces", () -> SteamEngineFacesTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "boiler_turns_as_one", BoilerTurnsAsOneTest::new, 40);
        register(event, environment, "engine_breaks_as_one", EngineBreaksAsOneTest::new, 40);
        register(event, environment, "power_machines_tile_walkably", PowerMachinesTileWalkablyTest::new, 40);
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
        registerSpaced(event, environment, "pole_stands_four_blocks_tall",
                PoleStandsFourBlocksTallTest::new, 100);
        registerSpaced(event, environment, "pole_breaks_as_one", PoleBreaksAsOneTest::new, 200);
        registerSpaced(event, environment, "pole_needs_headroom", PoleNeedsHeadroomTest::new, 100);
        registerSpaced(event, environment, "pole_wires_link_up", PoleWiresLinkUpTest::new, 200);
        registerSpaced(event, environment, "pole_wire_bounds_reach_both_ends", PoleWireBoundsTest::new, 100);
        register(event, environment, "boiler_opens_a_screen", BoilerOpensAScreenTest::new, 60);
        register(event, environment, "boiler_refuses_what_will_not_burn", BoilerRefusesNonFuelTest::new, 60);
        register(event, environment, "steam_engines_chain", SteamEnginesChainTest::new, 200);
        register(event, environment, "steam_engine_ignores_its_sides", SteamEngineIgnoresSidesTest::new, 100);
        register(event, environment, "steam_engine_connects_on_two_faces", SteamEngineFacesTest::new, 60);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    /**
     * How much empty world to leave around each test.
     *
     * <p>A grid test builds outside the structure it was given - the empty structure is a point -
     * and the things built here are no longer one block each. A steam engine is five tiles long,
     * a chain of two reaches ten blocks from the anchor, and a wire reaches 7.5 in every
     * direction. Without room between them the machines of one test land in the next test along,
     * where they are broken by its blocks or joined to its network, and the failure appears in
     * whichever test happened to run second. That is the worst kind of flake: real, silent, and
     * blamed on the wrong code.
     */
    private static final int PADDING = 24;

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, PADDING)));
    }

    /**
     * Kept as a separate name because the pole tests say what they need at the call site.
     *
     * <p>It used to be the only spaced one. Now every test here is spaced - see {@link #PADDING} -
     * because every test here builds something bigger than a block.
     */
    private static void registerSpaced(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        register(event, environment, name, factory, maxTicks);
    }

    /**
     * A boiler with coal in it, and an engine lying along the line to it.
     *
     * <p>The facing matters now. An engine takes steam through the two faces on its own axis, so
     * one laid north-south beside a boiler to its west connects to nothing at all - which is the
     * point of it being directional, and a thing every test here has to respect.
     */
    private static void buildChain(GameTestHelper helper, boolean fuelled) {
        place(helper, BOILER, ModBlocks.BOILER.get());
        placeEngine(helper, ENGINE);
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

    /** How high anything you would stand on reaches in this column, counting from its floor. */
    private static double surface(GameTestHelper helper, BlockPos floor, int layers) {
        double top = 0;
        for (int layer = 0; layer < layers; layer++) {
            BlockPos pos = floor.above(layer);
            VoxelShape shape = helper.getBlockState(pos)
                    .getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
            if (!shape.isEmpty()) {
                top = Math.max(top, layer + shape.max(Direction.Axis.Y));
            }
        }
        return top;
    }

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    /**
     * Places a block the way a player does, {@code setPlacedBy} included.
     *
     * <p>{@code helper.setBlock} writes one block state and stops, which is the whole of most
     * blocks and none of a pole: a pole is three blocks tall and the upper two are placed from
     * {@code setPlacedBy}. A test that skipped it would be testing a pole that cannot exist.
     */
    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        place(helper, pos, block.defaultBlockState());
    }

    /**
     * The same, for a block that has to be turned a particular way.
     *
     * <p>The state must carry the machine's <em>anchor</em> part, which the default state does, so
     * that {@code setPlacedBy} builds the rest of the machine around this position rather than
     * around some corner of it.
     */
    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state);
        BlockPos absolute = helper.absolutePos(pos);
        state.getBlock().setPlacedBy(helper.getLevel(), absolute,
                helper.getLevel().getBlockState(absolute), null, ItemStack.EMPTY);
    }

    /** An engine lying north-south, which is the axis its two steam ends are on. */
    private static void placeEngine(GameTestHelper helper, BlockPos pos) {
        place(helper, pos, ModBlocks.STEAM_ENGINE.get().defaultBlockState()
                .setValue(SteamEngineBlock.FACING, Direction.NORTH));
    }

    /** Which part of a pole, if any, stands at a test-relative position. */
    private static @Nullable PolePart partAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(pos));
        return state.is(ModBlocks.SMALL_ELECTRIC_POLE.get())
                ? state.getValue(SmallElectricPoleBlock.PART)
                : null;
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
            place(helper, BOILER, ModBlocks.BOILER.get());
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
            placeEngine(helper, ENGINE);
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
     * <p>Three poles in a vertical line, four blocks apart - so each three-block pole clears the
     * next by one. The outer two are eight apart, which
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
            place(helper, LOWER, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, UPPER, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole has no network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole has no network");
                        helper.assertFalse(lower == upper,
                                "two poles eight blocks apart joined one network, so wire reach is "
                                        + "not being measured");
                        helper.assertValueEqual(lower.poleCount(), 1, "poles in the lower network");
                    })
                    .thenExecute(() -> place(helper, MIDDLE, ModBlocks.SMALL_ELECTRIC_POLE.get()))
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
            // The near engine has blocks inside the pole's 5x5 supply area; the other is four
            // above it, outside - a supply area that was really the wire reach in disguise would
            // pick up both, and nothing else would notice.
            //
            // Note what a footprint changed here. The engine is powered because part of it is in
            // the area, not because its middle is: its anchor is three blocks from the pole and
            // out of range on its own. That is Factorio's rule - a pole powers a machine its area
            // touches - and it is why every block of a machine publishes the energy capability.
            // What that costs is a machine seen several times over, which PowerNetwork settles by
            // counting distinct handlers rather than positions.
            placeEngine(helper, ENGINE);
            placeEngine(helper, OUT_OF_RANGE);
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.endpointCount(), 1,
                        "machines a pole found in its supply area");
                boolean foundTheEngine = false;
                for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                    BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                    foundTheEngine |= network.hasEndpoint(helper.absolutePos(cell).asLong());
                }
                helper.assertTrue(foundTheEngine,
                        "the machine beside the pole is not the one it found");
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
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(10, () -> helper.assertValueEqual(
                            requireNetwork(helper, POLE, "the pole has no network").endpointCount(),
                            0,
                            "machines found beside a pole standing on its own"))
                    .thenExecute(() -> placeEngine(helper, ENGINE))
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
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

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

    /**
     * A pole is four blocks, and only the foot is a pole as far as the grid is concerned.
     *
     * <p>The second assertion is the one worth having. A multi-block that put a block entity in
     * every part would work perfectly and cost three times the memory, and nothing else here would
     * ever notice.
     */
    public static class PoleStandsFourBlocksTallTest extends GameTestInstance {

        public static final MapCodec<PoleStandsFourBlocksTallTest> CODEC =
                RecordCodecBuilder.<PoleStandsFourBlocksTallTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleStandsFourBlocksTallTest::info))
                                .apply(i, PoleStandsFourBlocksTallTest::new));

        private static final BlockPos FOOT = new BlockPos(0, 1, 0);

        public PoleStandsFourBlocksTallTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, FOOT, ModBlocks.SMALL_ELECTRIC_POLE.get());

            for (PolePart part : PolePart.values()) {
                helper.assertValueEqual(partAt(helper, FOOT.above(part.height())), part,
                        "the part " + part.height() + " blocks up");
            }

            for (PolePart part : PolePart.values()) {
                boolean expected = part == PolePart.FOOT;
                helper.assertTrue(
                        (helper.getLevel().getBlockEntity(
                                helper.absolutePos(FOOT.above(part.height()))) != null) == expected,
                        "block entity at the " + part.getSerializedName() + " of a pole - only the "
                                + "foot should have one, or a base of poles pays four times over");
            }

            helper.runAfterDelay(5, () -> {
                PowerNetwork network = requireNetwork(helper, FOOT, "the pole foot has no network");
                helper.assertValueEqual(network.poleCount(), 1,
                        "poles in the network - four blocks are one pole");
                helper.assertTrue(networkAt(helper, FOOT.above()) == null,
                        "the middle of a pole joined the network as a pole of its own");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole stands four blocks tall");
        }
    }

    /**
     * Break any part of a pole and the whole pole goes - once, and for one item back.
     *
     * <p>The middle is the interesting one to hit: it is neither the part that holds the block
     * entity nor the part that drops the item, so it is the case where a teardown that only
     * handled "broken from the bottom" would leave a pole floating with nothing under it.
     */
    public static class PoleBreaksAsOneTest extends GameTestInstance {

        public static final MapCodec<PoleBreaksAsOneTest> CODEC =
                RecordCodecBuilder.<PoleBreaksAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleBreaksAsOneTest::info))
                                .apply(i, PoleBreaksAsOneTest::new));

        private static final BlockPos FOOT = new BlockPos(0, 1, 0);

        public PoleBreaksAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, FOOT, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(networkAt(helper, FOOT) != null, "the pole has no network");
                        helper.setBlock(FOOT.above(), Blocks.AIR);
                    })
                    .thenExecuteAfter(5, () -> {
                        for (int height = 0; height < SmallElectricPoleBlock.HEIGHT; height++) {
                            // Not assertValueEqual: it compares by calling equals on the value,
                            // so a null one is a crash rather than a failure.
                            helper.assertTrue(partAt(helper, FOOT.above(height)) == null,
                                    "part of the pole is still standing " + height
                                            + " blocks up after the middle was broken");
                        }
                        helper.assertTrue(networkAt(helper, FOOT) == null,
                                "a pole that no longer exists is still in the grid index");

                        // One item, not three and not none. The middle and the top drop nothing
                        // by a loot-table condition; the bottom's own destruction is what pays
                        // the player back, whichever part they actually hit.
                        helper.assertItemEntityCountIs(
                                ModItems.SMALL_ELECTRIC_POLE.get(), FOOT, 4.0, 1);
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole breaks as one");
        }
    }

    /**
     * A pole will not go under a low ceiling rather than going in half-built.
     *
     * <p>Refusing at {@code getStateForPlacement} is what makes the teardown rule safe to state so
     * bluntly: if a pole could ever be placed with no room for its top, that rule would delete it
     * again the instant anything nudged it, and the player would have watched a pole vanish.
     */
    public static class PoleNeedsHeadroomTest extends GameTestInstance {

        public static final MapCodec<PoleNeedsHeadroomTest> CODEC =
                RecordCodecBuilder.<PoleNeedsHeadroomTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleNeedsHeadroomTest::info))
                                .apply(i, PoleNeedsHeadroomTest::new));

        private static final BlockPos GROUND = new BlockPos(0, 1, 0);
        private static final BlockPos FOOT = new BlockPos(0, 2, 0);

        public PoleNeedsHeadroomTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(GROUND, Blocks.STONE);

            helper.assertTrue(placementState(helper) != null,
                    "a pole refused to go somewhere with three blocks of clear air above it");

            // A ceiling where the pole's own top would be.
            helper.setBlock(FOOT.above(2), Blocks.STONE);
            helper.assertTrue(placementState(helper) == null,
                    "a pole went in under a ceiling too low for it, so two thirds of it is missing");

            helper.succeed();
        }

        /** What the block would place as, asked exactly the way a right-click asks it. */
        private static @Nullable BlockState placementState(GameTestHelper helper) {
            BlockPos below = helper.absolutePos(GROUND);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
            BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.SMALL_ELECTRIC_POLE.get()), hit);
            return ModBlocks.SMALL_ELECTRIC_POLE.get().getStateForPlacement(context);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole needs headroom");
        }
    }

    /**
     * <b>Poles wire themselves up.</b>
     *
     * <p>There is no coil to craft and no connector to place: two poles that can see each other are
     * wired, because the network has already worked out that they are connected and a player being
     * asked to say it a second time is the part of Immersive Engineering this pack does not want.
     *
     * <p>What is asserted is the list the <em>client</em> draws from. Both ends have to know - a
     * wire has two, and the pole that was already standing has no other way to learn that something
     * came into view - and the pole out of reach has to know nothing, or the rule is not reach at
     * all. Breaking one end has to clear the other, or the wire hangs in the air pointing at
     * nothing.
     */
    public static class PoleWiresLinkUpTest extends GameTestInstance {

        public static final MapCodec<PoleWiresLinkUpTest> CODEC =
                RecordCodecBuilder.<PoleWiresLinkUpTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleWiresLinkUpTest::info))
                                .apply(i, PoleWiresLinkUpTest::new));

        private static final BlockPos NEAR = new BlockPos(0, 1, 0);
        /** Five apart, inside the 7.5 wire reach. */
        private static final BlockPos ALSO_NEAR = new BlockPos(5, 1, 0);
        /** Twelve from both, outside it. */
        private static final BlockPos FAR = new BlockPos(0, 1, 12);

        public PoleWiresLinkUpTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, ALSO_NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, FAR, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        assertWiredTo(helper, NEAR, ALSO_NEAR);
                        assertWiredTo(helper, ALSO_NEAR, NEAR);
                        helper.assertValueEqual(links(helper, FAR).length, 0,
                                "wires from a pole twelve blocks from anything");
                    })
                    .thenExecute(() -> helper.setBlock(ALSO_NEAR, Blocks.AIR))
                    .thenExecuteAfter(5, () -> helper.assertValueEqual(links(helper, NEAR).length, 0,
                            "wires still hanging off a pole whose only neighbour was broken"))
                    .thenSucceed();
        }

        private static long[] links(GameTestHelper helper, BlockPos pole) {
            return helper.getBlockEntity(pole, SmallElectricPoleBlockEntity.class).links();
        }

        private static void assertWiredTo(GameTestHelper helper, BlockPos from, BlockPos to) {
            long wanted = helper.absolutePos(to).asLong();
            for (long link : links(helper, from)) {
                if (link == wanted) {
                    return;
                }
            }
            helper.fail("the pole at " + from + " is not wired to the one at " + to);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole wires link up");
        }
    }

    /**
     * <b>A pole claims enough space for the wires it draws, or they are culled away.</b>
     *
     * <p>A block entity renderer is frustum-tested against one box, and the default is the single
     * block the block entity sits in. A wire hangs between two poles seven blocks apart, so a pole
     * whose foot had gone off the edge of the screen stopped drawing wires that were still in plain
     * sight - which is what {@code getRenderBoundingBox} exists to fix.
     *
     * <p>Rendering cannot be tested headlessly, but the box can, and the box is the whole bug. The
     * assertion is that it reaches the far pole's head: the top of the far pole, not just its foot,
     * because that is where the wire actually ends.
     */
    public static class PoleWireBoundsTest extends GameTestInstance {

        public static final MapCodec<PoleWireBoundsTest> CODEC =
                RecordCodecBuilder.<PoleWireBoundsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PoleWireBoundsTest::info))
                                .apply(i, PoleWireBoundsTest::new));

        private static final BlockPos NEAR = new BlockPos(0, 1, 0);
        private static final BlockPos ALSO_NEAR = new BlockPos(6, 1, 0);

        public PoleWireBoundsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, ALSO_NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                AABB bounds = helper.getBlockEntity(NEAR, SmallElectricPoleBlockEntity.class).wireBounds();

                BlockPos ownHead = helper.absolutePos(
                        NEAR.above(SmallElectricPoleBlock.HEIGHT - 1));
                BlockPos farHead = helper.absolutePos(
                        ALSO_NEAR.above(SmallElectricPoleBlock.HEIGHT - 1));

                helper.assertTrue(bounds.contains(Vec3.atCenterOf(ownHead)),
                        "a pole does not claim its own head, so its wires are culled the moment "
                                + "its foot leaves the screen");
                helper.assertTrue(bounds.contains(Vec3.atCenterOf(farHead)),
                        "a pole does not claim the far end of the wire it draws, so the wire "
                                + "disappears whenever the pole itself is out of view");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pole wire bounds reach both ends");
        }
    }

    /**
     * A boiler opens, and what opens has the fuel slot in it.
     *
     * <p>It used to be fuelled by right-clicking with coal in hand, which meant its contents were
     * invisible and could not be taken back out. The assertion that matters is the slot count: a
     * menu that came up with only the player's inventory in it would look like a working screen
     * and be useless.
     */
    public static class BoilerOpensAScreenTest extends GameTestInstance {

        public static final MapCodec<BoilerOpensAScreenTest> CODEC =
                RecordCodecBuilder.<BoilerOpensAScreenTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BoilerOpensAScreenTest::info))
                                .apply(i, BoilerOpensAScreenTest::new));

        /** Six ingredient slots' worth of player inventory, plus the machine's own. */
        private static final int PLAYER_SLOTS = 36;

        public BoilerOpensAScreenTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            insert(boiler.fuelAccess(), Items.COAL, 1);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            AbstractContainerMenu menu = boiler.createMenu(1, player.getInventory(), player);
            helper.assertTrue(menu instanceof BoilerMenu, "the boiler opened something else");
            helper.assertValueEqual(menu.slots.size(), BoilerBlockEntity.SLOT_COUNT + PLAYER_SLOTS,
                    "slots in the boiler's menu");
            helper.assertValueEqual(
                    menu.getSlot(BoilerBlockEntity.FUEL_SLOT).getItem().getItem(), Items.COAL,
                    "what the first slot of the boiler's menu is showing");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("boiler opens a screen");
        }
    }

    /**
     * The fuel slot takes fuel and nothing else.
     *
     * <p>This used to be checked in the right-click handler, which is gone. Without it the slot
     * would happily accept a diamond and then sit there doing nothing, and an inserter pointed at
     * the boiler would keep feeding it whatever it had. One {@code isValid} closes the screen, the
     * hopper and the inserter at once, which is why it is asserted through the automation view
     * rather than through the menu.
     */
    public static class BoilerRefusesNonFuelTest extends GameTestInstance {

        public static final MapCodec<BoilerRefusesNonFuelTest> CODEC =
                RecordCodecBuilder.<BoilerRefusesNonFuelTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BoilerRefusesNonFuelTest::info))
                                .apply(i, BoilerRefusesNonFuelTest::new));

        public BoilerRefusesNonFuelTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);

            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.DIAMOND, 1), 0,
                    "diamonds accepted by a fuel slot");
            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.COAL, 1), 1,
                    "coal accepted by a fuel slot");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("boiler refuses what will not burn");
        }
    }

    /**
     * <b>Engines chain.</b> One boiler, two engines in a line, and the far one runs.
     *
     * <p>This is Factorio's arrangement and the reason an engine is a length of pipe that happens
     * to consume rather than a thing with a private connection to a boiler: steam runs along the
     * row, and the engine at the end is fed by the one before it. An engine that only drew from
     * boilers would pass every other test in this file and leave the second engine dead.
     */
    public static class SteamEnginesChainTest extends GameTestInstance {

        public static final MapCodec<SteamEnginesChainTest> CODEC =
                RecordCodecBuilder.<SteamEnginesChainTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEnginesChainTest::info))
                                .apply(i, SteamEnginesChainTest::new));

        /**
         * The next engine along the same line, chained off the far end of the first.
         *
         * <p>Five blocks past {@link #ENGINE}: two to reach the end of its spine, one for the seam
         * where the two machines touch, and two more to the middle of the second. A row of engines
         * off one boiler is the arrangement this subsystem exists for, and this is what one costs
         * now that an engine is the size Factorio made it.
         */
        private static final BlockPos FAR_ENGINE = new BlockPos(0, 1, 8);

        public SteamEnginesChainTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);
            placeEngine(helper, FAR_ENGINE);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored() > 0,
                        "the engine beside the boiler made no power");
                helper.assertTrue(
                        helper.getBlockEntity(FAR_ENGINE, SteamEngineBlockEntity.class).energyStored() > 0,
                        "the second engine in the row made no power, so steam does not run along "
                                + "a line of them and only the first one is worth building");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engines chain");
        }
    }

    /**
     * An engine takes steam through its two ends and nowhere else.
     *
     * <p>The other half of the same claim. If the connection were on all six faces the facing
     * would be decoration, a row would be no different from a heap, and you could feed an engine
     * by burying a boiler under it. The engine here lies north-south with the boiler due west.
     */
    public static class SteamEngineIgnoresSidesTest extends GameTestInstance {

        public static final MapCodec<SteamEngineIgnoresSidesTest> CODEC =
                RecordCodecBuilder.<SteamEngineIgnoresSidesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineIgnoresSidesTest::info))
                                .apply(i, SteamEngineIgnoresSidesTest::new));

        public SteamEngineIgnoresSidesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, BOILER, ModBlocks.BOILER.get());
            insert(helper.getBlockEntity(BOILER, BoilerBlockEntity.class).fuelAccess(), Items.COAL, 1);

            // Across the line rather than along it. The engine sits where a working one would,
            // and is turned a quarter turn - so its two open ends now point east and west, at
            // nothing, while the boiler's steam leaves to the north of it against a flank the
            // engine offers nothing on. Turning a machine has to be able to break a connection,
            // or its facing means nothing.
            place(helper, ENGINE, ModBlocks.STEAM_ENGINE.get().defaultBlockState()
                    .setValue(SteamEngineBlock.FACING, Direction.EAST));

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored(), 0,
                        "charge in an engine fed through its side, which has no connection");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine ignores its sides");
        }
    }

    /**
     * The other side of the connection: what a <em>pipe</em> sees when it looks at an engine.
     *
     * <p>{@code steam_engine_ignores_its_sides} covers which way the engine looks. This covers
     * which faces answer when something looks at it, which is a separate registration and the one
     * that will decide whether a pipe run can join an engine end-on or barge into its flank. It is
     * asserted directly because nothing else reaches it until pipes exist - breaking the sided
     * registration leaves every other test in this file passing.
     */
    public static class SteamEngineFacesTest extends GameTestInstance {

        public static final MapCodec<SteamEngineFacesTest> CODEC =
                RecordCodecBuilder.<SteamEngineFacesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineFacesTest::info))
                                .apply(i, SteamEngineFacesTest::new));

        public SteamEngineFacesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeEngine(helper, ENGINE);

            // The two open ends of the spine, and the face each one opens through. A north-facing
            // engine runs north to south, so its ends are two blocks either side of the middle.
            BlockPos north = SteamEngineShape.SHAPE.cellPos(
                    ENGINE, SteamEngineShape.NORTH_END, Direction.NORTH);
            BlockPos south = SteamEngineShape.SHAPE.cellPos(
                    ENGINE, SteamEngineShape.SOUTH_END, Direction.NORTH);

            helper.assertValueEqual(north, ENGINE.north(2), "where the north end of a spine is");
            helper.assertValueEqual(south, ENGINE.south(2), "where the south end of a spine is");

            helper.assertTrue(offersSteam(helper, north, Direction.NORTH),
                    "no steam at the north end of the engine, which is where a pipe goes");
            helper.assertTrue(offersSteam(helper, south, Direction.SOUTH),
                    "no steam at the south end of the engine");

            // Everywhere else, on every face: nothing. An engine is fed at its ends or not at
            // all, which is what makes a row of them a row rather than a heap - and now that it
            // has a footprint, "its ends" means two particular blocks rather than two faces of
            // one. The flanks are the interesting case: they are as close to a pipe as the ends
            // are, and they must still refuse it.
            for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                for (Direction side : Direction.values()) {
                    boolean isPort = (cell.equals(north) && side == Direction.NORTH)
                            || (cell.equals(south) && side == Direction.SOUTH);
                    helper.assertValueEqual(offersSteam(helper, cell, side), isPort,
                            "whether the engine offers steam at " + cell + " on its " + side
                                    + " face");
                }
            }
            helper.succeed();
        }

        private static boolean offersSteam(GameTestHelper helper, BlockPos pos, Direction side) {
            return helper.getLevel().getCapability(
                    Capabilities.Fluid.BLOCK, helper.absolutePos(pos), side) != null;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine connects on two faces");
        }
    }

    /**
     * A boiler turned every way it can be, and everything about it turning with it.
     *
     * <p><b>This is the test the whole multiblock framework was missing.</b> The assembler has no
     * facing - a Factorio assembler has no direction - so until there was a boiler, the rotation
     * in {@code Boxes} and {@code MachineShape} was written, compiled, and never once run in
     * anger. Three things have to turn together and none of them checks the others: where the
     * cells land, which way the geometry points, and which face the steam leaves by.
     *
     * <p>So the footprint is measured rather than asked for. A boiler facing north is three blocks
     * across and two deep; turned a quarter, it is two across and three deep, and its steam leaves
     * to the west instead of the south. Those are written out below as flat numbers, because a
     * test that computed them from the same rotation it is checking would agree with any rotation
     * at all, including a mirrored one.
     */
    public static class BoilerTurnsAsOneTest extends GameTestInstance {

        public static final MapCodec<BoilerTurnsAsOneTest> CODEC =
                RecordCodecBuilder.<BoilerTurnsAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BoilerTurnsAsOneTest::info))
                                .apply(i, BoilerTurnsAsOneTest::new));

        public BoilerTurnsAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        /** Facing, then the footprint it should occupy around the anchor, then where steam goes. */
        private record Turned(Direction facing, int minX, int maxX, int minZ, int maxZ,
                Direction port) {}

        private static final List<Turned> EXPECTED = List.of(
                new Turned(Direction.NORTH, -1, 1, -1, 0, Direction.SOUTH),
                new Turned(Direction.EAST, 0, 1, -1, 1, Direction.WEST),
                new Turned(Direction.SOUTH, -1, 1, 0, 1, Direction.NORTH),
                new Turned(Direction.WEST, -1, 0, -1, 1, Direction.EAST));

        @Override
        public void run(GameTestHelper helper) {
            for (Turned expected : EXPECTED) {
                check(helper, expected);
            }
            helper.succeed();
        }

        private void check(GameTestHelper helper, Turned expected) {
            clear(helper);
            place(helper, BOILER, ModBlocks.BOILER.get().defaultBlockState()
                    .setValue(BoilerBlock.FACING, expected.facing()));

            int minX = 99;
            int maxX = -99;
            int minZ = 99;
            int maxZ = -99;
            int blocks = 0;
            BlockPos steamAt = null;
            Direction steamSide = null;
            int ports = 0;

            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockPos pos = BOILER.offset(x, y, z);
                        if (!helper.getBlockState(pos).is(ModBlocks.BOILER.get())) {
                            continue;
                        }
                        blocks++;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);

                        for (Direction side : Direction.values()) {
                            if (helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                                    helper.absolutePos(pos), side) != null) {
                                ports++;
                                steamAt = pos;
                                steamSide = side;
                            }
                        }
                    }
                }
            }

            String turned = "a boiler facing " + expected.facing();
            helper.assertValueEqual(blocks, 7, turned + " is not seven blocks");
            helper.assertValueEqual(minX, expected.minX(), turned + ": western edge");
            helper.assertValueEqual(maxX, expected.maxX(), turned + ": eastern edge");
            helper.assertValueEqual(minZ, expected.minZ(), turned + ": northern edge");
            helper.assertValueEqual(maxZ, expected.maxZ(), turned + ": southern edge");

            helper.assertValueEqual(ports, 1, turned + " offers steam in more than one place");
            helper.assertValueEqual(steamAt, BOILER, turned + ": which block steam leaves by");
            helper.assertValueEqual(steamSide, expected.port(), turned + ": which face steam leaves by");
        }

        /** The machine from the last facing, out of the way of the next one. */
        private void clear(GameTestHelper helper) {
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockPos pos = BOILER.offset(x, y, z);
                        if (helper.getBlockState(pos).is(ModBlocks.BOILER.get())) {
                            helper.setBlock(pos, Blocks.AIR);
                        }
                    }
                }
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a boiler turns as one");
        }
    }

    /**
     * Break one block of an engine and all seventeen go, giving back exactly one engine.
     *
     * <p>A flank is broken rather than the middle: it is two blocks from the block entity, it has
     * no loot of its own, and everything that happens after it is the teardown rule crossing the
     * footprint. Seventeen is also the first machine big enough for that cascade to be worth
     * doubting - the two ways it fails are blocks left standing that nothing can break, and
     * seventeen engines dropped where one was placed.
     */
    public static class EngineBreaksAsOneTest extends GameTestInstance {

        public static final MapCodec<EngineBreaksAsOneTest> CODEC =
                RecordCodecBuilder.<EngineBreaksAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(EngineBreaksAsOneTest::info))
                                .apply(i, EngineBreaksAsOneTest::new));

        public EngineBreaksAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeEngine(helper, ENGINE);

            int standing = 0;
            for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                helper.assertBlockPresent(ModBlocks.STEAM_ENGINE.get(), cell);
                standing++;
            }
            helper.assertValueEqual(standing, 17, "blocks in a steam engine");

            // A corner of the west flank, as far from the block entity as anything gets.
            helper.getLevel().destroyBlock(helper.absolutePos(ENGINE.offset(-1, 0, -2)), true);

            helper.runAfterDelay(2, () -> {
                for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                    helper.assertBlockPresent(Blocks.AIR,
                            SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH));
                }
                helper.assertItemEntityCountIs(
                        ModItems.STEAM_ENGINE.get(), ENGINE, 6.0, 1);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an engine breaks as one");
        }
    }

    /**
     * A power plant you can walk across: boilers in a row, and an engine crossed at its waist.
     *
     * <p>The same requirement the assembler was shaped around, applied to the two machines a
     * player builds first and packs tightest. A boiler row and an engine chain are the standard
     * Factorio arrangement, and a wall of them would fence the player out of their own power
     * plant.
     *
     * <p>Two things are asserted. Boilers chained side by side leave a lane between their
     * chimneys, because the chimney is on the middle tile of three rather than on a corner. And
     * an engine can be crossed at the tile between its two flywheels, which is why there are two
     * of them with a gap rather than one long housing.
     */
    public static class PowerMachinesTileWalkablyTest extends GameTestInstance {

        public static final MapCodec<PowerMachinesTileWalkablyTest> CODEC =
                RecordCodecBuilder.<PowerMachinesTileWalkablyTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerMachinesTileWalkablyTest::info))
                                .apply(i, PowerMachinesTileWalkablyTest::new));

        public PowerMachinesTileWalkablyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        private static final double STEP = 0.6;
        private static final double JUMP = 1.25;

        @Override
        public void run(GameTestHelper helper) {
            // Two boilers shoulder to shoulder, and the row across their front.
            place(helper, BOILER, ModBlocks.BOILER.get());
            place(helper, BOILER.offset(3, 0, 0), ModBlocks.BOILER.get());
            walk(helper, BOILER.offset(-1, 0, -1), Direction.EAST, 6, "the front of a boiler row");

            // An engine, crossed at the waist between its flywheels.
            placeEngine(helper, ENGINE);
            walk(helper, ENGINE.offset(-2, 0, 0), Direction.EAST, 5, "the waist of an engine");

            // And the flywheels really are two blocks tall, so this cannot pass by going flat.
            helper.assertValueEqual(surface(helper, ENGINE.north(1), 2), 2.0,
                    "height of the first flywheel");
            helper.assertValueEqual(surface(helper, ENGINE.south(1), 2), 2.0,
                    "height of the second flywheel");
            helper.assertValueEqual(surface(helper, BOILER, 2), 2.0, "height of a chimney");
            helper.succeed();
        }

        /** Walks a line one column at a time, and complains about the first step too big to take. */
        private void walk(GameTestHelper helper, BlockPos from, Direction along, int columns,
                String what) {
            double previous = 0;
            for (int step = 0; step < columns; step++) {
                BlockPos column = from.relative(along, step);
                double top = surface(helper, column, 2);

                helper.assertTrue(top <= JUMP, what + ": the column at " + column + " stands "
                        + top + " blocks high, more than the " + JUMP + " a player can jump onto");
                // Getting onto the machine in the first place is a jump, and is allowed to be
                // one. Everything after that has to be a step, or crossing a factory is hopping.
                // Only the climbs. Walking off the far side of a machine is a drop, and a
                // drop of one block costs a player nothing at all.
                double climb = top - previous;
                double allowed = previous == 0 ? JUMP : STEP;
                helper.assertTrue(step == 0 || climb <= allowed,
                        what + ": the step up onto " + column + " is " + climb + " blocks, more "
                                + "than the " + allowed + " a player manages from " + previous);
                helper.assertTrue(helper.getBlockState(column.above(2)).isAir(),
                        what + ": no headroom over " + column);
                previous = top;
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power machines tile walkably");
        }
    }
}
