package com.jaguarm.nauvismining;

import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.miner.DigArea;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;
import com.jaguarm.nauvismining.machine.miner.MinerStatus;
import com.jaguarm.nauvismining.registry.ModBlocks;
import com.jaguarm.nauvismining.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import net.minecraft.world.item.Item;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>Half of them are about a drill being four or nine blocks - blocks left standing that nothing
 * can break, nine drills dropped where one was placed, a field you cannot walk across - and the
 * other half are about it being Factorio's drill: it takes ore from the ground under it and
 * leaves the ground, it puts the ore down in front of itself and nowhere else, it covers the
 * area Factorio gives it, and it sleeps when there is nothing to take.
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

    /** Where each test puts its drill: one block up, so the ground it mines is under it. */
    private static final BlockPos DRILL = new BlockPos(0, 1, 0);

    /**
     * Room around each test.
     *
     * <p>A grid test builds outside the structure it is given, and these build things five blocks
     * across and reach two more on every side. Without padding the drills of one test land in the
     * next test along and the failure turns up in whichever ran second.
     */
    private static final int PADDING = 16;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisMining.MODID);

    static {
        TEST_TYPES.register("drills_are_factorio_sized", () -> DrillSizeTest.CODEC);
        TEST_TYPES.register("drill_breaks_as_one", () -> DrillBreaksAsOneTest.CODEC);
        TEST_TYPES.register("drill_field_is_walkable", () -> DrillFieldIsWalkableTest.CODEC);
        TEST_TYPES.register("drill_slots_are_a_pickaxe_and_fuel", () -> DrillSlotsTest.CODEC);
        TEST_TYPES.register("drill_covers_factorios_area", () -> DigAreaIsFactoriosTest.CODEC);
        TEST_TYPES.register("drill_takes_ore_from_under_it", () -> DrillTakesOreTest.CODEC);
        TEST_TYPES.register("drill_outputs_to_the_front", () -> DrillOutputsToTheFrontTest.CODEC);
        TEST_TYPES.register("drill_sleeps_with_nothing_to_mine", () -> DrillSleepsTest.CODEC);
        TEST_TYPES.register("drill_takes_modules", () -> DrillTakesModulesTest.CODEC);
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

        register(event, environment, "drills_are_factorio_sized", DrillSizeTest::new, 40);
        register(event, environment, "drill_breaks_as_one", DrillBreaksAsOneTest::new, 40);
        register(event, environment, "drill_field_is_walkable", DrillFieldIsWalkableTest::new, 40);
        register(event, environment, "drill_slots_are_a_pickaxe_and_fuel", DrillSlotsTest::new, 40);
        register(event, environment, "drill_covers_factorios_area", DigAreaIsFactoriosTest::new, 40);
        register(event, environment, "drill_takes_ore_from_under_it", DrillTakesOreTest::new, 160);
        register(event, environment, "drill_outputs_to_the_front", DrillOutputsToTheFrontTest::new, 120);
        register(event, environment, "drill_sleeps_with_nothing_to_mine", DrillSleepsTest::new, 60);
        register(event, environment, "drill_takes_modules", DrillTakesModulesTest::new, 60);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisMining.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true,
                        Rotation.NONE, false, 1, 1, false, PADDING)));
    }

    // --- helpers ------------------------------------------------------------------------------

    /**
     * Puts a whole drill in, anchored here and facing north.
     *
     * <p>Not {@code helper.setBlock}, which would leave one block of a drill standing alone - and
     * a lone block of a machine is destroyed by its own teardown rule the moment anything beside
     * it changes, so a test that placed one would fail somewhere else entirely.
     */
    private static MinerBlockEntity place(GameTestHelper helper, BlockPos anchor, MachineTier tier) {
        MinerBlock block = ModBlocks.DRILLS.get(tier).get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(MinerBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, MinerBlockEntity.class);
    }

    /** A pickaxe in the slot: the one thing every drill here needs before it does anything. */
    private static void handPickaxe(MinerBlockEntity drill, Item pickaxe) {
        drill.inventory().set(MinerBlockEntity.PICKAXE_SLOT, ItemResource.of(new ItemStack(pickaxe)), 1);
    }

    /** Fills an electric drill's buffer through the view a pole would fill it through. */
    private static void charge(MinerBlockEntity drill) {
        try (Transaction transaction = Transaction.openRoot()) {
            drill.gridView().insert(drill.energyCapacity(), transaction);
            transaction.commit();
        }
    }

    /**
     * Lays the ground under a drill: stone in every column of its area from just under the
     * machine to {@code depth} below, so what is found there is what the test put there.
     */
    private static void bedrock(GameTestHelper helper, DigArea area, int depth) {
        for (int x = area.outerMinX(); x <= area.outerMaxX(); x++) {
            for (int z = area.outerMinZ(); z <= area.outerMaxZ(); z++) {
                for (int y = area.y() - 1; y >= area.y() - depth; y--) {
                    helper.setBlock(helper.relativePos(new BlockPos(x, y, z)), Blocks.STONE);
                }
            }
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

    // --- the drill is several blocks ----------------------------------------------------------

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
            // in the middle of its nine so that its area stays centred on the machine.
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

    // --- the drill is Factorio's --------------------------------------------------------------

    /**
     * A drill's slots are a pickaxe and, on a burner, fuel: nothing else goes in, and an electric
     * drill's fuel slot takes nothing at all. The module slots are Factorio's count - none on a
     * burner, three on an electric.
     *
     * <p>Asked of the inventory's own rule, which is what the screen, the hopper and the inserter
     * all go through, so one answer covers the three.
     */
    public static class DrillSlotsTest extends GameTestInstance {

        public static final MapCodec<DrillSlotsTest> CODEC =
                RecordCodecBuilder.<DrillSlotsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillSlotsTest::info))
                                .apply(i, DrillSlotsTest::new));

        public DrillSlotsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinerBlockEntity burner = place(helper, DRILL, MachineTier.BURNER);
            MinerBlockEntity electric = place(helper, DRILL.offset(6, 0, 0), MachineTier.ELECTRIC);

            ItemResource pickaxe = ItemResource.of(Items.IRON_PICKAXE);
            ItemResource shovel = ItemResource.of(Items.IRON_SHOVEL);
            ItemResource coal = ItemResource.of(Items.COAL);

            helper.assertTrue(burner.inventory().isValid(MinerBlockEntity.PICKAXE_SLOT, pickaxe),
                    "the pickaxe slot refused a pickaxe");
            helper.assertFalse(burner.inventory().isValid(MinerBlockEntity.PICKAXE_SLOT, shovel),
                    "the pickaxe slot took a shovel");
            helper.assertFalse(burner.inventory().isValid(MinerBlockEntity.PICKAXE_SLOT, coal),
                    "the pickaxe slot took coal");
            helper.assertTrue(burner.inventory().isValid(MinerBlockEntity.FUEL_SLOT, coal),
                    "a burner's fuel slot refused coal");
            helper.assertFalse(burner.inventory().isValid(MinerBlockEntity.FUEL_SLOT, pickaxe),
                    "a burner's fuel slot took a pickaxe");
            helper.assertFalse(electric.inventory().isValid(MinerBlockEntity.FUEL_SLOT, coal),
                    "an electric drill's fuel slot took coal");

            helper.assertValueEqual(burner.modules().size(), 0, "module slots on a burner drill");
            helper.assertValueEqual(electric.modules().size(), 3, "module slots on an electric drill");
            helper.assertTrue(burner.gridView() == null, "a burner drill offered the grid a buffer");
            helper.assertTrue(electric.gridView() != null, "an electric drill offered the grid nothing");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill's slots are a pickaxe and fuel");
        }
    }

    /**
     * A drill covers Factorio's area: the two by two a burner stands on, and the five by five
     * around an electric drill's three by three, and not one column more or less.
     *
     * <p>The first half is the claim a player can see. The second half is the one they cannot: the
     * area hands out columns by index, ring by ring, and <b>an off-by-one in that arithmetic loses
     * or repeats a column silently</b> - a drill would leave a strip of ore standing, or walk the
     * same ground twice, and both look like a drill that is simply working. So every index of an
     * area is walked and the set of columns it produces is compared with the rectangle it claims
     * to be.
     */
    public static class DigAreaIsFactoriosTest extends GameTestInstance {

        public static final MapCodec<DigAreaIsFactoriosTest> CODEC =
                RecordCodecBuilder.<DigAreaIsFactoriosTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DigAreaIsFactoriosTest::info))
                                .apply(i, DigAreaIsFactoriosTest::new));

        public DigAreaIsFactoriosTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            // Six apart, so the electric one's ring cannot reach the burner's blocks.
            checkArea(helper, MachineTier.BURNER, DRILL, 2);
            checkArea(helper, MachineTier.ELECTRIC, DRILL.offset(6, 0, 0), 5);
            helper.succeed();
        }

        /** Places a drill and holds its area to a square of the given side, centred on the machine. */
        private static void checkArea(GameTestHelper helper, MachineTier tier, BlockPos at, int side) {
            place(helper, at, tier);
            MinerBlock block = ModBlocks.DRILLS.get(tier).get();
            DigArea area = MinerBlock.digArea(helper.getBlockState(at), helper.absolutePos(at));

            helper.assertValueEqual(area.outerMaxX() - area.outerMinX() + 1, side, tier + " drill area width");
            helper.assertValueEqual(area.outerMaxZ() - area.outerMinZ() + 1, side, tier + " drill area depth");
            helper.assertValueEqual(area.columns(), side * side, tier + " drill area columns");

            // The machine's own blocks are all inside it.
            for (int part = 0; part < block.shape().cellCount(); part++) {
                BlockPos cell = block.shape().cellPos(helper.absolutePos(at), part, Direction.NORTH);
                helper.assertTrue(cell.getX() >= area.outerMinX() && cell.getX() <= area.outerMaxX()
                                && cell.getZ() >= area.outerMinZ() && cell.getZ() <= area.outerMaxZ(),
                        tier + " drill's block at " + cell + " is outside its own area");
            }

            // And the arithmetic: every column once, and none outside.
            Set<BlockPos> seen = new HashSet<>();
            for (DigArea.Column column : walk(helper, area)) {
                helper.assertTrue(seen.add(new BlockPos(column.x(), 0, column.z())),
                        "column " + column + " is handed out twice");
                helper.assertTrue(
                        column.x() >= area.outerMinX() && column.x() <= area.outerMaxX()
                                && column.z() >= area.outerMinZ() && column.z() <= area.outerMaxZ(),
                        "column " + column + " is outside the area it came from");
            }
            helper.assertValueEqual(seen.size(), area.columns(), tier + " drill columns actually handed out");
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill covers Factorio's area");
        }
    }

    /**
     * A burner drill with coal and a pickaxe takes iron ore out of the ground under it, puts raw
     * iron in the chest in front of it, and leaves the ground standing.
     *
     * <p>The ore is under the back corner, two blocks from the output, so this is the drill
     * reaching through the ground and not a block breaking in front of it. Afterwards the ore is
     * either still ore - Crumbling Ore is in the pack and takes eight harvests to a block - or the
     * stone it sat in when this mod runs alone; what it never is is air, and the stone beside it
     * is untouched either way. Four seconds an ore at Factorio's quarter speed; the window allows
     * the search and the delivery on top.
     */
    public static class DrillTakesOreTest extends GameTestInstance {

        public static final MapCodec<DrillTakesOreTest> CODEC =
                RecordCodecBuilder.<DrillTakesOreTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillTakesOreTest::info))
                                .apply(i, DrillTakesOreTest::new));

        public DrillTakesOreTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        private static final BlockPos ORE = new BlockPos(1, 0, 1);
        private static final BlockPos STONE = new BlockPos(0, 0, 0);
        /** In front of the firebox, which is the burner's output head. */
        private static final BlockPos CHEST = new BlockPos(0, 1, -1);

        @Override
        public void run(GameTestHelper helper) {
            MinerBlockEntity drill = place(helper, DRILL, MachineTier.BURNER);
            bedrock(helper, drill.digArea(), 3);
            helper.setBlock(ORE, Blocks.IRON_ORE);
            helper.setBlock(CHEST, Blocks.CHEST);

            handPickaxe(drill, Items.IRON_PICKAXE);
            drill.inventory().set(MinerBlockEntity.FUEL_SLOT, ItemResource.of(Items.COAL), 4);

            int window = MinerBlockEntity.cycleTicksFor(MachineTier.BURNER, ModuleEffect.NONE, 0) + 30;
            helper.runAfterDelay(window, () -> {
                helper.assertContainerContains(CHEST, Items.RAW_IRON);
                helper.assertBlockPresent(Blocks.STONE, STONE);
                helper.assertBlock(ORE, block -> block == Blocks.IRON_ORE || block == Blocks.STONE,
                        block -> Component.literal("the mined ore is " + block.getName().getString()
                                + " - a drill leaves the ground standing"));
                helper.assertValueEqual(drill.inventory().getAmountAsInt(MinerBlockEntity.PICKAXE_SLOT), 1,
                        "the pickaxe is still in the drill");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill takes ore from under it and leaves the ground");
        }
    }

    /**
     * An electric drill puts its ore in the chest in front of its head and not in the chest beside
     * it, and reaches the corner of its five by five to find it.
     *
     * <p>The one ore is under the far corner of the ring, two columns outside the machine's own
     * blocks: a drill that only covered its footprint would find nothing and this would time out.
     * The side chest is where the old drill would have pushed to, since it pushed anywhere.
     */
    public static class DrillOutputsToTheFrontTest extends GameTestInstance {

        public static final MapCodec<DrillOutputsToTheFrontTest> CODEC =
                RecordCodecBuilder.<DrillOutputsToTheFrontTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillOutputsToTheFrontTest::info))
                                .apply(i, DrillOutputsToTheFrontTest::new));

        public DrillOutputsToTheFrontTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        private static final BlockPos ORE = new BlockPos(-2, 0, -2);
        /** In front of the head, which is a block north of the middle. */
        private static final BlockPos FRONT = new BlockPos(0, 1, -2);
        private static final BlockPos SIDE = new BlockPos(2, 1, 0);

        @Override
        public void run(GameTestHelper helper) {
            MinerBlockEntity drill = place(helper, DRILL, MachineTier.ELECTRIC);
            bedrock(helper, drill.digArea(), 2);
            helper.setBlock(ORE, Blocks.COPPER_ORE);
            helper.setBlock(FRONT, Blocks.CHEST);
            helper.setBlock(SIDE, Blocks.CHEST);

            handPickaxe(drill, Items.IRON_PICKAXE);
            charge(drill);

            int window = MinerBlockEntity.cycleTicksFor(MachineTier.ELECTRIC, ModuleEffect.NONE, 0) + 30;
            helper.runAfterDelay(window, () -> {
                helper.assertContainerContains(FRONT, Items.RAW_COPPER);
                helper.assertContainerEmpty(SIDE);
                helper.assertTrue(drill.energyStored() < drill.energyCapacity(),
                        "the drill mined without spending anything");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill outputs to the front, and reaches its ring");
        }
    }

    /**
     * A drill over plain rock walks its columns once, reports that there is nothing to mine, and
     * stops ticking - non-negotiable #5, for the machine a base has most of.
     */
    public static class DrillSleepsTest extends GameTestInstance {

        public static final MapCodec<DrillSleepsTest> CODEC =
                RecordCodecBuilder.<DrillSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillSleepsTest::info))
                                .apply(i, DrillSleepsTest::new));

        public DrillSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MinerBlockEntity drill = place(helper, DRILL, MachineTier.BURNER);
            bedrock(helper, drill.digArea(), 3);
            handPickaxe(drill, Items.IRON_PICKAXE);
            drill.inventory().set(MinerBlockEntity.FUEL_SLOT, ItemResource.of(Items.COAL), 4);

            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(drill.status(), MinerStatus.NO_ORE, "status over bare rock");
                helper.assertFalse(helper.getBlockState(DRILL).getValue(MinerBlock.LIT),
                        "a drill with nothing to mine is lit");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                                helper.absolutePos(DRILL), ModBlocks.DRILLS.get(MachineTier.BURNER).get()),
                        "a drill with nothing to mine is still ticking");
                helper.assertValueEqual(drill.inventory().getAmountAsInt(MinerBlockEntity.FUEL_SLOT), 4,
                        "coal in a drill that never mined - fuel is spent only while working");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a drill with nothing to mine sleeps");
        }
    }

    /**
     * An electric drill takes Factorio's modules and reads them the way every other machine does:
     * a speed module shortens the cycle by its speed and raises the draw by its cost.
     *
     * <p>The module is {@code nauvis_machines}' and this mod does not name it, so the item is
     * looked up by id and the test passes trivially when it is not there - which it is not in a
     * standalone run of this mod. The slot accepting it at all is the library's rule.
     */
    public static class DrillTakesModulesTest extends GameTestInstance {

        public static final MapCodec<DrillTakesModulesTest> CODEC =
                RecordCodecBuilder.<DrillTakesModulesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(DrillTakesModulesTest::info))
                                .apply(i, DrillTakesModulesTest::new));

        public DrillTakesModulesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Item speed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_machines", "speed_module"));
            if (ModuleSlots.moduleOf(new ItemStack(speed)) == null) {
                helper.succeed();
                return;
            }

            MinerBlockEntity drill = place(helper, DRILL, MachineTier.ELECTRIC);
            bedrock(helper, drill.digArea(), 2);
            helper.setBlock(new BlockPos(0, 0, 0), Blocks.IRON_ORE);
            handPickaxe(drill, Items.IRON_PICKAXE);
            drill.modules().set(0, ItemResource.of(speed), 1);
            charge(drill);

            ModuleEffect effect = ModuleSlots.moduleOf(new ItemStack(speed)).effect();
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(drill.status(), MinerStatus.MINING, "status with ore, power and a module");
                helper.assertValueEqual(drill.cycleTicks(),
                        MinerBlockEntity.cycleTicksFor(MachineTier.ELECTRIC, effect, 0),
                        "ticks an ore takes with a speed module");
                helper.assertTrue(drill.cycleTicks()
                        < MinerBlockEntity.cycleTicksFor(MachineTier.ELECTRIC, ModuleEffect.NONE, 0),
                        "a speed module did not shorten the cycle");
                helper.assertValueEqual(drill.currentEnergyPerTick(),
                        effect.scaleEnergy(MachineTier.ELECTRIC.energyPerTick()),
                        "draw with a speed module");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an electric drill takes modules");
        }
    }
}
