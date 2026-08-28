package com.jaguarm.nauvislogistics;

import java.util.List;

import com.jaguarm.nauvislogistics.belt.BeltBlock;
import com.jaguarm.nauvislogistics.belt.BeltLane;
import com.jaguarm.nauvislogistics.belt.BeltLines;
import com.jaguarm.nauvislogistics.belt.BeltRun;
import com.jaguarm.nauvislogistics.belt.Belts;
import com.jaguarm.nauvislogistics.belt.TransportBeltBlock;
import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.InserterBlock;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
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
 * The belt, headless.
 *
 * <p>Three of these check things no amount of watching a belt would show, and they are the reason
 * this file is separate from the inserter's:
 *
 * <ul>
 *   <li>{@code belt_moves_at_factorio_speed} checks the number rather than the behaviour. A belt
 *       that carries items at the wrong speed works perfectly and quietly makes every ratio in a
 *       factory wrong.</li>
 *   <li>{@code belt_lanes_stay_apart} checks the thing that is expensive to add later. Two lanes
 *       is why an inserter takes from the far side and why half a belt of iron beside half a belt
 *       of copper is a thing players build.</li>
 *   <li>{@code belt_survives_being_cut} checks that items are pinned to blocks rather than to the
 *       line they happen to be on, which is what makes editing a belt line safe and what lets a
 *       client rebuild its own copy without being told anything.</li>
 * </ul>
 *
 * <p>Run with {@code ./gradlew :nauvis_logistics:runGameTestServer}, or {@code :nauvis:...} for the
 * whole pack.
 */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class NauvisLogisticsBeltGameTests {

    private NauvisLogisticsBeltGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** The tail of every belt line built here. Lines run east, along +X. */
    private static final BlockPos TAIL = new BlockPos(0, 1, 0);

    /**
     * A block entity registers itself from {@code onLoad}, which {@code Level.tickBlockEntities}
     * defers by a tick. So nothing here may look at a run in the tick it placed the belts.
     */
    private static final int SETTLED = 2;

    /** Room around each test. A belt line sprawls, and tests that touch cost the wrong one a fail. */
    private static final int PADDING = 12;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisLogistics.MODID);

    static {
        TEST_TYPES.register("belt_line_is_one_run", () -> LineIsOneRunTest.CODEC);
        TEST_TYPES.register("belt_carries_an_item", () -> CarriesAnItemTest.CODEC);
        TEST_TYPES.register("belt_moves_at_its_declared_speed", () -> FactorioSpeedTest.CODEC);
        TEST_TYPES.register("belt_lanes_stay_apart", () -> LanesStayApartTest.CODEC);
        TEST_TYPES.register("belt_holds_four_items_a_tile", () -> FourItemsATileTest.CODEC);
        TEST_TYPES.register("belt_sleeps_when_empty", () -> SleepsWhenEmptyTest.CODEC);
        TEST_TYPES.register("belt_backs_up_at_a_dead_end", () -> BacksUpTest.CODEC);
        TEST_TYPES.register("belt_does_not_load_a_chest", () -> DoesNotLoadAChestTest.CODEC);
        TEST_TYPES.register("belt_hands_to_the_next_line", () -> HandsOverTest.CODEC);
        TEST_TYPES.register("belt_survives_being_cut", () -> SurvivesBeingCutTest.CODEC);
        TEST_TYPES.register("belt_drops_what_it_carried", () -> DropsWhatItCarriedTest.CODEC);
        TEST_TYPES.register("belt_is_walked_over", () -> WalkedOverTest.CODEC);
        TEST_TYPES.register("belt_loop_carries_round", () -> LoopCarriesRoundTest.CODEC);
        TEST_TYPES.register("belt_carries_what_stands_on_it", () -> CarriesWhatStandsOnItTest.CODEC);
        TEST_TYPES.register("inserter_loads_a_belt", () -> InserterLoadsABeltTest.CODEC);
        TEST_TYPES.register("inserter_takes_from_a_belt", () -> InserterTakesFromABeltTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "belts"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "belt_line_is_one_run", LineIsOneRunTest::new, 60);
        register(event, environment, "belt_carries_an_item", CarriesAnItemTest::new, 200);
        register(event, environment, "belt_moves_at_its_declared_speed", FactorioSpeedTest::new, 200);
        register(event, environment, "belt_lanes_stay_apart", LanesStayApartTest::new, 60);
        register(event, environment, "belt_holds_four_items_a_tile", FourItemsATileTest::new, 60);
        register(event, environment, "belt_sleeps_when_empty", SleepsWhenEmptyTest::new, 60);
        register(event, environment, "belt_backs_up_at_a_dead_end", BacksUpTest::new, 200);
        register(event, environment, "belt_does_not_load_a_chest", DoesNotLoadAChestTest::new, 200);
        register(event, environment, "belt_hands_to_the_next_line", HandsOverTest::new, 200);
        register(event, environment, "belt_survives_being_cut", SurvivesBeingCutTest::new, 60);
        register(event, environment, "belt_drops_what_it_carried", DropsWhatItCarriedTest::new, 60);
        register(event, environment, "belt_is_walked_over", WalkedOverTest::new, 60);
        register(event, environment, "belt_loop_carries_round", LoopCarriesRoundTest::new, 200);
        register(event, environment, "belt_carries_what_stands_on_it",
                CarriesWhatStandsOnItTest::new, 200);
        register(event, environment, "inserter_loads_a_belt", InserterLoadsABeltTest::new, 200);
        register(event, environment, "inserter_takes_from_a_belt", InserterTakesFromABeltTest::new, 200);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, PADDING)));
    }

    // --- the scenery ------------------------------------------------------------------------------

    /** A straight line of belts running east from {@link #TAIL}. */
    private static void line(GameTestHelper helper, int length) {
        for (int i = 0; i < length; i++) {
            place(helper, TAIL.east(i), Direction.EAST);
        }
    }

    /** One belt, pointing a given way. */
    private static void place(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.TRANSPORT_BELT.get().defaultBlockState()
                .setValue(BeltBlock.FACING, facing));
    }

    private static BeltLines lines(GameTestHelper helper) {
        return BeltLines.of(helper.getLevel());
    }

    private static BeltRun runAt(GameTestHelper helper, BlockPos pos) {
        BeltRun run = lines(helper).runAt(helper.absolutePos(pos));
        helper.assertTrue(run != null, "expected a belt run at " + pos);
        return run;
    }

    /** The belt at {@code pos}, reached exactly the way an inserter beside it would reach it. */
    private static ResourceHandler<ItemResource> belt(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), side);
        helper.assertTrue(handler != null, "expected an item handler on the belt at " + pos);
        return handler;
    }

    private static ResourceHandler<ItemResource> container(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "expected an item handler at " + pos);
        return handler;
    }

    private static int put(ResourceHandler<ItemResource> handler, Item item, int count) {
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

    // --- the tests --------------------------------------------------------------------------------

    /** Five belts in a line are one object, not five. The claim the whole subsystem rests on. */
    public static class LineIsOneRunTest extends GameTestInstance {

        public static final MapCodec<LineIsOneRunTest> CODEC =
                RecordCodecBuilder.<LineIsOneRunTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LineIsOneRunTest::info))
                                .apply(i, LineIsOneRunTest::new));

        public LineIsOneRunTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 5);
            helper.runAfterDelay(SETTLED, () -> {
                BeltRun tail = runAt(helper, TAIL);
                helper.assertValueEqual(tail.blocks().size(), 5, "belts in the run");
                helper.assertTrue(runAt(helper, TAIL.east(4)) == tail,
                        "the far end of the line is a different run object from the near end");
                helper.assertValueEqual(tail.speed(), TransportBeltBlock.SPEED, "the run's speed");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a line of belts is one run");
        }
    }

    /** An item put on the near end arrives at the far end, and is still one item when it does. */
    public static class CarriesAnItemTest extends GameTestInstance {

        public static final MapCodec<CarriesAnItemTest> CODEC =
                RecordCodecBuilder.<CarriesAnItemTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(CarriesAnItemTest::info))
                                .apply(i, CarriesAnItemTest::new));

        public CarriesAnItemTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 5);
            helper.runAfterDelay(SETTLED, () -> {
                helper.assertValueEqual(put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1), 1,
                        "ingots put on the tail of the belt");

                // Five blocks at six sixty-fourths a tick is a little under sixty ticks, and it
                // starts three quarters of the way along the first one.
                helper.runAfterDelay(70, () -> {
                    helper.assertValueEqual(countIn(belt(helper, TAIL, null), Items.IRON_INGOT), 0,
                            "ingots still standing on the tail belt");
                    helper.assertValueEqual(countIn(belt(helper, TAIL.east(4), null), Items.IRON_INGOT), 1,
                            "ingots arrived at the head belt");
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
            return Component.literal("a belt carries an item to the far end");
        }
    }

    /**
     * An item travels exactly as far in a second as the belt it is on says it should.
     *
     * <p>Exactly, not approximately: this is what catches a run that rounds, drops or doubles a
     * step, and it would have caught the belt moving items one block a tick.
     *
     * <p><b>It does not check that six is the right number</b> - it reads the same constant the
     * belt does, so it would pass just as happily at any speed. The number is identity and is
     * checked where identity is checked: {@code data/mapping.json} records 1.875 tiles a second
     * for {@code transport-belt} and {@code tools/check_models.py} fails the build if
     * {@link TransportBeltBlock#SPEED} stops agreeing with it. Two halves of one guarantee: this
     * one says the belt obeys its speed, that one says the speed is Factorio's.
     */
    public static class FactorioSpeedTest extends GameTestInstance {

        public static final MapCodec<FactorioSpeedTest> CODEC =
                RecordCodecBuilder.<FactorioSpeedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FactorioSpeedTest::info))
                                .apply(i, FactorioSpeedTest::new));

        public FactorioSpeedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 8);
            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);

                helper.runAfterDelay(1, () -> {
                    BeltRun run = runAt(helper, TAIL);
                    int lane = run.lane(Belts.LEFT).isEmpty() ? Belts.RIGHT : Belts.LEFT;
                    int start = run.lane(lane).position(0);

                    helper.runAfterDelay(20, () -> {
                        BeltRun now = runAt(helper, TAIL);
                        helper.assertValueEqual(now.lane(lane).size(), 1, "items still on the lane");
                        helper.assertValueEqual(start - now.lane(lane).position(0),
                                TransportBeltBlock.SPEED * 20,
                                "sixty-fourths of a block travelled in one second");
                        helper.succeed();
                    });
                });
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt moves at its declared speed");
        }
    }

    /**
     * Two lanes, and things put on from opposite sides land on different ones.
     *
     * <p>Also that each goes on the <em>far</em> lane, which is Factorio's rule and the reason one
     * belt can feed two rows of machines.
     */
    public static class LanesStayApartTest extends GameTestInstance {

        public static final MapCodec<LanesStayApartTest> CODEC =
                RecordCodecBuilder.<LanesStayApartTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LanesStayApartTest::info))
                                .apply(i, LanesStayApartTest::new));

        public LanesStayApartTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            helper.runAfterDelay(SETTLED, () -> {
                // North of an eastbound belt is its left; an inserter there reaches the right lane.
                put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);
                put(belt(helper, TAIL, Direction.SOUTH), Items.COPPER_INGOT, 1);

                BeltRun run = runAt(helper, TAIL);
                helper.assertValueEqual(run.lane(Belts.RIGHT).size(), 1, "items on the right lane");
                helper.assertValueEqual(run.lane(Belts.LEFT).size(), 1, "items on the left lane");
                helper.assertTrue(
                        run.lane(Belts.RIGHT).item(0).getItem() == Items.IRON_INGOT,
                        "the item put on from the north did not land on the far lane");
                helper.assertTrue(
                        run.lane(Belts.LEFT).item(0).getItem() == Items.COPPER_INGOT,
                        "the item put on from the south did not land on the far lane");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("belt lanes stay apart");
        }
    }

    /** Four items to a tile a lane, eight in all - Factorio's spacing, and nothing more fits. */
    public static class FourItemsATileTest extends GameTestInstance {

        public static final MapCodec<FourItemsATileTest> CODEC =
                RecordCodecBuilder.<FourItemsATileTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FourItemsATileTest::info))
                                .apply(i, FourItemsATileTest::new));

        public FourItemsATileTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            helper.runAfterDelay(SETTLED, () -> {
                helper.assertValueEqual(put(belt(helper, TAIL, null), Items.IRON_INGOT, 64), 8,
                        "items a single belt tile accepted");
                BeltRun run = runAt(helper, TAIL);
                helper.assertValueEqual(run.lane(Belts.LEFT).size(), 4, "items on the left lane");
                helper.assertValueEqual(run.lane(Belts.RIGHT).size(), 4, "items on the right lane");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt tile holds four items a lane");
        }
    }

    /**
     * A belt with nothing on it is not visited at all, and one with something on it is.
     *
     * <p>Non-negotiable #5, and the belt's answer to it is unusual: a run is awake while it has
     * <em>items</em> rather than while it is <em>moving</em>, because a jammed run costs almost
     * nothing to tick. Asserting the empty case is what stops that becoming "always awake".
     */
    public static class SleepsWhenEmptyTest extends GameTestInstance {

        public static final MapCodec<SleepsWhenEmptyTest> CODEC =
                RecordCodecBuilder.<SleepsWhenEmptyTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SleepsWhenEmptyTest::info))
                                .apply(i, SleepsWhenEmptyTest::new));

        public SleepsWhenEmptyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 5);
            helper.runAfterDelay(SETTLED, () -> {
                helper.assertFalse(lines(helper).isActive(runAt(helper, TAIL)),
                        "an empty belt run is being ticked");

                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);
                helper.assertTrue(lines(helper).isActive(runAt(helper, TAIL)),
                        "a belt did not wake when something was put on it");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an empty belt run sleeps");
        }
    }

    /** Items pile up at the end of a line that goes nowhere, and none of them is lost. */
    public static class BacksUpTest extends GameTestInstance {

        public static final MapCodec<BacksUpTest> CODEC =
                RecordCodecBuilder.<BacksUpTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BacksUpTest::info))
                                .apply(i, BacksUpTest::new));

        public BacksUpTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 3);
            helper.runAfterDelay(SETTLED, () -> {
                int put = put(belt(helper, TAIL, null), Items.IRON_INGOT, 8);
                helper.assertValueEqual(put, 8, "items put on the tail tile");

                helper.runAfterDelay(120, () -> {
                    BeltRun run = runAt(helper, TAIL);
                    helper.assertValueEqual(run.itemCount(), 8, "items still on the line");
                    helper.assertValueEqual(run.lane(Belts.LEFT).lead(), 0,
                            "the leading item has not reached the end of the line");
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
            return Component.literal("a belt backs up at a dead end");
        }
    }

    /**
     * A belt running into a chest backs up rather than filling it.
     *
     * <p>Factorio's rule, and a deliberate departure from what a Minecraft player might expect. A
     * belt that loaded containers would make half the inserters in a base pointless.
     */
    public static class DoesNotLoadAChestTest extends GameTestInstance {

        public static final MapCodec<DoesNotLoadAChestTest> CODEC =
                RecordCodecBuilder.<DoesNotLoadAChestTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DoesNotLoadAChestTest::info))
                                .apply(i, DoesNotLoadAChestTest::new));

        public DoesNotLoadAChestTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 2);
            helper.setBlock(TAIL.east(2), Blocks.CHEST);
            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);

                helper.runAfterDelay(100, () -> {
                    helper.assertValueEqual(countIn(container(helper, TAIL.east(2)), Items.IRON_INGOT), 0,
                            "ingots the belt pushed into the chest");
                    helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 1,
                            "ingots still waiting on the belt");
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
            return Component.literal("a belt does not load a chest");
        }
    }

    /**
     * Two lines meeting a third: the merge is where a run ends and the next begins.
     *
     * <p>The belt coming in from the side loads onto the near lane, which is what side-loading is
     * and why a player can fill both lanes of one belt from two sources.
     */
    public static class HandsOverTest extends GameTestInstance {

        public static final MapCodec<HandsOverTest> CODEC =
                RecordCodecBuilder.<HandsOverTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(HandsOverTest::info))
                                .apply(i, HandsOverTest::new));

        public HandsOverTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos behind = TAIL;
            BlockPos side = TAIL.east().north();
            BlockPos join = TAIL.east();

            helper.setBlock(behind, ModBlocks.TRANSPORT_BELT.get().defaultBlockState()
                    .setValue(BeltBlock.FACING, Direction.EAST));
            helper.setBlock(side, ModBlocks.TRANSPORT_BELT.get().defaultBlockState()
                    .setValue(BeltBlock.FACING, Direction.SOUTH));
            helper.setBlock(join, ModBlocks.TRANSPORT_BELT.get().defaultBlockState()
                    .setValue(BeltBlock.FACING, Direction.EAST));

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertTrue(runAt(helper, behind) != runAt(helper, join),
                        "a belt with two belts feeding it should start a line of its own");
                helper.assertValueEqual(runAt(helper, join).blocks().size(), 1, "belts in the joining run");

                put(belt(helper, side, null), Items.COPPER_INGOT, 1);

                helper.runAfterDelay(40, () -> {
                    BeltRun joined = runAt(helper, join);
                    helper.assertValueEqual(joined.itemCount(), 1, "items handed to the next line");
                    helper.assertValueEqual(joined.lane(Belts.LEFT).size(), 1,
                            "an item side-loading from the north should be on the near lane, the left one");
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
            return Component.literal("a belt hands over to the next line");
        }
    }

    /**
     * <b>Cutting a line in half keeps everything standing where it was.</b>
     *
     * <p>Items are pinned to a block and an offset into it, not to a distance along whichever run
     * they happen to be on - so joining, splitting and lengthening a line all leave them alone.
     * That is also why a client can rebuild its own copy of a belt line from block states and be
     * told nothing at all.
     */
    public static class SurvivesBeingCutTest extends GameTestInstance {

        public static final MapCodec<SurvivesBeingCutTest> CODEC =
                RecordCodecBuilder.<SurvivesBeingCutTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SurvivesBeingCutTest::info))
                                .apply(i, SurvivesBeingCutTest::new));

        public SurvivesBeingCutTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 5);
            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);
                put(belt(helper, TAIL.east(4), null), Items.COPPER_INGOT, 1);

                BeltRun before = runAt(helper, TAIL);
                int offset = before.lane(Belts.LEFT).position(before.lane(Belts.LEFT).size() - 1)
                        - before.frontEdge(0);

                // Broken and inspected within the tick, so nothing has had a chance to move and
                // the offsets can be compared exactly.
                helper.setBlock(TAIL.east(2), Blocks.AIR);

                {
                    BeltRun near = runAt(helper, TAIL);
                    BeltRun far = runAt(helper, TAIL.east(3));
                    helper.assertTrue(near != far, "cutting a line left one run, not two");
                    helper.assertValueEqual(near.blocks().size(), 2, "belts in the near half");
                    helper.assertValueEqual(far.blocks().size(), 2, "belts in the far half");
                    helper.assertValueEqual(near.itemCount() + far.itemCount(), 2,
                            "items still on a belt after the line was cut");

                    // The near item was standing on the tail block and is still standing on it,
                    // the same distance along - even though the line it is on is now three
                    // blocks shorter and every distance is measured from a new place.
                    helper.assertValueEqual(
                            near.lane(Belts.LEFT).position(0) - near.frontEdge(0), offset,
                            "where the item stands on its own block");
                    helper.succeed();
                }
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt line survives being cut");
        }
    }

    /** Breaking a belt drops what was standing on it, rather than eating it. */
    public static class DropsWhatItCarriedTest extends GameTestInstance {

        public static final MapCodec<DropsWhatItCarriedTest> CODEC =
                RecordCodecBuilder.<DropsWhatItCarriedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DropsWhatItCarriedTest::info))
                                .apply(i, DropsWhatItCarriedTest::new));

        public DropsWhatItCarriedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);
                helper.destroyBlock(TAIL);
                helper.runAfterDelay(2, () -> {
                    helper.assertItemEntityPresent(Items.IRON_INGOT, TAIL, 2.0);
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
            return Component.literal("a broken belt drops what it carried");
        }
    }

    /**
     * Half a block high, so a player walks over a belt rather than jumping onto it.
     *
     * <p>Identity of a different kind: a belt you have to jump is not a belt, and a base laid out
     * the way Factorio lays them out has belts everywhere you want to walk.
     */
    public static class WalkedOverTest extends GameTestInstance {

        public static final MapCodec<WalkedOverTest> CODEC =
                RecordCodecBuilder.<WalkedOverTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(WalkedOverTest::info))
                                .apply(i, WalkedOverTest::new));

        public WalkedOverTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            BlockPos pos = helper.absolutePos(TAIL);
            double top = helper.getLevel().getBlockState(pos)
                    .getCollisionShape(helper.getLevel(), pos).max(Direction.Axis.Y);
            // Vanilla's step height is 0.6, so anything up to that is walked across.
            helper.assertTrue(top <= 0.5 + 1.0E-6,
                    "a belt stands " + top + " blocks high, which is more than a player steps over");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt is walked over");
        }
    }

    /**
     * <b>Four belts turning in a square carry an item round and round, and it comes back where it
     * started.</b>
     *
     * <p>A ring of belts has no beginning, so the run is broken open at an arbitrary block and the
     * two ends joined back up - see {@code BeltRun.loops()}. Get that wrong and an item leaving the
     * head is handed to the run's own tail as though it were a stranger, which lands it in the
     * middle of that block rather than at the edge it just crossed: items vanish at one corner and
     * appear out of the middle of another. It read as a tunnel to the centre of the square.
     *
     * <p>So this measures where an item <em>is</em>, through the same {@code pointAt} the renderer
     * draws it with, one lap later. Half a block of drift a lap is what the bug cost.
     */
    public static class LoopCarriesRoundTest extends GameTestInstance {

        public static final MapCodec<LoopCarriesRoundTest> CODEC =
                RecordCodecBuilder.<LoopCarriesRoundTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(LoopCarriesRoundTest::info))
                                .apply(i, LoopCarriesRoundTest::new));

        public LoopCarriesRoundTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos corner = TAIL;
            place(helper, corner, Direction.EAST);
            place(helper, corner.east(), Direction.SOUTH);
            place(helper, corner.east().south(), Direction.WEST);
            place(helper, corner.south(), Direction.NORTH);

            helper.runAfterDelay(SETTLED, () -> {
                BeltRun run = runAt(helper, corner);
                helper.assertValueEqual(run.blocks().size(), 4, "belts in the ring");
                helper.assertTrue(run.loops(), "four belts in a square did not read as a loop");

                put(belt(helper, corner, null), Items.IRON_INGOT, 1);
                BeltLane lane = run.lane(Belts.LEFT);
                helper.assertValueEqual(lane.size(), 1, "items on the loop");
                Vec3 started = run.pointAt(lane.position(0), Belts.LEFT);

                // A lap is four blocks of sixty-four units at six a tick, so forty-three ticks
                // carries it round once and two units further - a thirtieth of a block.
                helper.runAfterDelay(43, () -> {
                    BeltRun now = runAt(helper, corner);
                    helper.assertValueEqual(now.itemCount(), 1, "items still going round");
                    Vec3 ended = now.pointAt(now.lane(Belts.LEFT).position(0), Belts.LEFT);
                    double drift = ended.distanceTo(started);
                    helper.assertTrue(drift < 0.2,
                            "an item is " + drift + " blocks from where it set off a lap ago, so the "
                                    + "loop is losing ground where it joins back up");
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
            return Component.literal("a loop of belts carries an item round");
        }
    }

    /**
     * A belt carries what is standing on it, which in Factorio includes the player.
     *
     * <p>A dropped item stands in for one here - a gametest has no player to walk about, and it is
     * the same {@code stepOn} hook either way. What it pins is that the hook fires at all: a belt
     * is a bottom slab, so anything on top of it is inside the block <em>above</em> and the obvious
     * {@code entityInside} never runs.
     */
    public static class CarriesWhatStandsOnItTest extends GameTestInstance {

        public static final MapCodec<CarriesWhatStandsOnItTest> CODEC =
                RecordCodecBuilder.<CarriesWhatStandsOnItTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(CarriesWhatStandsOnItTest::info))
                                .apply(i, CarriesWhatStandsOnItTest::new));

        public CarriesWhatStandsOnItTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 8);
            helper.runAfterDelay(SETTLED, () -> {
                // Over the middle of the tile, not its corner: spawnItem(Item, BlockPos) takes the
                // block's corner, which would leave half of the item hanging over the gap beside
                // the belt.
                ItemEntity rider = helper.spawnItem(Items.IRON_INGOT,
                        TAIL.getX() + 0.5F, TAIL.getY() + 1.0F, TAIL.getZ() + 0.5F);
                double from = rider.getX();

                // Long enough to fall the half block onto the belt and then be carried. A belt
                // moves 1.875 blocks a second, so two blocks in three seconds is a low bar.
                helper.runAfterDelay(60, () -> {
                    helper.assertTrue(rider.isAlive(), "the item fell out of the world");
                    double carried = rider.getX() - from;
                    helper.assertTrue(carried > 2.0,
                            "something standing on an eastbound belt moved " + carried
                                    + " blocks east (resting at y=" + rider.getY() + ", on the ground: "
                                    + rider.onGround() + "), so the belt is not carrying it");
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
            return Component.literal("a belt carries what stands on it");
        }
    }

    /** The joint that matters: an inserter fills a belt without knowing what a belt is. */
    public static class InserterLoadsABeltTest extends GameTestInstance {

        public static final MapCodec<InserterLoadsABeltTest> CODEC =
                RecordCodecBuilder.<InserterLoadsABeltTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterLoadsABeltTest::info))
                                .apply(i, InserterLoadsABeltTest::new));

        public InserterLoadsABeltTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos chest = new BlockPos(0, 1, 2);
            BlockPos inserter = new BlockPos(0, 1, 1);

            line(helper, 3);
            helper.setBlock(chest, Blocks.CHEST);
            helper.setBlock(inserter, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                    .setValue(InserterBlock.FACING, Direction.NORTH));

            helper.runAfterDelay(SETTLED, () -> {
                put(container(helper, chest), Items.IRON_INGOT, 3);
                put(helper.getBlockEntity(inserter, BurnerInserterBlockEntity.class).fuelAccess(),
                        Items.COAL, 1);

                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 10, () -> {
                    helper.assertTrue(runAt(helper, TAIL).itemCount() >= 1,
                            "the inserter put nothing on the belt");
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
            return Component.literal("an inserter loads a belt");
        }
    }

    /** And the other way: an inserter takes an item off a belt and puts it in a chest. */
    public static class InserterTakesFromABeltTest extends GameTestInstance {

        public static final MapCodec<InserterTakesFromABeltTest> CODEC =
                RecordCodecBuilder.<InserterTakesFromABeltTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(InserterTakesFromABeltTest::info))
                                .apply(i, InserterTakesFromABeltTest::new));

        public InserterTakesFromABeltTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos inserter = new BlockPos(0, 1, 1);
            BlockPos chest = new BlockPos(0, 1, 2);

            // One belt, so the item jams at its own end and is still there for the swing.
            line(helper, 1);
            helper.setBlock(inserter, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                    .setValue(InserterBlock.FACING, Direction.SOUTH));
            helper.setBlock(chest, Blocks.CHEST);

            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);
                put(helper.getBlockEntity(inserter, BurnerInserterBlockEntity.class).fuelAccess(),
                        Items.COAL, 1);

                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 10, () -> {
                    helper.assertValueEqual(countIn(container(helper, chest), Items.IRON_INGOT), 1,
                            "ingots the inserter took off the belt");
                    helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 0,
                            "items left on the belt");
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
            return Component.literal("an inserter takes from a belt");
        }
    }
}
