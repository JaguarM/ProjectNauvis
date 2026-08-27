package com.jaguarm.nauvisresearch;

import java.util.List;

import com.jaguarm.nauvisresearch.lab.LabBlock;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvisresearch.multiblock.Multiblock;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
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
import net.minecraft.world.item.Item;
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

    /** Given packs and power, a lab finishes a cycle in exactly the time it says it will. */
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
            LabBlockEntity lab = placeLab(helper);
            charge(lab);
            helper.assertValueEqual(insert(lab.automationView(),
                    ModItems.SCIENCE_PACK_1.get(), 2), 2, "packs accepted");

            helper.assertValueEqual(lab.cycles(), 0, "research before it has done any");

            // One tick short: still working, and the pack not yet spent.
            helper.runAfterDelay(LabBlockEntity.TICKS_PER_CYCLE - 1, () -> {
                helper.assertValueEqual(lab.cycles(), 0,
                        "research a tick before the cycle is due");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 2,
                        "packs a tick before the cycle is due");
            });

            helper.runAfterDelay(LabBlockEntity.TICKS_PER_CYCLE + 2, () -> {
                helper.assertValueEqual(lab.cycles(), 1, "research after one cycle");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 1,
                        "packs left after one cycle");
                helper.assertTrue(lab.energyStored() < LabBlockEntity.ENERGY_CAPACITY,
                        "a lab that researched without spending any power");
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
}
