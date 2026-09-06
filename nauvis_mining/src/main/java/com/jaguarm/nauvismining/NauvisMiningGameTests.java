package com.jaguarm.nauvismining;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.miner.DigArea;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismining.registry.ModBlocks;
import com.jaguarm.nauvismining.registry.ModItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>The first tests this mod has had, and they arrive with the change that needed them: as of
 * version 2.0 a drill is not one block but four or nine, and the ways that goes wrong are ways a
 * person notices only after building a mining field. Blocks left standing that nothing can break;
 * nine drills dropped where one was placed; a field you cannot walk across.
 *
 * <p>Run with {@code ./gradlew runGameTestServer}, which exits non-zero if any of them fail.
 *
 * <p>The 26.2 shape is registry-driven: a test type is a registry entry, and its codec has to
 * exist even though these are registered in code and never serialised.
 */
@EventBusSubscriber(modid = NauvisMining.MODID)
public final class NauvisMiningGameTests {

    private NauvisMiningGameTests() {}

    /** A test whose structure is missing silently does not run. Minecraft ships an empty one. */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** Where each test puts its drill: one block up, so it is not inside the floor. */
    private static final BlockPos DRILL = new BlockPos(0, 1, 0);

    /**
     * Room around each test.
     *
     * <p>A grid test builds outside the structure it is given, and these build things three blocks
     * across. Without padding the drills of one test land in the next test along and the failure
     * turns up in whichever ran second.
     */
    private static final int PADDING = 16;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisMining.MODID);

    static {
        TEST_TYPES.register("drills_are_factorio_sized", () -> DrillSizeTest.CODEC);
        TEST_TYPES.register("drill_breaks_as_one", () -> DrillBreaksAsOneTest.CODEC);
        TEST_TYPES.register("drill_field_is_walkable", () -> DrillFieldIsWalkableTest.CODEC);
        TEST_TYPES.register("drill_takes_no_shovel", () -> NoShovelSlotTest.CODEC);
        TEST_TYPES.register("drill_mines_its_own_footprint", () -> DigAreaIsTheFootprintTest.CODEC);
    }

    /** Called from the mod constructor so the test types register with everything else. */
    public static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisMining.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "drills_are_factorio_sized", DrillSizeTest::new);
        register(event, environment, "drill_breaks_as_one", DrillBreaksAsOneTest::new);
        register(event, environment, "drill_field_is_walkable", DrillFieldIsWalkableTest::new);
        register(event, environment, "drill_takes_no_shovel", NoShovelSlotTest::new);
        register(event, environment, "drill_mines_its_own_footprint", DigAreaIsTheFootprintTest::new);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisMining.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true,
                        Rotation.NONE, false, 1, 1, false, PADDING)));
    }

    /**
     * Puts a whole drill in, anchored here and facing north.
     *
     * <p>Not {@code helper.setBlock}, which would leave one block of a drill standing alone - and
     * a lone block of a machine is destroyed by its own teardown rule the moment anything beside
     * it changes, so a test that placed one would fail somewhere else entirely.
     */
    private static void place(GameTestHelper helper, BlockPos anchor, MachineTier tier) {
        MinerBlock block = ModBlocks.DRILLS.get(tier).get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(MinerBlock.FACING, Direction.NORTH));
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

    /**
     * Both drills are the size Factorio made them, and each has exactly one block entity.
     *
     * <p>A burner mining drill is two tiles by two and an electric one is three by three. That is
     * identity rather than decoration - it decides what a mining field looks like - so it is
     * asserted here against flat numbers, and Project Nauvis's {@code tools/check_models.py}
     * asserts the same shapes against Factorio's own figures a second time.
     */
    public static class DrillSizeTest extends GameTestInstance {

        public static final MapCodec<DrillSizeTest> CODEC =
                RecordCodecBuilder.<DrillSizeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillSizeTest::info))
                                .apply(i, DrillSizeTest::new));

        public DrillSizeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            check(helper, MachineTier.BURNER, 2, 2, 5);
            check(helper, MachineTier.ELECTRIC, 3, 3, 9);
            helper.succeed();
        }

        private void check(GameTestHelper helper, MachineTier tier, int width, int depth,
                int blocks) {
            MinerBlock block = ModBlocks.DRILLS.get(tier).get();
            MachineShape shape = block.shape();
            String what = tier.id();

            helper.assertValueEqual(shape.width(), width, what + ": tiles across");
            helper.assertValueEqual(shape.depth(), depth, what + ": tiles deep");
            helper.assertValueEqual(shape.cellCount(), blocks, what + ": blocks");

            place(helper, DRILL, tier);
            int entities = 0;
            for (int part = 0; part < shape.cellCount(); part++) {
                BlockPos cell = shape.cellPos(DRILL, part, Direction.NORTH);
                helper.assertBlockPresent(block, cell);
                helper.assertValueEqual(
                        Multiblock.anchorPos(block, helper.getBlockState(cell),
                                helper.absolutePos(cell)),
                        helper.absolutePos(DRILL), what + ": anchor as seen from " + cell);
                if (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null) {
                    entities++;
                }
            }
            helper.assertValueEqual(entities, 1, what + ": block entities in one drill");

            for (int part = 0; part < shape.cellCount(); part++) {
                helper.setBlock(shape.cellPos(DRILL, part, Direction.NORTH), Blocks.AIR);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("drills are Factorio sized");
        }
    }

    /**
     * Break one block of a drill and the rest go, giving back exactly one drill.
     *
     * <p>A corner is broken rather than the block that holds the machine: it has no block entity,
     * its own loot entry is conditioned away, and everything after it is the teardown cascading
     * across the footprint. Both ways that fails are quiet - blocks left standing that nothing
     * can break, or nine drills dropped where one was placed.
     */
    public static class DrillBreaksAsOneTest extends GameTestInstance {

        public static final MapCodec<DrillBreaksAsOneTest> CODEC =
                RecordCodecBuilder.<DrillBreaksAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillBreaksAsOneTest::info))
                                .apply(i, DrillBreaksAsOneTest::new));

        public DrillBreaksAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinerBlock block = ModBlocks.DRILLS.get(MachineTier.ELECTRIC).get();
            MachineShape shape = block.shape();
            place(helper, DRILL, MachineTier.ELECTRIC);

            // A back corner: two blocks from the block entity and about as far from it as an
            // electric drill goes.
            helper.getLevel().destroyBlock(helper.absolutePos(DRILL.offset(-1, 0, 1)), true);

            helper.runAfterDelay(2, () -> {
                for (int part = 0; part < shape.cellCount(); part++) {
                    helper.assertBlockPresent(Blocks.AIR,
                            shape.cellPos(DRILL, part, Direction.NORTH));
                }
                helper.assertItemEntityCountIs(
                        ModItems.DRILLS.get(MachineTier.ELECTRIC).get(), DRILL, 4.0, 1);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill breaks as one");
        }
    }

    /**
     * Two electric drills side by side, and you walk straight over both.
     *
     * <p>The reason the deck is half a block. A mining field is dozens of drills covering the
     * ground the player wants to be on, and there is nowhere else to put them - so at anything
     * taller than the player's own step, a patch of ore becomes a patch you cannot cross. Half a
     * block is under the 0.6 a player climbs for free, so the whole field is one floor.
     *
     * <p>The output heads are asserted to stand a full block, so this cannot come out green by the
     * drill quietly flattening to nothing.
     */
    public static class DrillFieldIsWalkableTest extends GameTestInstance {

        public static final MapCodec<DrillFieldIsWalkableTest> CODEC =
                RecordCodecBuilder.<DrillFieldIsWalkableTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillFieldIsWalkableTest::info))
                                .apply(i, DrillFieldIsWalkableTest::new));

        public DrillFieldIsWalkableTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        /** What a player climbs without jumping. */
        private static final double STEP = 0.6;

        @Override
        public void run(GameTestHelper helper) {
            place(helper, DRILL, MachineTier.ELECTRIC);
            place(helper, DRILL.offset(3, 0, 0), MachineTier.ELECTRIC);

            // Both are still standing: neither took the other for part of itself.
            helper.assertBlockPresent(ModBlocks.DRILLS.get(MachineTier.ELECTRIC).get(),
                    DRILL.offset(1, 0, 0));
            helper.assertBlockPresent(ModBlocks.DRILLS.get(MachineTier.ELECTRIC).get(),
                    DRILL.offset(2, 0, 0));

            // The back row of both drills, which is deck all the way across.
            double previous = 0;
            for (int x = -1; x <= 4; x++) {
                BlockPos column = DRILL.offset(x, 0, 1);
                double top = surface(helper, column, 2);

                helper.assertTrue(top <= STEP, "the drill deck at x=" + x + " stands " + top
                        + " blocks high, which is more than the " + STEP + " a player steps over "
                        + "- a mining field has to be walkable");
                helper.assertTrue(x == -1 || top - previous <= STEP,
                        "the step up onto x=" + x + " is " + (top - previous) + " blocks");
                previous = top;
            }

            // And the heads stand a full block, so the drill has not quietly gone flat. The head
            // is a block north of the anchor, not on it: an electric drill keeps its block entity
            // in the middle of its nine so that its dig area stays centred on the machine.
            helper.assertValueEqual(surface(helper, DRILL.north(1), 2), 1.0,
                    "height of an output head");
            helper.assertValueEqual(surface(helper, DRILL, 2), 0.5,
                    "height of the deck under the middle of a drill");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill field is walkable");
        }
    }

    /**
     * There is no shovel slot, and the slots after it did not stay where they were.
     *
     * <p>Two claims, and the second is the one that would go wrong quietly. Removing a slot from
     * the middle of a container renumbers everything after it - the modules moved from 4..7 to
     * 3..6 and the output grid with them - and every one of those indices is written into a saved
     * drill and read back out. A drill that loaded a module into an output slot would look like a
     * machine that had eaten it, and nothing else here would notice.
     *
     * <p>Asked of {@code acceptsInSlot}, which is the rule both the menu and the block entity go
     * through, rather than of a placed machine: it is a pure function of the slot number, so this
     * pins the numbering itself rather than one drill's behaviour on one tick.
     */
    public static class NoShovelSlotTest extends GameTestInstance {

        public static final MapCodec<NoShovelSlotTest> CODEC =
                RecordCodecBuilder.<NoShovelSlotTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(NoShovelSlotTest::info))
                                .apply(i, NoShovelSlotTest::new));

        public NoShovelSlotTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // An electric drill's rules: every module slot unlocked, no fuel slot, and fill
            // wanted - the most permissive set there is, so a shovel getting in anywhere would
            // get in here.
            MinerBlockEntity.SlotRules rules = new MinerBlockEntity.SlotRules(
                    MinerBlockEntity.MODULE_SLOTS, true, true);
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

            for (int slot = 0; slot < MinerBlockEntity.SLOT_COUNT; slot++) {
                helper.assertFalse(
                        MinerBlockEntity.acceptsInSlot(slot, shovel, helper.getLevel(), rules),
                        "slot " + slot + " still takes a shovel");
            }

            // And the numbering the removal shifted. Written out rather than derived, because
            // deriving them from the same constants the code uses would assert nothing.
            helper.assertValueEqual(MinerBlockEntity.SLOT_PICKAXE, 2, "the pickaxe slot");
            helper.assertValueEqual(MinerBlockEntity.SLOT_MODULE_START, 3, "the first module slot");
            helper.assertValueEqual(MinerBlockEntity.SLOT_OUTPUT_START, 7, "the first output slot");
            helper.assertValueEqual(MinerBlockEntity.SLOT_COUNT, 16, "slots in a drill");

            helper.assertTrue(
                    MinerBlockEntity.acceptsInSlot(MinerBlockEntity.SLOT_PICKAXE,
                            new ItemStack(Items.IRON_PICKAXE), helper.getLevel(), rules),
                    "the pickaxe slot stopped taking a pickaxe");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill has no shovel slot");
        }
    }

    /**
     * A drill mines the ground it stands on: two by two under a burner, three by three under an
     * electric, and not one column more.
     *
     * <p>The first half is the claim a player can see. The second half is the one they cannot: the
     * area hands out columns by index, ring by ring, and <b>an off-by-one in that arithmetic loses
     * or repeats a column silently</b> - a drill would leave a strip of ore standing, or walk the
     * same ground twice, and both look like a drill that is simply working. So every index of an
     * area is walked and the set of columns it produces is compared with the rectangle it claims
     * to be, at zero rings and again at two, where all four sides and the corners are in play.
     */
    public static class DigAreaIsTheFootprintTest extends GameTestInstance {

        public static final MapCodec<DigAreaIsTheFootprintTest> CODEC =
                RecordCodecBuilder.<DigAreaIsTheFootprintTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DigAreaIsTheFootprintTest::info))
                                .apply(i, DigAreaIsTheFootprintTest::new));

        public DigAreaIsTheFootprintTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            checkFootprint(helper, MachineTier.BURNER, 2);
            checkFootprint(helper, MachineTier.ELECTRIC, 3);
            helper.succeed();
        }

        /** Places a drill and holds its area to the square the machine occupies. */
        private static void checkFootprint(GameTestHelper helper, MachineTier tier, int side) {
            // Two apart, so the burner's area cannot reach the electric one's blocks.
            BlockPos at = tier == MachineTier.BURNER ? DRILL : DRILL.offset(6, 0, 0);
            place(helper, at, tier);

            MinerBlock block = ModBlocks.DRILLS.get(tier).get();
            DigArea area = DigArea.of(block.shape(), helper.absolutePos(at), Direction.NORTH, 0);

            helper.assertValueEqual(area.footprintWidth(), side, tier + " drill area width");
            helper.assertValueEqual(area.footprintDepth(), side, tier + " drill area depth");
            helper.assertValueEqual(area.columns(), side * side, tier + " drill area columns");

            // The columns are the machine's own, block for block.
            Set<BlockPos> machine = new HashSet<>();
            for (int part = 0; part < block.shape().cellCount(); part++) {
                BlockPos cell = block.shape().cellPos(helper.absolutePos(at), part, Direction.NORTH);
                machine.add(new BlockPos(cell.getX(), 0, cell.getZ()));
            }
            for (DigArea.Column column : walk(helper, area)) {
                helper.assertTrue(machine.contains(new BlockPos(column.x(), 0, column.z())),
                        tier + " drill digs " + column + ", which is not a block of the machine");
            }

            // And the arithmetic, out where the rings are.
            DigArea wide = DigArea.of(block.shape(), helper.absolutePos(at), Direction.NORTH, 2);
            helper.assertValueEqual(wide.columns(), (side + 4) * (side + 4),
                    tier + " drill columns at two rings");
            Set<BlockPos> seen = new HashSet<>();
            for (DigArea.Column column : walk(helper, wide)) {
                helper.assertTrue(seen.add(new BlockPos(column.x(), 0, column.z())),
                        "column " + column + " is handed out twice");
                helper.assertTrue(
                        column.x() >= wide.outerMinX() && column.x() <= wide.outerMaxX()
                                && column.z() >= wide.outerMinZ() && column.z() <= wide.outerMaxZ(),
                        "column " + column + " is outside the area it came from");
            }
            helper.assertValueEqual(seen.size(), wide.columns(),
                    tier + " drill columns actually handed out");
        }

        /** Every column of an area, by the same 1-based index the machine walks. */
        private static List<DigArea.Column> walk(GameTestHelper helper, DigArea area) {
            List<DigArea.Column> columns = new java.util.ArrayList<>();
            for (int index = 1; index <= area.columns(); index++) {
                DigArea.Column column = area.column(index);
                helper.assertTrue(column != null, "no column at index " + index);
                columns.add(column);
            }
            helper.assertTrue(area.column(area.columns() + 1) == null,
                    "the area handed out a column past its own end");
            return columns;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill mines the ground it stands on");
        }
    }
}
