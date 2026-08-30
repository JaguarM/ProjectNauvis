package com.jaguarm.nauvismining;

import java.util.List;

import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.multiblock.MachineShape;
import com.jaguarm.nauvismining.multiblock.Multiblock;
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
}
