package com.jaguarm.nauvislogistics;

import java.util.List;

import com.jaguarm.nauvislogistics.belt.BeltBlock;
import com.jaguarm.nauvislogistics.belt.BeltLane;
import com.jaguarm.nauvislogistics.belt.BeltLines;
import com.jaguarm.nauvislogistics.belt.BeltRun;
import com.jaguarm.nauvislogistics.belt.BeltShape;
import com.jaguarm.nauvislogistics.belt.FastSplitterBlock;
import com.jaguarm.nauvislogistics.belt.FastTransportBeltBlock;
import com.jaguarm.nauvislogistics.belt.Belts;
import com.jaguarm.nauvislogistics.belt.BeltLines;
import com.jaguarm.nauvislogistics.belt.SplitterBlock;
import com.jaguarm.nauvislogistics.belt.SplitterBlockEntity;
import com.jaguarm.nauvislogistics.belt.SplitterShape;
import com.jaguarm.nauvislogistics.belt.TransportBeltBlock;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.registry.ModItems;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.InserterBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        TEST_TYPES.register("belt_bends_the_way_it_carries", () -> BendsTheWayItCarriesTest.CODEC);
        TEST_TYPES.register("belt_turns_when_clicked_with_a_belt", () -> TurnsWhenClickedTest.CODEC);
        TEST_TYPES.register("fast_belt_moves_at_its_declared_speed", () -> FastBeltSpeedTest.CODEC);
        TEST_TYPES.register("belt_is_replaced_by_another_tier", () -> ReplacedByAnotherTierTest.CODEC);
        TEST_TYPES.register("belt_is_replaced_by_a_slower_belt", () -> ReplacedByASlowerBeltTest.CODEC);
        TEST_TYPES.register("belt_tiers_meet_as_two_runs", () -> TiersMeetTest.CODEC);
        TEST_TYPES.register("belt_climbs_a_step", () -> ClimbsAStepTest.CODEC);
        TEST_TYPES.register("belt_descends_a_step", () -> DescendsAStepTest.CODEC);
        TEST_TYPES.register("belt_slope_carries_at_its_height", () -> SlopeCarriesAtHeightTest.CODEC);
        TEST_TYPES.register("belt_slope_beats_a_bend", () -> SlopeBeatsABendTest.CODEC);
        TEST_TYPES.register("belt_slope_risers_are_climbable", () -> SlopeRisersTest.CODEC);
        TEST_TYPES.register("inserter_loads_a_belt", () -> InserterLoadsABeltTest.CODEC);
        TEST_TYPES.register("inserter_takes_from_a_belt", () -> InserterTakesFromABeltTest.CODEC);
        TEST_TYPES.register("inserter_fills_only_the_far_lane", () -> OnlyTheFarLaneTest.CODEC);
        TEST_TYPES.register("inserter_takes_the_near_lane_first", () -> NearLaneFirstTest.CODEC);
        TEST_TYPES.register("burner_inserter_fuels_itself_from_a_belt",
                () -> FuelsItselfFromABeltTest.CODEC);
        TEST_TYPES.register("belt_wakes_an_inserter_when_an_item_arrives",
                () -> BeltWakesAnInserterTest.CODEC);
        TEST_TYPES.register("splitter_splits_single_belt_evenly",
                () -> SplitterSplitsSingleBeltEvenlyTest.CODEC);
        TEST_TYPES.register("splitter_merges_two_belts",
                () -> SplitterMergesTwoBeltsTest.CODEC);
        TEST_TYPES.register("splitter_overflows_when_one_output_is_blocked",
                () -> SplitterOverflowsWhenBlockedTest.CODEC);
        TEST_TYPES.register("splitter_preserves_lanes",
                () -> SplitterPreservesLanesTest.CODEC);
        TEST_TYPES.register("splitter_sleeps_when_empty",
                () -> SplitterSleepsWhenEmptyTest.CODEC);
        TEST_TYPES.register("splitter_is_two_by_one_and_breaks_together",
                () -> SplitterBreaksTogetherTest.CODEC);
        TEST_TYPES.register("fast_splitter_moves_at_its_declared_speed",
                () -> FastSplitterSpeedTest.CODEC);
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
        register(event, environment, "belt_bends_the_way_it_carries",
                BendsTheWayItCarriesTest::new, 60);
        register(event, environment, "belt_turns_when_clicked_with_a_belt",
                TurnsWhenClickedTest::new, 60);
        register(event, environment, "fast_belt_moves_at_its_declared_speed",
                FastBeltSpeedTest::new, 200);
        register(event, environment, "belt_is_replaced_by_another_tier",
                ReplacedByAnotherTierTest::new, 60);
        register(event, environment, "belt_is_replaced_by_a_slower_belt",
                ReplacedByASlowerBeltTest::new, 60);
        register(event, environment, "belt_tiers_meet_as_two_runs", TiersMeetTest::new, 200);
        register(event, environment, "belt_climbs_a_step", ClimbsAStepTest::new, 200);
        register(event, environment, "belt_descends_a_step", DescendsAStepTest::new, 200);
        register(event, environment, "belt_slope_carries_at_its_height",
                SlopeCarriesAtHeightTest::new, 60);
        register(event, environment, "belt_slope_beats_a_bend", SlopeBeatsABendTest::new, 60);
        register(event, environment, "belt_slope_risers_are_climbable", SlopeRisersTest::new, 60);
        register(event, environment, "inserter_loads_a_belt", InserterLoadsABeltTest::new, 200);
        register(event, environment, "inserter_takes_from_a_belt", InserterTakesFromABeltTest::new, 200);
        register(event, environment, "inserter_fills_only_the_far_lane", OnlyTheFarLaneTest::new, 60);
        register(event, environment, "inserter_takes_the_near_lane_first", NearLaneFirstTest::new, 60);
        register(event, environment, "burner_inserter_fuels_itself_from_a_belt",
                FuelsItselfFromABeltTest::new, 200);
        register(event, environment, "belt_wakes_an_inserter_when_an_item_arrives",
                BeltWakesAnInserterTest::new, 200);
        register(event, environment, "splitter_splits_single_belt_evenly",
                SplitterSplitsSingleBeltEvenlyTest::new, 200);
        register(event, environment, "splitter_merges_two_belts",
                SplitterMergesTwoBeltsTest::new, 200);
        register(event, environment, "splitter_overflows_when_one_output_is_blocked",
                SplitterOverflowsWhenBlockedTest::new, 200);
        register(event, environment, "splitter_preserves_lanes",
                SplitterPreservesLanesTest::new, 200);
        register(event, environment, "splitter_sleeps_when_empty",
                SplitterSleepsWhenEmptyTest::new, 200);
        register(event, environment, "splitter_is_two_by_one_and_breaks_together",
                SplitterBreaksTogetherTest::new, 60);
        register(event, environment, "fast_splitter_moves_at_its_declared_speed",
                FastSplitterSpeedTest::new, 60);
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
        line(helper, length, ModBlocks.TRANSPORT_BELT.get());
    }

    /** The same, in a named tier. */
    private static void line(GameTestHelper helper, int length, Block tier) {
        for (int i = 0; i < length; i++) {
            place(helper, TAIL.east(i), Direction.EAST, tier);
        }
    }

    /** A belt item, the thing you hold to point a belt somewhere. */
    private static ItemStack belt() {
        return new ItemStack(ModItems.TRANSPORT_BELT.get());
    }

    /** A red belt item, the thing you hold to make a line faster. */
    private static ItemStack fastBelt() {
        return new ItemStack(ModItems.FAST_TRANSPORT_BELT.get());
    }

    /** One belt, pointing a given way. */
    private static void place(GameTestHelper helper, BlockPos pos, Direction facing) {
        place(helper, pos, facing, ModBlocks.TRANSPORT_BELT.get());
    }

    private static void place(GameTestHelper helper, BlockPos pos, Direction facing, Block tier) {
        helper.setBlock(pos, tier.defaultBlockState().setValue(BeltBlock.FACING, facing));
    }

    /**
     * One second of a belt of the given tier, against the tier's own speed.
     *
     * <p>Shared by the two speed tests rather than written twice, because the claim is the same
     * one: a run carries things at the speed of the block it is made of.
     */
    private static void movesAtItsSpeed(GameTestHelper helper, Block tier, int speed) {
        line(helper, 8, tier);
        helper.runAfterDelay(SETTLED, () -> {
            put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);

            helper.runAfterDelay(1, () -> {
                BeltRun run = runAt(helper, TAIL);
                int lane = run.lane(Belts.LEFT).isEmpty() ? Belts.RIGHT : Belts.LEFT;
                int start = run.lane(lane).position(0);

                helper.runAfterDelay(20, () -> {
                    BeltRun now = runAt(helper, TAIL);
                    helper.assertValueEqual(now.lane(lane).size(), 1, "items still on the lane");
                    helper.assertValueEqual(start - now.lane(lane).position(0), speed * 20,
                            "sixty-fourths of a block travelled in one second");
                    helper.succeed();
                });
            });
        });
    }

    /** How many of an item a player is holding, anywhere in their inventory. */
    private static int carrying(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
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

    /**
     * Right-clicks a block with something in hand, the way a player's click arrives at it.
     *
     * @param facing which way the player is looking, which is what a belt takes from the click.
     */
    private static Player click(GameTestHelper helper, BlockPos pos, ItemStack held, Direction facing) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setYRot(facing.toYRot());
        BlockPos absolute = helper.absolutePos(pos);
        helper.getLevel().getBlockState(absolute).useItemOn(held, helper.getLevel(), player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        return player;
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
            movesAtItsSpeed(helper, ModBlocks.TRANSPORT_BELT.get(), TransportBeltBlock.SPEED);
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
     * A corner is drawn bending the way its items actually travel.
     *
     * <p>Worth a test of its own because getting it back to front is invisible to every other one
     * here: the items still go round, at the right speed, on the right lane. It is only wrong to
     * look at - and a belt that looks wrong is one a player rips up and lays again, which is the
     * failure mode the rotation bug in the boiler had.
     *
     * <p>Also that two feeders is <em>not</em> a bend. Factorio draws a side-load as a straight
     * belt something joins, and a bend there would say the line goes somewhere it does not.
     */
    public static class BendsTheWayItCarriesTest extends GameTestInstance {

        public static final MapCodec<BendsTheWayItCarriesTest> CODEC =
                RecordCodecBuilder.<BendsTheWayItCarriesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BendsTheWayItCarriesTest::info))
                                .apply(i, BendsTheWayItCarriesTest::new));

        public BendsTheWayItCarriesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // Coming in from the west, leaving to the north: the items arrive on the corner's left.
            BlockPos corner = TAIL.east();
            place(helper, TAIL, Direction.EAST);
            place(helper, corner, Direction.NORTH);

            // And the mirror of it, well clear: in from the east, out to the north.
            BlockPos mirror = TAIL.south(4).east();
            place(helper, mirror.east(), Direction.WEST);
            place(helper, mirror, Direction.NORTH);

            // A plain line, which must stay straight.
            BlockPos straight = TAIL.south(8);
            place(helper, straight, Direction.EAST);
            place(helper, straight.east(), Direction.EAST);

            // And a side-load: fed from behind and from the side at once.
            BlockPos joined = TAIL.south(12).east();
            place(helper, joined, Direction.EAST);
            place(helper, joined.west(), Direction.EAST);
            place(helper, joined.north(), Direction.SOUTH);

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertBlockProperty(corner, BeltBlock.SHAPE, BeltShape.FROM_LEFT);
                helper.assertBlockProperty(mirror, BeltBlock.SHAPE, BeltShape.FROM_RIGHT);
                helper.assertBlockProperty(straight.east(), BeltBlock.SHAPE, BeltShape.STRAIGHT);
                helper.assertBlockProperty(joined, BeltBlock.SHAPE, BeltShape.STRAIGHT);

                // The one that feeds the corner is not itself a corner.
                helper.assertBlockProperty(TAIL, BeltBlock.SHAPE, BeltShape.STRAIGHT);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt bends the way it carries");
        }
    }

    /**
     * A belt in hand points the belt you click on the way you are facing, and the line follows it.
     *
     * <p>The turn itself is the easy half. The half worth a test is that <b>the run is rebuilt</b>:
     * turning a belt only changes a block state, so its block entity is never removed and neither
     * of the two hooks that maintain the graph fires - while the lines through it are now entirely
     * different lines. Miss that and a belt visibly points one way while carrying things another,
     * which is the worst kind of wrong: it looks like a rendering bug and is not one.
     *
     * <p>And that what was standing on it is still standing on it afterwards.
     */
    public static class TurnsWhenClickedTest extends GameTestInstance {

        public static final MapCodec<TurnsWhenClickedTest> CODEC =
                RecordCodecBuilder.<TurnsWhenClickedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TurnsWhenClickedTest::info))
                                .apply(i, TurnsWhenClickedTest::new));

        public TurnsWhenClickedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 3);
            helper.runAfterDelay(SETTLED, () -> {
                helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 3, "belts in the line");
                put(belt(helper, TAIL, null), Items.IRON_INGOT, 1);

                // Clicked by a player looking south, so it points south - the same direction a
                // belt placed there by that player would have.
                click(helper, TAIL.east(), belt(), Direction.SOUTH);
                helper.assertBlockProperty(TAIL.east(), BeltBlock.FACING, Direction.SOUTH);

                // The line did not break, it bent: the belt behind still feeds the turned one, so
                // the run follows it round and the belt beyond it is left on its own.
                helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 2, "belts in the bent line");
                helper.assertValueEqual(runAt(helper, TAIL.east(2)).blocks().size(), 1,
                        "belts in what is left beyond the turn");
                helper.assertBlockProperty(TAIL.east(), BeltBlock.SHAPE, BeltShape.FROM_RIGHT);
                helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 1,
                        "items still on the belt they were standing on");

                // Clicking it again from the same place changes nothing, which is what makes
                // running a belt along a row you have already built safe.
                click(helper, TAIL.east(), belt(), Direction.SOUTH);
                helper.assertBlockProperty(TAIL.east(), BeltBlock.FACING, Direction.SOUTH);
                helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 1, "items after a second click");

                // And facing back along the line puts it back, in one click rather than three.
                click(helper, TAIL.east(), belt(), Direction.EAST);
                helper.assertBlockProperty(TAIL.east(), BeltBlock.FACING, Direction.EAST);
                helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 3,
                        "belts in the line once the turned belt points along it again");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a belt in hand turns a belt");
        }
    }

    /**
     * The red belt, at its own speed.
     *
     * <p>Not a duplicate of {@link FactorioSpeedTest}: that one holds the yellow belt to Factorio's
     * figure, and this one holds the <em>run</em> to whichever belt it is made of. Speed lives on
     * the block and the run reads it once when it is built, so a run that had kept a constant of
     * its own would carry red belts at yellow speed and pass every other test in this file.
     */
    public static class FastBeltSpeedTest extends GameTestInstance {

        public static final MapCodec<FastBeltSpeedTest> CODEC =
                RecordCodecBuilder.<FastBeltSpeedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FastBeltSpeedTest::info))
                                .apply(i, FastBeltSpeedTest::new));

        public FastBeltSpeedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            movesAtItsSpeed(helper, ModBlocks.FAST_TRANSPORT_BELT.get(), FastTransportBeltBlock.SPEED);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a fast belt moves at twice a belt's speed");
        }
    }

    /**
     * Fast-replace: a belt of another tier in hand becomes the belt you clicked on.
     *
     * <p>Four claims, and all four are things that would go wrong quietly. The block is swapped
     * rather than a property set; it points the way the player is facing, exactly as it would have
     * done if they had placed it on bare ground; what was standing on the belt is still standing on
     * it afterwards rather than on the floor; and it is paid for, one belt off the stack and the
     * old one back.
     */
    public static class ReplacedByAnotherTierTest extends GameTestInstance {

        public static final MapCodec<ReplacedByAnotherTierTest> CODEC =
                RecordCodecBuilder.<ReplacedByAnotherTierTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ReplacedByAnotherTierTest::info))
                                .apply(i, ReplacedByAnotherTierTest::new));

        public ReplacedByAnotherTierTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos head = TAIL.east(2);
            line(helper, 3);
            helper.runAfterDelay(SETTLED, () -> {
                helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 3, "belts in the line");
                // On the head of the line, which has nothing beyond it, so the item stays where it
                // is put and this test is about the replacement rather than about timing.
                put(belt(helper, head, Direction.NORTH), Items.IRON_INGOT, 1);

                // Clicked by a player looking south, so it ends up facing south - a belt in hand
                // places a belt, and the tier is the only thing that makes this path different
                // from turning one.
                ItemStack held = new ItemStack(ModItems.FAST_TRANSPORT_BELT.get(), 2);
                Player player = click(helper, head, held, Direction.SOUTH);

                helper.assertBlockPresent(ModBlocks.FAST_TRANSPORT_BELT.get(), head);
                helper.assertBlockProperty(head, BeltBlock.FACING, Direction.SOUTH);
                helper.assertValueEqual(held.getCount(), 1, "fast belts left in hand");
                helper.assertValueEqual(carrying(player, ModItems.TRANSPORT_BELT.get()), 1,
                        "belts handed back for the one replaced");

                // A tick for the new block entity to join the graph, which is what every belt
                // placement costs - see SETTLED.
                helper.runAfterDelay(SETTLED, () -> {
                    helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 2,
                            "belts left on the slow line behind the replacement");
                    BeltRun replaced = runAt(helper, head);
                    helper.assertValueEqual(replaced.blocks().size(), 1, "belts in the new run");
                    helper.assertValueEqual(replaced.itemCount(), 1,
                            "items still standing where they stood before the replacement");
                    // The one failure this whole path exists to avoid: replacing the block runs
                    // preRemoveSideEffects, which spills what is standing on a belt.
                    helper.assertItemEntityNotPresent(Items.IRON_INGOT, head, 2.0);
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
            return Component.literal("a belt of another tier in hand replaces a belt");
        }
    }

    /**
     * And it goes down as readily as up.
     *
     * <p>Factorio's rule, and the reason to have it is that the alternative is a gesture with a
     * condition on it: a belt in hand is a belt you are placing, whichever belt it is. A downgrade
     * is the same code path as an upgrade, so what this pins is the decision rather than the
     * mechanism.
     */
    public static class ReplacedByASlowerBeltTest extends GameTestInstance {

        public static final MapCodec<ReplacedByASlowerBeltTest> CODEC =
                RecordCodecBuilder.<ReplacedByASlowerBeltTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ReplacedByASlowerBeltTest::info))
                                .apply(i, ReplacedByASlowerBeltTest::new));

        public ReplacedByASlowerBeltTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 3, ModBlocks.FAST_TRANSPORT_BELT.get());
            helper.runAfterDelay(SETTLED, () -> {
                ItemStack held = new ItemStack(ModItems.TRANSPORT_BELT.get(), 2);
                Player player = click(helper, TAIL.east(), held, Direction.EAST);

                helper.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), TAIL.east());
                helper.assertBlockProperty(TAIL.east(), BeltBlock.FACING, Direction.EAST);
                helper.assertValueEqual(held.getCount(), 1, "belts left in hand");
                helper.assertValueEqual(carrying(player, ModItems.FAST_TRANSPORT_BELT.get()), 1,
                        "fast belts handed back for the one replaced");

                helper.runAfterDelay(SETTLED, () -> {
                    // One yellow belt between two red ones is three runs, because a run follows
                    // one tier.
                    helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 1,
                            "belts in the fast run behind");
                    helper.assertValueEqual(runAt(helper, TAIL.east()).blocks().size(), 1,
                            "belts in the slow one put in the middle of it");
                    helper.assertValueEqual(runAt(helper, TAIL.east(2)).blocks().size(), 1,
                            "belts in the fast run beyond");
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
            return Component.literal("a slower belt in hand replaces a belt too");
        }
    }

    /**
     * Where two tiers meet: two runs, and the items cross.
     *
     * <p>A run has one speed, so it cannot span a tier change - {@link BeltLines} follows a line
     * only through belts of the same block, and the boundary is a hand-off exactly like the one at
     * a merge. That was written when there was one tier to try it with; this is the test.
     */
    public static class TiersMeetTest extends GameTestInstance {

        public static final MapCodec<TiersMeetTest> CODEC =
                RecordCodecBuilder.<TiersMeetTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(TiersMeetTest::info))
                                .apply(i, TiersMeetTest::new));

        public TiersMeetTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos join = TAIL.east(3);
            for (int i = 0; i < 3; i++) {
                place(helper, TAIL.east(i), Direction.EAST);
                place(helper, join.east(i), Direction.EAST, ModBlocks.FAST_TRANSPORT_BELT.get());
            }

            helper.runAfterDelay(SETTLED, () -> {
                BeltRun slow = runAt(helper, TAIL);
                BeltRun fast = runAt(helper, join);
                helper.assertTrue(slow != fast, "two tiers meeting should be two runs");
                helper.assertValueEqual(slow.blocks().size(), 3, "belts in the slow run");
                helper.assertValueEqual(fast.blocks().size(), 3, "belts in the fast run");

                put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);

                helper.runAfterDelay(45, () -> {
                    helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 0,
                            "items left behind on the slow line");
                    BeltRun arrived = runAt(helper, join);
                    helper.assertValueEqual(arrived.itemCount(), 1, "items handed to the fast line");
                    // Put on from the north, so it is on the far lane - the right one - and it is
                    // still there after crossing, because going straight in at the back of a tile
                    // does not swap lanes over.
                    helper.assertValueEqual(arrived.lane(Belts.RIGHT).size(), 1,
                            "an item going straight in at the back keeps the lane it was on");
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
            return Component.literal("two belt tiers meet as two runs");
        }
    }

    /**
     * A line that climbs a step, built the way a player builds one: the top belt last.
     *
     * <p>The order is the test. Placing the belt above turns the belt below it into a ramp, and
     * that is a <em>block state</em> change on a belt whose block entity is not touched - so the
     * run it belongs to was built before it, from the shape it had then. Nothing but
     * {@code BeltBlockEntity.setBlockState} notices, and if it did not, the line would carry items
     * along a ramp it did not know was a ramp.
     */
    public static class ClimbsAStepTest extends GameTestInstance {

        public static final MapCodec<ClimbsAStepTest> CODEC =
                RecordCodecBuilder.<ClimbsAStepTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(ClimbsAStepTest::info))
                                .apply(i, ClimbsAStepTest::new));

        public ClimbsAStepTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos ramp = TAIL.east();
            BlockPos top = TAIL.east(2).above();

            place(helper, TAIL, Direction.EAST);
            place(helper, ramp, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                // Flat while there is nothing above to climb to, and two belts of one run.
                helper.assertBlockProperty(ramp, BeltBlock.SHAPE, BeltShape.STRAIGHT);
                helper.assertValueEqual(runAt(helper, TAIL).blocks().size(), 2, "belts before the climb");

                place(helper, top, Direction.EAST);
                place(helper, top.east(), Direction.EAST);

                helper.runAfterDelay(SETTLED, () -> {
                    helper.assertBlockProperty(ramp, BeltBlock.SHAPE, BeltShape.UP);
                    helper.assertBlockProperty(top, BeltBlock.SHAPE, BeltShape.STRAIGHT);

                    BeltRun run = runAt(helper, TAIL);
                    helper.assertValueEqual(run.blocks().size(), 4, "belts in the climbing line");
                    helper.assertTrue(run == runAt(helper, top),
                            "a line that changes level should still be one run");

                    put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);
                    helper.runAfterDelay(60, () -> {
                        helper.assertValueEqual(runAt(helper, TAIL).itemCount(), 1,
                                "items still on the line after climbing it");
                        helper.assertValueEqual(
                                runAt(helper, TAIL).lane(Belts.RIGHT).lead(), 0,
                                "sixty-fourths the leading item is short of the top of the climb");
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
            return Component.literal("a belt line climbs a step");
        }
    }

    /**
     * And down the other side, where the ramp is the block being descended <em>into</em>.
     *
     * <p>A ramp always belongs to the lower of the two blocks - vanilla's rule for rails - so a
     * line going down puts it one step ahead of the belt that feeds it rather than under it. The
     * shape is {@link BeltShape#DOWN} and the two are the same ramp travelled opposite ways, which
     * is why there are two shapes and not eight.
     */
    public static class DescendsAStepTest extends GameTestInstance {

        public static final MapCodec<DescendsAStepTest> CODEC =
                RecordCodecBuilder.<DescendsAStepTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DescendsAStepTest::info))
                                .apply(i, DescendsAStepTest::new));

        public DescendsAStepTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos high = TAIL.above();
            BlockPos ramp = TAIL.east();

            place(helper, high, Direction.EAST);
            place(helper, ramp, Direction.EAST);
            place(helper, ramp.east(), Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertBlockProperty(ramp, BeltBlock.SHAPE, BeltShape.DOWN);
                helper.assertBlockProperty(high, BeltBlock.SHAPE, BeltShape.STRAIGHT);

                BeltRun run = runAt(helper, high);
                helper.assertValueEqual(run.blocks().size(), 3, "belts in the descending line");

                put(belt(helper, high, Direction.NORTH), Items.COPPER_INGOT, 1);
                helper.runAfterDelay(60, () -> {
                    helper.assertValueEqual(runAt(helper, high).itemCount(), 1,
                            "items still on the line after going down it");
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
            return Component.literal("a belt line goes down a step");
        }
    }

    /**
     * An item on a ramp is drawn at the height of the ramp.
     *
     * <p>The one thing about a slope that only arithmetic can check. {@code BeltRun.pointAt} lifts
     * an item between the seam it came in over and the seam it leaves by, and those come from the
     * two blocks sharing each seam rather than from either one's shape - so the claim worth pinning
     * is that the middle of a ramp is half a step up from the belt behind it, and that the seam
     * between the ramp and the belt at the top of the climb is the same height for both of them.
     */
    public static class SlopeCarriesAtHeightTest extends GameTestInstance {

        public static final MapCodec<SlopeCarriesAtHeightTest> CODEC =
                RecordCodecBuilder.<SlopeCarriesAtHeightTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SlopeCarriesAtHeightTest::info))
                                .apply(i, SlopeCarriesAtHeightTest::new));

        public SlopeCarriesAtHeightTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos ramp = TAIL.east();
            BlockPos top = TAIL.east(2).above();

            place(helper, TAIL, Direction.EAST);
            place(helper, ramp, Direction.EAST);
            place(helper, top, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                BeltRun run = runAt(helper, TAIL);
                double ground = helper.absolutePos(TAIL).getY() + Belts.HEIGHT;

                helper.assertValueEqual(middleHeight(helper, run, TAIL), ground,
                        "the height of an item over the flat belt");
                // Half a step up: in at the bottom of the ramp, out at the top of it.
                helper.assertValueEqual(middleHeight(helper, run, ramp), ground + 0.5,
                        "the height of an item over the middle of the ramp");
                helper.assertValueEqual(middleHeight(helper, run, top), ground + 1.0,
                        "the height of an item over the belt at the top of the climb");
                helper.succeed();
            });
        }

        /** How high an item is carried over the middle of one block of a run. */
        private static double middleHeight(GameTestHelper helper, BeltRun run, BlockPos block) {
            int index = run.indexOf(helper.absolutePos(block));
            helper.assertTrue(index >= 0, "expected " + block + " to be part of the run");
            return run.pointAt(run.frontEdge(index) + Belts.UNITS_PER_BLOCK / 2.0, Belts.LEFT).y;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a slope carries items at the height it is drawn");
        }
    }

    /**
     * Level wins over a step, and a slope wins over a bend.
     *
     * <p>Two orderings, and both are decisions rather than accidents. A belt that could hand to
     * something straight ahead or to something a step up hands to the one straight ahead, which is
     * vanilla's rail probe and what stops a line grabbing the floor above it. And a belt that both
     * climbs and has something side-loading into it is drawn climbing, because the drawing has to
     * agree with where its items actually go.
     */
    public static class SlopeBeatsABendTest extends GameTestInstance {

        public static final MapCodec<SlopeBeatsABendTest> CODEC =
                RecordCodecBuilder.<SlopeBeatsABendTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SlopeBeatsABendTest::info))
                                .apply(i, SlopeBeatsABendTest::new));

        public SlopeBeatsABendTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos ramp = TAIL.east();

            // A ramp with a belt joining it from the north: a side-load onto a climb.
            place(helper, ramp, Direction.EAST);
            place(helper, TAIL.east(2).above(), Direction.EAST);
            place(helper, ramp.north(), Direction.SOUTH);

            // And, well clear of it, a belt with a belt both straight ahead and a step up.
            BlockPos fork = TAIL.south(4);
            place(helper, fork, Direction.EAST);
            place(helper, fork.east(), Direction.EAST);
            place(helper, fork.east().above(), Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertBlockProperty(ramp, BeltBlock.SHAPE, BeltShape.UP);

                helper.assertBlockProperty(fork, BeltBlock.SHAPE, BeltShape.STRAIGHT);
                helper.assertTrue(runAt(helper, fork) == runAt(helper, fork.east()),
                        "a belt with somewhere level to go should go there rather than up a step");
                helper.assertTrue(runAt(helper, fork) != runAt(helper, fork.east().above()),
                        "the belt a step up should be a line of its own");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("level beats a step, and a slope beats a bend");
        }
    }

    /**
     * A slope is shallow enough at every point for a belt to lift something up it.
     *
     * <p><b>This is the arithmetic behind the one thing a slope gets wrong invisibly.</b> A player
     * or a mob walks up anything under {@code maxUpStep}, which is 0.6 for them - but on
     * {@code Entity} that method returns <em>zero</em>, and an item, a minecart or an experience orb
     * therefore climbs nothing at all on its own. Everything of that kind is lifted by
     * {@code BeltBlock.stepOn}, and a lift can only be as big as the belt's own speed without
     * shoving things along faster than the belt runs. So <b>every riser in the stair under a ramp
     * has to be smaller than one tick of the slowest belt</b>, or that belt's cargo stops dead
     * against it - which it did, with quarter-block steps.
     *
     * <p>Asserted against the shape rather than by watching an item climb, and that is deliberate.
     * A dropped item is carried at a rate that wanders and sometimes stalls outright - see
     * {@code docs/GAPS.md} - so a test that watched one failed about one run in six while the thing
     * it was meant to be testing worked perfectly. That is the same trap
     * {@code belt_carries_what_stands_on_it} fell into and was deleted for. The shape is the claim,
     * the shape is exact, and it is what a future edit would break.
     */
    public static class SlopeRisersTest extends GameTestInstance {

        public static final MapCodec<SlopeRisersTest> CODEC =
                RecordCodecBuilder.<SlopeRisersTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SlopeRisersTest::info))
                                .apply(i, SlopeRisersTest::new));

        public SlopeRisersTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos ramp = TAIL.east();
            place(helper, TAIL, Direction.EAST);
            place(helper, ramp, Direction.EAST);
            place(helper, TAIL.east(2).above(), Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertBlockProperty(ramp, BeltBlock.SHAPE, BeltShape.UP);

                BlockPos at = helper.absolutePos(ramp);
                BlockState state = helper.getLevel().getBlockState(at);
                VoxelShape shape = state.getCollisionShape(helper.getLevel(), at);

                // What one tick of the slowest belt can lift something by.
                double lift = TransportBeltBlock.SPEED / (double) Belts.UNITS_PER_BLOCK;
                double previous = -1.0;
                double highest = 0.0;
                for (int sample = 0; sample < 64; sample++) {
                    double along = (sample + 0.5) / 64.0;
                    // max(Y, b, c) takes the other two axes in cycle order, which for Y is z then x.
                    double top = shape.max(Direction.Axis.Y, 0.5, along);
                    helper.assertTrue(top > 0.0,
                            "a ramp should be solid the whole way along, and is not at " + along);
                    if (previous >= 0.0) {
                        helper.assertTrue(top - previous <= lift + 1.0E-9, String.format(
                                "a riser of %.3f at %.2f along the ramp, and a belt lifts %.3f in a "
                                + "tick - anything with no step height of its own stops there",
                                top - previous, along, lift));
                    }
                    previous = top;
                    highest = Math.max(highest, top);
                }

                // And because it does reach past its own block, the block has to say so, or the
                // game asks the air above the ramp to do the carrying over the top of every slope.
                helper.assertTrue(highest > 1.0, "a ramp's stair should reach above its own block");
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                helper.assertTrue(state.collisionExtendsVertically(helper.getLevel(), at, player),
                        "a ramp should declare that its collision reaches above its block");

                BlockPos flat = helper.absolutePos(TAIL);
                helper.assertTrue(!helper.getLevel().getBlockState(flat)
                                .collisionExtendsVertically(helper.getLevel(), flat, player),
                        "a flat belt should not, being half a block like any slab");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a slope's risers are small enough to be lifted over");
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

    /** Whether an inserter has a block tick coming - which is what "awake" means here. */
    private static boolean isScheduled(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(pos), ModBlocks.BURNER_INSERTER.get());
    }

    /**
     * A belt fed from one side only ever fills half: four items on the far lane, and then it
     * refuses, with the near lane empty and visible.
     *
     * <p>This is the rule {@code belt_holds_four_items_a_tile} does <em>not</em> catch, because
     * that one asks with no side at all and is entitled to both lanes. Falling back to the near
     * lane when the far one is full looks generous, and it is the difference between one belt
     * feeding two rows of machines and one inserter filling a whole belt on its own. A player
     * putting inserters down both sides of a bus is relying on this and on nothing else.
     */
    public static class OnlyTheFarLaneTest extends GameTestInstance {

        public static final MapCodec<OnlyTheFarLaneTest> CODEC =
                RecordCodecBuilder.<OnlyTheFarLaneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(OnlyTheFarLaneTest::info))
                                .apply(i, OnlyTheFarLaneTest::new));

        public OnlyTheFarLaneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            helper.runAfterDelay(SETTLED, () -> {
                // North of an eastbound belt is its left, so the far lane from there is the right.
                helper.assertValueEqual(put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 64),
                        4, "items one tile took from a single side");

                BeltRun run = runAt(helper, TAIL);
                helper.assertValueEqual(run.lane(Belts.RIGHT).size(), 4, "items on the far lane");
                helper.assertValueEqual(run.lane(Belts.LEFT).size(), 0,
                        "an inserter on one side filled the near lane too, so one belt can no "
                                + "longer feed two rows of machines");

                // And the near lane is not unreachable, only not this asker's to fill.
                helper.assertValueEqual(put(belt(helper, TAIL, Direction.SOUTH), Items.COPPER_INGOT, 64),
                        4, "items the other side took");
                helper.assertValueEqual(runAt(helper, TAIL).lane(Belts.LEFT).size(), 4,
                        "items the far lane from the south holds");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an inserter fills only the far lane");
        }
    }

    /**
     * Taking is the other way round: the <em>near</em> lane first.
     *
     * <p>Not a mirror of giving, and not an oversight. Factorio's inserter reaches across to give
     * because that is what makes a belt feed two rows, and grabs from the near side because the arm
     * has less distance to cover - "this favors inserters taking from the inner lane". The two
     * rules genuinely disagree, which is why {@code BeltAccess} has two methods rather than one
     * with a flag: a flag is an invitation to use the same rule for both jobs again.
     */
    public static class NearLaneFirstTest extends GameTestInstance {

        public static final MapCodec<NearLaneFirstTest> CODEC =
                RecordCodecBuilder.<NearLaneFirstTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(NearLaneFirstTest::info))
                                .apply(i, NearLaneFirstTest::new));

        public NearLaneFirstTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            line(helper, 1);
            helper.runAfterDelay(SETTLED, () -> {
                // One item a lane, each put on from the side it is far from.
                put(belt(helper, TAIL, Direction.NORTH), Items.IRON_INGOT, 1);
                put(belt(helper, TAIL, Direction.SOUTH), Items.COPPER_INGOT, 1);

                BeltRun run = runAt(helper, TAIL);
                helper.assertTrue(run.lane(Belts.RIGHT).item(0).getItem() == Items.IRON_INGOT,
                        "the iron did not land on the right lane");
                helper.assertTrue(run.lane(Belts.LEFT).item(0).getItem() == Items.COPPER_INGOT,
                        "the copper did not land on the left lane");

                // Both lanes are still offered - the far one is second, not refused.
                ResourceHandler<ItemResource> fromNorth = belt(helper, TAIL, Direction.NORTH);
                helper.assertValueEqual(fromNorth.size(), 2, "items an asker to the north can see");
                helper.assertTrue(fromNorth.getResource(0).getItem() == Items.COPPER_INGOT,
                        "an asker to the north should reach its near lane, the left one, first");

                ResourceHandler<ItemResource> fromSouth = belt(helper, TAIL, Direction.SOUTH);
                helper.assertTrue(fromSouth.getResource(0).getItem() == Items.IRON_INGOT,
                        "an asker to the south should reach its near lane, the right one, first");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an inserter takes the near lane first");
        }
    }

    /**
     * The whole point of the burner tier, in one arrangement: a dry inserter beside a coal belt.
     *
     * <p>Two claims, and the first is the one that would rot silently. <b>Coal reaching the belt
     * behind it wakes it in the same tick.</b> An inserter with nothing to burn schedules no ticks,
     * so nothing it does can ever restart it and the wake has to arrive from outside. It does:
     * the belt marks the block an item crossed onto, that is a {@code setChanged}, and NeoForge
     * routes it to all six neighbours, where {@code InserterBlock.onNeighborChange} turns it into
     * a scheduled tick. If that ever stops being true the inserter still passes every test that
     * hands it its work directly, and is dead on a real belt.
     *
     * <p>The second is that it then fuels itself and gets on with the job, with nobody having given
     * it anything.
     */
    public static class FuelsItselfFromABeltTest extends GameTestInstance {

        public static final MapCodec<FuelsItselfFromABeltTest> CODEC =
                RecordCodecBuilder.<FuelsItselfFromABeltTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FuelsItselfFromABeltTest::info))
                                .apply(i, FuelsItselfFromABeltTest::new));

        public FuelsItselfFromABeltTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos inserter = new BlockPos(0, 1, 1);
            BlockPos chest = new BlockPos(0, 1, 2);

            // One belt, so the coal jams at its own end and is still there for the swing.
            line(helper, 1);
            helper.setBlock(inserter, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                    .setValue(InserterBlock.FACING, Direction.SOUTH));
            helper.setBlock(chest, Blocks.CHEST);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper, inserter),
                        "an inserter with no fuel and an empty belt behind it is still ticking");

                put(belt(helper, TAIL, Direction.SOUTH), Items.COAL, 4);

                helper.assertTrue(isScheduled(helper, inserter),
                        "coal reaching the belt behind a dry inserter did not wake it in the same "
                                + "tick - the belt marks the block an item crossed onto, and "
                                + "InserterBlock.onNeighborChange is what turns that into a tick");

                helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 10, () -> {
                    BurnerInserterBlockEntity burner =
                            helper.getBlockEntity(inserter, BurnerInserterBlockEntity.class);
                    helper.assertTrue(burner.burnTime() > 0,
                            "the inserter never took a lump off the belt to burn");
                    helper.assertValueEqual(countIn(container(helper, chest), Items.COAL), 1,
                            "coal an unfuelled inserter moved off a coal belt");
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
            return Component.literal("a burner inserter fuels itself from a belt");
        }
    }

    /**
     * <b>The test the belt subsystem most needed and did not have.</b>
     *
     * <p>An item put onto a belt from outside announces itself - that is a transaction committing,
     * and {@code BeltRun.markChanged} tells the neighbours. An item that <em>travels</em> into a
     * tile announces nothing on its own: no block entity changed and no chunk was touched. So an
     * inserter that had gone to sleep beside an empty stretch of belt slept through the item
     * arriving, and the first thing a player builds - a burner inserter beside a coal belt - died
     * the moment there was a gap in the coal.
     *
     * <p>Everything else in the pack gets this for free, which is exactly why it was missed: a
     * chest, a furnace and an assembler all call {@code setChanged} when their contents change, and
     * every existing test hands the inserter its work directly, on its own tile. This one puts the
     * coal <em>four tiles upstream</em> and makes it walk.
     *
     * <p>See {@code BeltRun.announceArrivals}. If it is ever deleted as an optimisation, this test
     * is what says no.
     */
    public static class BeltWakesAnInserterTest extends GameTestInstance {

        public static final MapCodec<BeltWakesAnInserterTest> CODEC =
                RecordCodecBuilder.<BeltWakesAnInserterTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BeltWakesAnInserterTest::info))
                                .apply(i, BeltWakesAnInserterTest::new));

        public BeltWakesAnInserterTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos last = TAIL.east(4);
            BlockPos inserter = last.south();
            BlockPos chest = inserter.south();

            line(helper, 5);
            helper.setBlock(inserter, ModBlocks.BURNER_INSERTER.get().defaultBlockState()
                    .setValue(InserterBlock.FACING, Direction.SOUTH));
            helper.setBlock(chest, Blocks.CHEST);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper, inserter),
                        "an inserter with no fuel and an empty belt behind it is still ticking");

                // Onto the tail, four tiles upstream: nothing crosses the belt's boundary anywhere
                // near the inserter, so only the arrival itself can wake it.
                put(belt(helper, TAIL, Direction.SOUTH), Items.COAL, 4);
                helper.assertFalse(isScheduled(helper, inserter),
                        "coal four tiles upstream woke an inserter it cannot reach - the arrival "
                                + "signal is meant to be per block, not per run");

                // Four tiles at 6/64 of a block a tick is about 43 ticks, then a swing.
                helper.runAfterDelay(120, () -> {
                    BurnerInserterBlockEntity burner =
                            helper.getBlockEntity(inserter, BurnerInserterBlockEntity.class);
                    helper.assertTrue(burner.burnTime() > 0,
                            "coal reached the tile beside a sleeping inserter and it never woke - "
                                    + "an item travelling along a belt changes no block entity, so "
                                    + "BeltRun.announceArrivals is the only thing that can say so");
                    helper.assertTrue(countIn(container(helper, chest), Items.COAL) >= 1,
                            "the inserter woke and lit itself but delivered nothing");
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
            return Component.literal("a belt wakes an inserter when an item arrives");
        }
    }

    private static void placeSplitter(GameTestHelper helper, BlockPos pos, Direction facing) {
        placeSplitter(helper, pos, facing, ModBlocks.SPLITTER.get());
    }

    /** A splitter of a named tier, anchored at {@code pos}. */
    private static void placeSplitter(GameTestHelper helper, BlockPos pos, Direction facing,
            SplitterBlock tier) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = tier.defaultBlockState()
                .setValue(SplitterBlock.FACING, facing)
                .setValue(SplitterShape.SHAPE.part(), SplitterShape.SHAPE.anchor());
        helper.setBlock(pos, state);
        Multiblock.setPlacedBy(tier, helper.getLevel(), absolute, state);
    }

    /** The block entity of the splitter anchored at {@code pos}, in test coordinates. */
    private static SplitterBlockEntity splitter(GameTestHelper helper, BlockPos pos) {
        return (SplitterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    /**
     * Whether a splitter is being ticked.
     *
     * <p>Membership of {@link BeltLines}' active set, not the block tick queue: a splitter is part
     * of the belt simulation and is ticked with the runs either side of it, on both sides of the
     * wire. See {@code SplitterBlockEntity}.
     */
    private static boolean awake(GameTestHelper helper, BlockPos pos) {
        return BeltLines.of(helper.getLevel()).isActive(splitter(helper, pos));
    }

    /** One belt into a splitter divides items evenly across two output belts. */
    public static class SplitterSplitsSingleBeltEvenlyTest extends GameTestInstance {

        public static final MapCodec<SplitterSplitsSingleBeltEvenlyTest> CODEC =
                RecordCodecBuilder.<SplitterSplitsSingleBeltEvenlyTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterSplitsSingleBeltEvenlyTest::info))
                                .apply(i, SplitterSplitsSingleBeltEvenlyTest::new));

        public SplitterSplitsSingleBeltEvenlyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos input = new BlockPos(1, 1, 1);
            BlockPos splitter = new BlockPos(2, 1, 1);
            BlockPos outLeft = new BlockPos(3, 1, 1);
            BlockPos outRight = new BlockPos(3, 1, 2);

            place(helper, input, Direction.EAST);
            placeSplitter(helper, splitter, Direction.EAST);
            place(helper, outLeft, Direction.EAST);
            place(helper, outRight, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, input, null), Items.IRON_INGOT, 8);

                helper.runAfterDelay(80, () -> {
                    helper.assertValueEqual(countIn(belt(helper, outLeft, null), Items.IRON_INGOT), 4,
                            "items routed to the left output");
                    helper.assertValueEqual(countIn(belt(helper, outRight, null), Items.IRON_INGOT), 4,
                            "items routed to the right output");
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
            return Component.literal("a splitter divides a single belt evenly");
        }
    }

    /** Two input belts into a splitter merge into one output belt. */
    public static class SplitterMergesTwoBeltsTest extends GameTestInstance {

        public static final MapCodec<SplitterMergesTwoBeltsTest> CODEC =
                RecordCodecBuilder.<SplitterMergesTwoBeltsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterMergesTwoBeltsTest::info))
                                .apply(i, SplitterMergesTwoBeltsTest::new));

        public SplitterMergesTwoBeltsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos inLeft = new BlockPos(1, 1, 1);
            BlockPos inRight = new BlockPos(1, 1, 2);
            BlockPos splitter = new BlockPos(2, 1, 1);
            BlockPos outLeft = new BlockPos(3, 1, 1);

            place(helper, inLeft, Direction.EAST);
            place(helper, inRight, Direction.EAST);
            placeSplitter(helper, splitter, Direction.EAST);
            place(helper, outLeft, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, inLeft, null), Items.COPPER_INGOT, 2);
                put(belt(helper, inRight, null), Items.IRON_INGOT, 2);

                helper.runAfterDelay(80, () -> {
                    helper.assertValueEqual(countIn(belt(helper, outLeft, null), Items.COPPER_INGOT), 2,
                            "copper items merged to output");
                    helper.assertValueEqual(countIn(belt(helper, outLeft, null), Items.IRON_INGOT), 2,
                            "iron items merged to output");
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
            return Component.literal("a splitter merges two belts into one output");
        }
    }

    /** When one output is blocked or missing, all items overflow to the open output. */
    public static class SplitterOverflowsWhenBlockedTest extends GameTestInstance {

        public static final MapCodec<SplitterOverflowsWhenBlockedTest> CODEC =
                RecordCodecBuilder.<SplitterOverflowsWhenBlockedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterOverflowsWhenBlockedTest::info))
                                .apply(i, SplitterOverflowsWhenBlockedTest::new));

        public SplitterOverflowsWhenBlockedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos input = new BlockPos(1, 1, 1);
            BlockPos splitter = new BlockPos(2, 1, 1);
            BlockPos outRight = new BlockPos(3, 1, 2);

            place(helper, input, Direction.EAST);
            placeSplitter(helper, splitter, Direction.EAST);
            place(helper, outRight, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, input, null), Items.IRON_INGOT, 4);

                helper.runAfterDelay(80, () -> {
                    helper.assertValueEqual(countIn(belt(helper, outRight, null), Items.IRON_INGOT), 4,
                            "all items overflowed to the open right output");
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
            return Component.literal("a splitter overflows when one output is blocked");
        }
    }

    /** A splitter preserves the left and right lane identities of items. */
    public static class SplitterPreservesLanesTest extends GameTestInstance {

        public static final MapCodec<SplitterPreservesLanesTest> CODEC =
                RecordCodecBuilder.<SplitterPreservesLanesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterPreservesLanesTest::info))
                                .apply(i, SplitterPreservesLanesTest::new));

        public SplitterPreservesLanesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos input = new BlockPos(1, 1, 1);
            BlockPos splitter = new BlockPos(2, 1, 1);
            BlockPos outLeft = new BlockPos(3, 1, 1);
            BlockPos outRight = new BlockPos(3, 1, 2);

            place(helper, input, Direction.EAST);
            placeSplitter(helper, splitter, Direction.EAST);
            place(helper, outLeft, Direction.EAST);
            place(helper, outRight, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                put(belt(helper, input, Direction.SOUTH), Items.COPPER_INGOT, 1);
                put(belt(helper, input, Direction.NORTH), Items.IRON_INGOT, 1);

                helper.runAfterDelay(80, () -> {
                    BeltRun leftRun = runAt(helper, outLeft);
                    BeltRun rightRun = runAt(helper, outRight);

                    int copperLeft = leftRun.lane(Belts.LEFT).size() + rightRun.lane(Belts.LEFT).size();
                    int ironRight = leftRun.lane(Belts.RIGHT).size() + rightRun.lane(Belts.RIGHT).size();

                    helper.assertValueEqual(copperLeft, 1, "copper remained on the left lane");
                    helper.assertValueEqual(ironRight, 1, "iron remained on the right lane");
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
            return Component.literal("a splitter preserves lane sides");
        }
    }

    /** An empty splitter has no scheduled ticks; inserting an item wakes it. */
    public static class SplitterSleepsWhenEmptyTest extends GameTestInstance {

        public static final MapCodec<SplitterSleepsWhenEmptyTest> CODEC =
                RecordCodecBuilder.<SplitterSleepsWhenEmptyTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterSleepsWhenEmptyTest::info))
                                .apply(i, SplitterSleepsWhenEmptyTest::new));

        public SplitterSleepsWhenEmptyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos input = new BlockPos(1, 1, 1);
            BlockPos splitter = new BlockPos(2, 1, 1);
            BlockPos outLeft = new BlockPos(3, 1, 1);

            place(helper, input, Direction.EAST);
            placeSplitter(helper, splitter, Direction.EAST);
            place(helper, outLeft, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                helper.assertFalse(awake(helper, splitter),
                        "an empty splitter should sleep without being ticked");

                put(belt(helper, input, null), Items.IRON_INGOT, 1);

                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(awake(helper, splitter),
                            "a splitter should be awake while moving an item");

                    helper.runAfterDelay(70, () -> {
                        helper.assertFalse(awake(helper, splitter),
                                "a splitter should sleep again once the item has exited");
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
            return Component.literal("an empty splitter sleeps");
        }
    }

    /** Breaking either cell of a 2x1 splitter removes both cells and drops one item. */
    public static class SplitterBreaksTogetherTest extends GameTestInstance {

        public static final MapCodec<SplitterBreaksTogetherTest> CODEC =
                RecordCodecBuilder.<SplitterBreaksTogetherTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SplitterBreaksTogetherTest::info))
                                .apply(i, SplitterBreaksTogetherTest::new));

        public SplitterBreaksTogetherTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos splitterLeft = new BlockPos(2, 1, 1);
            BlockPos splitterRight = new BlockPos(2, 1, 2);

            placeSplitter(helper, splitterLeft, Direction.EAST);

            helper.runAfterDelay(SETTLED, () -> {
                helper.destroyBlock(splitterRight);

                helper.runAfterDelay(2, () -> {
                    helper.assertBlockNotPresent(ModBlocks.SPLITTER.get(), splitterLeft);
                    helper.assertBlockNotPresent(ModBlocks.SPLITTER.get(), splitterRight);
                    helper.assertItemEntityPresent(ModItems.SPLITTER.get(), splitterLeft, 3.0);
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
            return Component.literal("a 2x1 splitter breaks together");
        }
    }

    /**
     * The red splitter, at its own speed.
     *
     * <p><b>This is the test that would have caught the bug the tier was built out of.</b> Speed
     * used to be a constant on the one concrete splitter class, read through the class name by
     * {@code SplitterBlockEntity.tick} - so a second splitter that differed only in its constant
     * would have crossed its deck at the yellow one's speed and passed every other splitter test
     * here, all of which count items rather than time them.
     *
     * <p>Measured on the deck rather than through the belts either side of it, because the deck is
     * the only part a splitter moves items along itself: an item is put on at the far edge and its
     * position is read a fixed number of ticks later. Four ticks, so a fast splitter is still short
     * of the exit and nothing has been handed anywhere.
     */
    public static class FastSplitterSpeedTest extends GameTestInstance {

        public static final MapCodec<FastSplitterSpeedTest> CODEC =
                RecordCodecBuilder.<FastSplitterSpeedTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(FastSplitterSpeedTest::info))
                                .apply(i, FastSplitterSpeedTest::new));

        private static final int TICKS = 4;

        public FastSplitterSpeedTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos pos = new BlockPos(2, 1, 1);
            placeSplitter(helper, pos, Direction.EAST, ModBlocks.FAST_SPLITTER.get());

            helper.runAfterDelay(SETTLED, () -> {
                splitter(helper, pos).insertAt(SplitterShape.LEFT_TRACK, Belts.LEFT,
                        Belts.UNITS_PER_BLOCK, ItemResource.of(Items.IRON_INGOT));

                helper.runAfterDelay(1, () -> {
                    BeltLane lane = splitter(helper, pos).lane(SplitterShape.LEFT_TRACK, Belts.LEFT);
                    helper.assertValueEqual(lane.size(), 1, "items on the deck");
                    int start = lane.position(0);

                    helper.runAfterDelay(TICKS, () -> {
                        BeltLane now = splitter(helper, pos)
                                .lane(SplitterShape.LEFT_TRACK, Belts.LEFT);
                        helper.assertValueEqual(now.size(), 1, "items still on the deck");
                        helper.assertValueEqual(start - now.position(0),
                                FastSplitterBlock.SPEED * TICKS,
                                "sixty-fourths of a block crossed in " + TICKS + " ticks");
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
            return Component.literal("a fast splitter moves items at its own speed");
        }
    }
}
