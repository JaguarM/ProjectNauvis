package com.jaguarm.nauvisrocket;

import java.util.List;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.silo.Launch;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;
import com.jaguarm.nauvisrocket.silo.RocketSiloShape;
import com.jaguarm.nauvisrocket.silo.RocketSiloStatus;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
 * Tests that run inside a real server, headless: the silo stands, builds a rocket part out of
 * what it is fed, launches when it has a rocket and a satellite, and sleeps when it has nothing
 * to do.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}, which puts every mod in the pack on one
 * classpath, or {@code :nauvis_rocket:runGameTestServer} for this mod alone.
 */
@EventBusSubscriber(modid = NauvisRocket.MODID)
public final class NauvisRocketGameTests {

    private NauvisRocketGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** Where every test puts its silo: one block up, so it is not inside the floor. */
    private static final BlockPos SILO = new BlockPos(0, 1, 0);

    /**
     * How much empty world to leave around each test. A silo is nine blocks across and its
     * anchor is in the middle, so four each way is the machine and the rest is room.
     */
    private static final int PADDING = 24;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisRocket.MODID);

    static {
        TEST_TYPES.register("a_silo_is_a_hundred_and_thirty_five_blocks", () -> SiloPlacesTest.CODEC);
        TEST_TYPES.register("a_silo_builds_a_rocket_part", () -> SiloBuildsAPartTest.CODEC);
        TEST_TYPES.register("a_silo_launches_a_satellite", () -> SiloLaunchesTest.CODEC);
        TEST_TYPES.register("a_silo_with_nothing_to_build_sleeps", () -> SiloSleepsTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisRocket.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, environment, "a_silo_is_a_hundred_and_thirty_five_blocks", SiloPlacesTest::new, 20);
        register(event, environment, "a_silo_builds_a_rocket_part", SiloBuildsAPartTest::new, 120);
        register(event, environment, "a_silo_launches_a_satellite", SiloLaunchesTest::new,
                RocketSiloBlockEntity.LAUNCH_TICKS + 60);
        register(event, environment, "a_silo_with_nothing_to_build_sleeps", SiloSleepsTest::new, 40);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            String name, TestFactory factory, int maxTicks) {
        event.registerTest(Identifier.fromNamespaceAndPath(NauvisRocket.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE,
                        false, 1, 1, false, PADDING)));
    }

    /** A whole silo, anchored here. */
    private static RocketSiloBlockEntity placeSilo(GameTestHelper helper) {
        Multiblock.place(ModBlocks.ROCKET_SILO.get(), helper.getLevel(), helper.absolutePos(SILO),
                ModBlocks.ROCKET_SILO.get().defaultBlockState());
        return helper.getBlockEntity(SILO, RocketSiloBlockEntity.class);
    }

    /** Fills the silo's buffer the way a power pole would. */
    private static void charge(RocketSiloBlockEntity silo) {
        try (Transaction transaction = Transaction.openRoot()) {
            silo.gridView().insert(RocketSiloBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    /** An item by id, so a test can name another mod's item without a compile-time dependency. */
    private static Item item(GameTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
        return item;
    }

    /** Puts items in the way an inserter will: through the published capability view. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** The silo is the size Factorio made it, with its block entity in the middle of the pad. */
    public static class SiloPlacesTest extends GameTestInstance {

        public static final MapCodec<SiloPlacesTest> CODEC = RecordCodecBuilder.<SiloPlacesTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(SiloPlacesTest::info)).apply(i, SiloPlacesTest::new));

        public SiloPlacesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeSilo(helper);
            int blocks = 0;
            for (BlockPos pos : RocketSiloShape.SHAPE.positions(helper.absolutePos(SILO), net.minecraft.core.Direction.NORTH)) {
                if (helper.getLevel().getBlockState(pos).is(ModBlocks.ROCKET_SILO.get())) {
                    blocks++;
                }
            }
            helper.assertValueEqual(blocks, RocketSiloShape.CELL_COUNT, "blocks of silo placed");
            helper.assertValueEqual(RocketSiloShape.SHAPE.width(), 9, "the silo's width");
            helper.assertValueEqual(RocketSiloShape.SHAPE.depth(), 9, "the silo's depth");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a silo is a hundred and thirty-five blocks");
        }
    }

    /**
     * Fed one rocket part's worth and charged, a silo builds one rocket part into its rocket in
     * the recipe's three seconds, and spends the ingredients doing it.
     */
    public static class SiloBuildsAPartTest extends GameTestInstance {

        public static final MapCodec<SiloBuildsAPartTest> CODEC = RecordCodecBuilder.<SiloBuildsAPartTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(SiloBuildsAPartTest::info)).apply(i, SiloBuildsAPartTest::new));

        public SiloBuildsAPartTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            ResourceHandler<ItemResource> view = silo.automationView();
            helper.assertValueEqual(insert(view, item(helper, "neoprogressivematerials:low_density_structure"), 10), 10,
                    "low density structures accepted");
            helper.assertValueEqual(insert(view, item(helper, "neoprogressivematerials:rocket_control_unit"), 10), 10,
                    "rocket control units accepted");
            helper.assertValueEqual(insert(view, item(helper, "neoprogressivematerials:rocket_fuel"), 10), 10,
                    "rocket fuel accepted");

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(silo.status(), RocketSiloStatus.BUILDING, "status while building");
                helper.assertValueEqual(silo.parts(), 0, "parts before the recipe's three seconds are up");
            });
            // Three seconds is sixty ticks; the first tick's check and a tick of slack.
            helper.runAfterDelay(70, () -> {
                helper.assertValueEqual(silo.parts(), 1, "rocket parts built");
                for (int slot = 0; slot < RocketSiloBlockEntity.INPUT_SLOTS; slot++) {
                    helper.assertValueEqual(silo.inventory().getAmountAsInt(slot), 0,
                            "ingredients left in slot " + slot + " after one part");
                }
                helper.assertTrue(silo.energyStored() < RocketSiloBlockEntity.ENERGY_CAPACITY,
                        "the silo built a part for nothing");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a silo builds a rocket part");
        }
    }

    /**
     * A silo with a complete rocket and a satellite in its slot launches on its own: the
     * satellite goes, the rocket empties, a thousand space science packs are owed and the
     * output slot fills with the first stack of them, and the advancement is the game's end.
     */
    public static class SiloLaunchesTest extends GameTestInstance {

        public static final MapCodec<SiloLaunchesTest> CODEC = RecordCodecBuilder.<SiloLaunchesTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(SiloLaunchesTest::info)).apply(i, SiloLaunchesTest::new));

        public SiloLaunchesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            silo.loadRocket(100);
            helper.assertValueEqual(insert(silo.automationView(), ModItems.SATELLITE.get(), 2), 1,
                    "satellites accepted: a rocket carries one");

            helper.runAfterDelay(5, () -> helper.assertValueEqual(silo.status(), RocketSiloStatus.LAUNCHING,
                    "status once the rocket has its satellite"));
            helper.runAfterDelay(RocketSiloBlockEntity.LAUNCH_TICKS + 10, () -> {
                helper.assertValueEqual(silo.launches(), 1, "rockets launched");
                helper.assertValueEqual(silo.parts(), 0, "parts left in the rocket after the launch");
                helper.assertValueEqual(silo.inventory().getAmountAsInt(RocketSiloBlockEntity.SATELLITE_SLOT), 0,
                        "the satellite stayed behind");
                int inOutput = silo.inventory().getAmountAsInt(RocketSiloBlockEntity.OUTPUT_SLOT);
                helper.assertTrue(silo.inventory().getResource(RocketSiloBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.SPACE_SCIENCE_PACK.get()), "the output holds something other than space science");
                helper.assertValueEqual(inOutput + silo.owed(), 1000, "space science sent back, in the output and owed");
                helper.assertTrue(inOutput > 0, "none of the science reached the output slot");

                Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
                helper.assertTrue(Launch.award((ServerPlayer) player), "the launch advancement could not be awarded");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a silo launches a satellite");
        }
    }

    /** A silo with nothing in its slots schedules nothing: non-negotiable #5. */
    public static class SiloSleepsTest extends GameTestInstance {

        public static final MapCodec<SiloSleepsTest> CODEC = RecordCodecBuilder.<SiloSleepsTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(SiloSleepsTest::info)).apply(i, SiloSleepsTest::new));

        public SiloSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(silo.status(), RocketSiloStatus.NO_INGREDIENTS, "status of an empty silo");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                        helper.absolutePos(SILO), ModBlocks.ROCKET_SILO.get()), "an empty silo is still ticking");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a silo with nothing to build sleeps");
        }
    }
}
