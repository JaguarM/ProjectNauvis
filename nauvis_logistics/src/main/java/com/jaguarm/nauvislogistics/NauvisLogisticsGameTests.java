package com.jaguarm.nauvislogistics;

import java.util.List;

import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.storage.IronChestBlockEntity;
import com.jaguarm.nauvislogistics.transport.InserterBlock;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlockEntity;
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
import net.minecraft.world.item.Items;
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
 * <p>The one that earns its keep is {@code inserter_wakes_when_source_fills}. Everything else here
 * checks that the inserter does its job; that one checks the claim the whole design rests on -
 * that a chest gaining an item reaches the inserter beside it without anyone polling for it. If
 * that claim is wrong the inserter still works and the test suite still passes without it, and a
 * base of ten thousand inserters is quietly a slideshow. See {@link InserterBlock}.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}, which puts every mod in the pack on one
 * classpath, or {@code :nauvis_logistics:runGameTestServer} for this mod alone.
 */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class NauvisLogisticsGameTests {

    private NauvisLogisticsGameTests() {}

    /** Minecraft ships {@code minecraft:empty}; a missing structure makes a test silently not run. */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** A chest, an inserter pointing east, and a chest. The whole of milestone 1 in three blocks. */
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos INSERTER = new BlockPos(1, 1, 0);
    private static final BlockPos DESTINATION = new BlockPos(2, 1, 0);

    /** Beside the inserter but not in its way: a container it must learn to ignore. */
    private static final BlockPos BYSTANDER = new BlockPos(1, 1, 1);

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisLogistics.MODID);

    static {
        TEST_TYPES.register("inserter_moves_items", () -> InserterMovesItemsTest.CODEC);
        TEST_TYPES.register("inserter_sleeps", () -> InserterSleepsTest.CODEC);
        TEST_TYPES.register("inserter_wakes_when_source_fills", () -> InserterWakesTest.CODEC);
        TEST_TYPES.register("inserter_needs_fuel", () -> InserterNeedsFuelTest.CODEC);
        TEST_TYPES.register("inserter_ignores_bystanders", () -> InserterIgnoresBystandersTest.CODEC);
        TEST_TYPES.register("iron_chest_holds_items", () -> IronChestHoldsItemsTest.CODEC);
        TEST_TYPES.register("inserter_fills_iron_chest", () -> InserterFillsIronChestTest.CODEC);
        TEST_TYPES.register("electric_inserter_moves_items", () -> ElectricInserterMovesItemsTest.CODEC);
        TEST_TYPES.register("electric_inserter_needs_power", () -> ElectricInserterNeedsPowerTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "inserter_moves_items", InserterMovesItemsTest::new, 200);
        register(event, environment, "inserter_sleeps", InserterSleepsTest::new, 100);
        register(event, environment, "inserter_wakes_when_source_fills", InserterWakesTest::new, 200);
        register(event, environment, "inserter_needs_fuel", InserterNeedsFuelTest::new, 200);
        register(event, environment, "inserter_ignores_bystanders", InserterIgnoresBystandersTest::new, 100);
        register(event, environment, "iron_chest_holds_items", IronChestHoldsItemsTest::new, 60);
        register(event, environment, "inserter_fills_iron_chest", InserterFillsIronChestTest::new, 200);
        register(event, environment, "electric_inserter_moves_items",
                ElectricInserterMovesItemsTest::new, 200);
        register(event, environment, "electric_inserter_needs_power",
                ElectricInserterNeedsPowerTest::new, 200);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE)));
    }

    /**
     * Chest, inserter, chest - the arrangement every test here uses.
     *
     * @param fuelled false to leave the inserter without coal.
     */
    private static void buildLine(GameTestHelper helper, boolean fuelled) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(DESTINATION, Blocks.CHEST);
        helper.setBlock(INSERTER, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                .setValue(InserterBlock.FACING, Direction.EAST));

        if (fuelled) {
            BurnerInserterBlockEntity inserter = helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class);
            insert(inserter.fuelAccess(), Items.COAL, 1);
        }
    }

    /**
     * The same line with the electric inserter in the middle.
     *
     * @param charged false to leave it with an empty buffer and no pole anywhere near it.
     */
    private static void buildElectricLine(GameTestHelper helper, boolean charged) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(DESTINATION, Blocks.CHEST);
        helper.setBlock(INSERTER, ModBlocks.INSERTER.get().defaultBlockState()
                .setValue(InserterBlock.FACING, Direction.EAST));

        if (charged) {
            charge(helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class));
        }
    }

    /** Fills the buffer the way a pole would, through the capability the pole would use. */
    private static void charge(ElectricInserterBlockEntity inserter) {
        try (Transaction transaction = Transaction.openRoot()) {
            inserter.gridView().insert(ElectricInserterBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    private static boolean isElectricScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(INSERTER), ModBlocks.INSERTER.get());
    }

    /** A neighbour's inventory, reached exactly the way the inserter reaches it. */
    private static ResourceHandler<ItemResource> container(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "expected an item handler at " + pos);
        return handler;
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int countIn(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getResource(index).getItem() == item) {
                total += handler.getAmountAsInt(index);
            }
        }
        return total;
    }

    /**
     * Whether the inserter has a block tick coming - which is what "awake" means here.
     *
     * <p>Asserting this rather than "it eventually moved an item" is the difference between
     * testing the behaviour and testing the cost of the behaviour.
     */
    private static boolean isScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(INSERTER), ModBlocks.BURNER_INSERTER.get());
    }

    /** One item goes from the chest behind to the chest in front, and the inserter burns for it. */
    public static class InserterMovesItemsTest extends GameTestInstance {

        public static final MapCodec<InserterMovesItemsTest> CODEC =
                RecordCodecBuilder.<InserterMovesItemsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterMovesItemsTest::info))
                                .apply(i, InserterMovesItemsTest::new));

        public InserterMovesItemsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, true);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 3), 3,
                    "ingots put in the source chest");

            // One swing, plus a tick to wake on and a little slack.
            helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                        "ingots delivered after one swing");
                helper.assertValueEqual(countIn(container(helper, SOURCE), Items.IRON_INGOT), 2,
                        "ingots left in the source chest");
                helper.assertTrue(
                        helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class).burnTime() > 0,
                        "the inserter moved an item without burning anything");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("inserter moves items");
        }
    }

    /** A fuelled inserter with an empty chest behind it costs nothing at all. */
    public static class InserterSleepsTest extends GameTestInstance {

        public static final MapCodec<InserterSleepsTest> CODEC =
                RecordCodecBuilder.<InserterSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterSleepsTest::info))
                                .apply(i, InserterSleepsTest::new));

        public InserterSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, true);
            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper),
                        "an inserter with nothing to move is still scheduled to tick");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("inserter sleeps");
        }
    }

    /**
     * <b>The test this design exists to pass.</b>
     *
     * <p>A sleeping inserter is woken by a chest it does not own gaining an item, with nothing
     * polling and nothing subscribing - purely because a container that changes calls
     * {@code setChanged}, and NeoForge routes that to all six neighbours as
     * {@code onNeighborChange}. The wake is asserted in the same tick as the insert, because if it
     * needed a tick of slack it would not be a signal, it would be a poll wearing a disguise.
     */
    public static class InserterWakesTest extends GameTestInstance {

        public static final MapCodec<InserterWakesTest> CODEC =
                RecordCodecBuilder.<InserterWakesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterWakesTest::info))
                                .apply(i, InserterWakesTest::new));

        public InserterWakesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, true);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper), "the inserter never went to sleep to begin with");

                insert(container(helper, SOURCE), Items.IRON_INGOT, 1);

                helper.assertTrue(isScheduled(helper),
                        "a chest gaining an item did not wake the inserter beside it - onNeighborChange "
                                + "is what this whole design rests on, so check it still reaches the block");

                // And having woken, it does the work rather than merely stirring.
                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                    helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                            "ingots delivered after waking");
                    helper.assertFalse(isScheduled(helper),
                            "the inserter did not go back to sleep once the chest was empty again");
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
            return Component.literal("inserter wakes when source fills");
        }
    }

    /** No coal, no work - and no ticking while it waits for some. */
    public static class InserterNeedsFuelTest extends GameTestInstance {

        public static final MapCodec<InserterNeedsFuelTest> CODEC =
                RecordCodecBuilder.<InserterNeedsFuelTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterNeedsFuelTest::info))
                                .apply(i, InserterNeedsFuelTest::new));

        public InserterNeedsFuelTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, false);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 3), 3,
                    "ingots put in the source chest");

            helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 0,
                        "ingots moved by an inserter with no fuel");
                helper.assertFalse(isScheduled(helper),
                        "an inserter out of fuel is still scheduled to tick");

                // Coal is the other thing that has to wake it, and it arrives in its own slot
                // rather than a neighbour's.
                BurnerInserterBlockEntity inserter = helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class);
                insert(inserter.fuelAccess(), Items.COAL, 1);
                helper.assertTrue(isScheduled(helper), "fuel arriving did not wake the inserter");

                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                    helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                            "ingots delivered once it was fuelled");
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
            return Component.literal("inserter needs fuel");
        }
    }

    /**
     * A container beside the inserter that is neither its source nor its destination does not wake
     * it.
     *
     * <p>{@code onNeighborChange} arrives from all six sides, and in a packed base most of those
     * are machines saving themselves for reasons this inserter cannot act on. Waking anyway is
     * correct and wasteful - it costs a tick to discover there is nothing to do - so the block
     * filters on the two positions it can use before looking anything up. This test is what stops
     * that filter from being quietly removed as a simplification.
     */
    public static class InserterIgnoresBystandersTest extends GameTestInstance {

        public static final MapCodec<InserterIgnoresBystandersTest> CODEC =
                RecordCodecBuilder.<InserterIgnoresBystandersTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterIgnoresBystandersTest::info))
                                .apply(i, InserterIgnoresBystandersTest::new));

        public InserterIgnoresBystandersTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, true);
            helper.setBlock(BYSTANDER, Blocks.CHEST);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper), "the inserter never went to sleep to begin with");

                insert(container(helper, BYSTANDER), Items.IRON_INGOT, 1);
                helper.assertFalse(isScheduled(helper),
                        "a chest the inserter cannot reach woke it anyway - the neighbour filter in "
                                + "InserterBlock.onNeighborChange is gone or wrong");

                // The same change to the source still wakes it, so the filter has not gone too far.
                insert(container(helper, SOURCE), Items.IRON_INGOT, 1);
                helper.assertTrue(isScheduled(helper), "the filter is now rejecting the source too");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("inserter ignores bystanders");
        }
    }

    /** The chest is a container of the size it claims, and automation can reach it. */
    public static class IronChestHoldsItemsTest extends GameTestInstance {

        public static final MapCodec<IronChestHoldsItemsTest> CODEC =
                RecordCodecBuilder.<IronChestHoldsItemsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(IronChestHoldsItemsTest::info))
                                .apply(i, IronChestHoldsItemsTest::new));

        public IronChestHoldsItemsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(SOURCE, ModBlocks.IRON_CHEST.get());
            helper.getBlockEntity(SOURCE, IronChestBlockEntity.class);

            // Through the capability, not the Container interface: NeoForge only wraps a
            // hard-coded list of vanilla block entity types, so a modded Container that forgets
            // to register one is invisible to every inserter in the game while looking fine.
            ResourceHandler<ItemResource> chest = container(helper, SOURCE);
            helper.assertValueEqual(chest.size(), IronChestBlockEntity.SLOT_COUNT, "slots on an iron chest");

            helper.assertValueEqual(insert(chest, Items.IRON_INGOT, 100), 100, "ingots accepted");
            helper.assertValueEqual(countIn(chest, Items.IRON_INGOT), 100, "ingots held");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("iron chest holds items");
        }
    }

    /**
     * Milestone 1, in the only three blocks that are ours: chest, inserter, chest.
     *
     * <p>Both containers here are iron chests rather than vanilla ones, which makes this a
     * different claim from {@code inserter_moves_items}. That one proves the inserter can talk to
     * a container somebody else wrote; this proves ours behaves like one - that it publishes its
     * capability, and that changing it wakes the inserter beside it the same way a vanilla chest
     * does.
     */
    public static class InserterFillsIronChestTest extends GameTestInstance {

        public static final MapCodec<InserterFillsIronChestTest> CODEC =
                RecordCodecBuilder.<InserterFillsIronChestTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterFillsIronChestTest::info))
                                .apply(i, InserterFillsIronChestTest::new));

        public InserterFillsIronChestTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(SOURCE, ModBlocks.IRON_CHEST.get());
            helper.setBlock(DESTINATION, ModBlocks.IRON_CHEST.get());
            helper.setBlock(INSERTER, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                    .setValue(InserterBlock.FACING, Direction.EAST));
            insert(helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class).fuelAccess(), Items.COAL, 1);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper), "the inserter never went to sleep to begin with");

                insert(container(helper, SOURCE), Items.IRON_INGOT, 1);
                helper.assertTrue(isScheduled(helper),
                        "an iron chest gaining an item did not wake the inserter beside it");

                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                    helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                            "ingots delivered into the iron chest");
                    helper.assertValueEqual(countIn(container(helper, SOURCE), Items.IRON_INGOT), 0,
                            "ingots left behind in the source chest");
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
            return Component.literal("inserter fills iron chest");
        }
    }

    /**
     * The electric inserter does the same job on electricity, and does it faster.
     *
     * <p>The speed is asserted rather than waited out. Twenty-four ticks against the burner's
     * thirty is the whole reason to build one, and a tier that quietly swings at the same rate as
     * the one it replaces would pass any test that only checked the item arrived.
     */
    public static class ElectricInserterMovesItemsTest extends GameTestInstance {

        public static final MapCodec<ElectricInserterMovesItemsTest> CODEC =
                RecordCodecBuilder.<ElectricInserterMovesItemsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ElectricInserterMovesItemsTest::info))
                                .apply(i, ElectricInserterMovesItemsTest::new));

        public ElectricInserterMovesItemsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildElectricLine(helper, true);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 1), 1,
                    "iron accepted by the source chest");

            helper.startSequence()
                    .thenExecuteAfter(BurnerInserterBlockEntity.SWING_TICKS - 4, () ->
                            helper.assertValueEqual(
                                    countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                                    "iron delivered by the time a burner would still be swinging"))
                    .thenExecute(() -> {
                        helper.assertValueEqual(countIn(container(helper, SOURCE), Items.IRON_INGOT), 0,
                                "iron left in the source chest");
                        helper.assertTrue(
                                helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class)
                                        .energyStored() < ElectricInserterBlockEntity.ENERGY_CAPACITY,
                                "the inserter swung without spending any electricity");
                    })
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("electric inserter moves items");
        }
    }

    /**
     * <b>Why this item waited for the grid.</b>
     *
     * <p>An electric inserter with no pole in range must be a paperweight - otherwise it is
     * strictly better than the burner for free, and the tier it is meant to be an upgrade from
     * has no reason to exist.
     *
     * <p>The second half is the wake, checked the same way the assembler's is: it has to be
     * asleep first, or the restart proves nothing.
     */
    public static class ElectricInserterNeedsPowerTest extends GameTestInstance {

        public static final MapCodec<ElectricInserterNeedsPowerTest> CODEC =
                RecordCodecBuilder.<ElectricInserterNeedsPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ElectricInserterNeedsPowerTest::info))
                                .apply(i, ElectricInserterNeedsPowerTest::new));

        public ElectricInserterNeedsPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildElectricLine(helper, false);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 1), 1,
                    "iron accepted by the source chest");

            helper.startSequence()
                    .thenExecuteAfter(ElectricInserterBlockEntity.SWING_TICKS * 2, () -> {
                        helper.assertValueEqual(
                                countIn(container(helper, DESTINATION), Items.IRON_INGOT), 0,
                                "items moved by an inserter with no electricity");
                        helper.assertFalse(isElectricScheduled(helper),
                                "an inserter with no electricity is still scheduled to tick");
                    })
                    .thenExecute(() -> {
                        charge(helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class));
                        helper.assertTrue(isElectricScheduled(helper),
                                "electricity arrived and the inserter was not woken - it will sleep "
                                        + "through the grid coming back");
                    })
                    .thenExecuteAfter(ElectricInserterBlockEntity.SWING_TICKS + 2, () ->
                            helper.assertValueEqual(
                                    countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                                    "items moved after the power came back"))
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("electric inserter needs power");
        }
    }
}
