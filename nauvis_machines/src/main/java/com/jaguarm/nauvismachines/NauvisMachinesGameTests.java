package com.jaguarm.nauvismachines;

import java.util.List;

import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerMenu;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerShape;
import com.jaguarm.nauvismachines.multiblock.MachineShape;
import com.jaguarm.nauvismachines.multiblock.Multiblock;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
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
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>This is the half of testing that needs nobody watching. Behaviour - a machine consuming its
 * ingredients, a hopper being refused the ingredients it should not have, a machine going back
 * to sleep - belongs here. How any of it looks does not, and never will.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}, which puts every mod in the pack on one
 * classpath, or {@code :nauvis_machines:runGameTestServer} for this mod alone.
 *
 * <p>The 26.2 shape is registry-driven and unlike every tutorial. See {@code docs/API-26.2.md} -
 * in particular, {@code FunctionGameTestInstance} is unavailable to mods, because the registry
 * its bodies live in is bootstrapped during {@code BuiltInRegistries} static initialisation,
 * before any mod exists. Subclassing {@link GameTestInstance} is the way in.
 */
@EventBusSubscriber(modid = NauvisMachines.MODID)
public final class NauvisMachinesGameTests {

    private NauvisMachinesGameTests() {}

    /**
     * A test whose structure is missing silently does not run - {@code placeStructure} returns
     * false and reports nothing. Minecraft ships {@code minecraft:empty}, which is all a test
     * needing no terrain requires.
     */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** Where every test puts its machine: one block up, so it is not inside the floor. */
    private static final BlockPos MACHINE = new BlockPos(0, 1, 0);

    /**
     * Factorio's craft time for an assembling machine 1: 0.5s, which is ten ticks. Written out
     * here so the test asserts the number rather than waiting long enough not to care.
     */
    private static final int CRAFT_TICKS = 10;

    /**
     * Test types are a registry like any other, and the codec is what a datapack would use to
     * deserialise one. Ours are registered in code and never serialised, but the registry entry
     * still has to exist for the type to be legal.
     */
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisMachines.MODID);

    static {
        TEST_TYPES.register("assembler_places", () -> AssemblerPlacesTest.CODEC);
        TEST_TYPES.register("assembler_holds_items", () -> AssemblerHoldsItemsTest.CODEC);
        TEST_TYPES.register("assembler_crafts", () -> AssemblerCraftsTest.CODEC);
        TEST_TYPES.register("assembler_sleeps", () -> AssemblerSleepsTest.CODEC);
        TEST_TYPES.register("assembler_stalls_when_full", () -> AssemblerStallsWhenFullTest.CODEC);
        TEST_TYPES.register("assembler_spills_when_broken", () -> AssemblerSpillsWhenBrokenTest.CODEC);
        TEST_TYPES.register("assembler_menu_selects_recipe", () -> AssemblerMenuSelectsRecipeTest.CODEC);
        TEST_TYPES.register("assembler_needs_power", () -> AssemblerNeedsPowerTest.CODEC);
        TEST_TYPES.register("assembler_wakes_when_power_arrives",
                () -> AssemblerWakesWhenPowerArrivesTest.CODEC);
        TEST_TYPES.register("assembler_gives_no_power_back", () -> AssemblerGivesNoPowerBackTest.CODEC);
        TEST_TYPES.register("assembler_is_ten_blocks", () -> AssemblerIsTenBlocksTest.CODEC);
        TEST_TYPES.register("assembler_breaks_as_one", () -> AssemblerBreaksAsOneTest.CODEC);
        TEST_TYPES.register("assembler_fed_from_any_cell", () -> AssemblerFedFromAnyCellTest.CODEC);
        TEST_TYPES.register("assemblers_tile_walkably", () -> AssemblersTileWalkablyTest.CODEC);
    }

    /** Called from the mod constructor so the test types register with everything else. */
    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        // Our own environment rather than a lookup of minecraft:default, because the event
        // exposes no getter for one that already exists. An empty AllOf imposes no conditions,
        // which is exactly what minecraft:default is.
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "assembler_places", AssemblerPlacesTest::new, 20);
        register(event, environment, "assembler_holds_items", AssemblerHoldsItemsTest::new, 20);
        register(event, environment, "assembler_crafts", AssemblerCraftsTest::new, 100);
        register(event, environment, "assembler_sleeps", AssemblerSleepsTest::new, 60);
        register(event, environment, "assembler_stalls_when_full", AssemblerStallsWhenFullTest::new, 100);
        register(event, environment, "assembler_spills_when_broken", AssemblerSpillsWhenBrokenTest::new, 60);
        register(event, environment, "assembler_menu_selects_recipe", AssemblerMenuSelectsRecipeTest::new, 60);
        register(event, environment, "assembler_needs_power", AssemblerNeedsPowerTest::new, 100);
        register(event, environment, "assembler_wakes_when_power_arrives",
                AssemblerWakesWhenPowerArrivesTest::new, 100);
        register(event, environment, "assembler_gives_no_power_back",
                AssemblerGivesNoPowerBackTest::new, 40);
        register(event, environment, "assembler_is_ten_blocks", AssemblerIsTenBlocksTest::new, 20);
        register(event, environment, "assembler_breaks_as_one", AssemblerBreaksAsOneTest::new, 40);
        register(event, environment, "assembler_fed_from_any_cell",
                AssemblerFedFromAnyCellTest::new, 20);
        register(event, environment, "assemblers_tile_walkably",
                AssemblersTileWalkablyTest::new, 20);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisMachines.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE)));
    }

    /**
     * Puts a whole assembler in, all ten blocks of it, anchored here.
     *
     * <p>Not {@code helper.setBlock}, which would leave one block of a machine standing on its
     * own. That is not merely incomplete: the teardown rule in {@code Multiblock} destroys a cell
     * whose neighbours are not its machine's other cells, so a lone middle block survives only
     * until something next to it changes, and a test that placed one would fail somewhere else
     * entirely.
     */
    private static void placeMachine(GameTestHelper helper, BlockPos anchor) {
        AssemblerBlock block = ModBlocks.ASSEMBLING_MACHINE_1.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState());
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

    /** An item by id, so a test can name another mod's item without a compile-time dependency. */
    private static Item item(GameTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
        return item;
    }

    /**
     * Places a machine and points it at the one recipe every test here uses - its own, which is
     * the only one milestone 1 can pay for.
     */
    private static AssemblerBlockEntity machineMaking(GameTestHelper helper, Item product) {
        placeMachine(helper, MACHINE);
        AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
        ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(helper.getLevel(), product);
        helper.assertTrue(recipe != null,
                "no timed recipe makes this item - is the generated recipe on disk, and are "
                        + "facrafting and neoprogressivematerials both loaded?");
        assembler.setRecipe(recipe);
        charge(assembler);
        return assembler;
    }

    /**
     * Fills the machine's buffer the way a power pole would.
     *
     * <p>Every test that expects a craft to happen calls this, because since the assembler became
     * electric a craft that does not happen is ambiguous. The tests about power do not call it -
     * that is what they are for.
     */
    private static void charge(AssemblerBlockEntity assembler) {
        try (Transaction transaction = Transaction.openRoot()) {
            assembler.gridView().insert(AssemblerBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    /** Places a machine with a recipe and an empty buffer. */
    private static AssemblerBlockEntity unpoweredMachineMaking(GameTestHelper helper, Item product) {
        placeMachine(helper, MACHINE);
        AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
        ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(helper.getLevel(), product);
        helper.assertTrue(recipe != null, "no timed recipe makes this item");
        assembler.setRecipe(recipe);
        return assembler;
    }

    /** One craft's worth of ingredients, put in the way an inserter will. */
    private static void feedOneCraft(GameTestHelper helper, ResourceHandler<ItemResource> view) {
        helper.assertValueEqual(
                insert(view, item(helper, "neoprogressivematerials:electronic_circuit"), 3), 3,
                "circuits accepted");
        helper.assertValueEqual(
                insert(view, item(helper, "neoprogressivematerials:iron_gear_wheel"), 5), 5,
                "gear wheels accepted");
        helper.assertValueEqual(insert(view, Items.IRON_INGOT, 9), 9, "iron plates accepted");
    }

    /**
     * Whether the machine has a block tick coming.
     *
     * <p>This is what "asleep" means here, and it is worth asserting directly: a machine that
     * ticks and does nothing looks identical from the outside and costs exactly as much as one
     * that works.
     */
    private static boolean isScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(MACHINE), ModBlocks.ASSEMBLING_MACHINE_1.get());
    }

    /** Puts items in the way an inserter will: through the published capability view. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** The assembling machine exists in the world, not merely in a registry. */
    public static class AssemblerPlacesTest extends GameTestInstance {

        public static final MapCodec<AssemblerPlacesTest> CODEC = RecordCodecBuilder.<AssemblerPlacesTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(AssemblerPlacesTest::info))
                        .apply(i, AssemblerPlacesTest::new));

        public AssemblerPlacesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeMachine(helper, MACHINE);
            helper.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_1.get(), MACHINE);
            helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler places");
        }
    }

    /**
     * The inventory holds what is put in it, and the published view lets things in one end only.
     *
     * <p>The asymmetry is the point. A hopper under an assembler must take the product and not
     * drain the ingredients it was just fed, and that rule lives in
     * {@link com.jaguarm.nauvismachines.machine.MachineAccess} rather than in the inventory.
     */
    public static class AssemblerHoldsItemsTest extends GameTestInstance {

        public static final MapCodec<AssemblerHoldsItemsTest> CODEC =
                RecordCodecBuilder.<AssemblerHoldsItemsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerHoldsItemsTest::info))
                                .apply(i, AssemblerHoldsItemsTest::new));

        public AssemblerHoldsItemsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeMachine(helper, MACHINE);
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            ResourceHandler<ItemResource> view = assembler.automationView();

            helper.assertValueEqual(insert(view, Items.IRON_INGOT, 10), 10, "ingots accepted");
            helper.assertValueEqual(
                    assembler.inventory().getAmountAsInt(0), 10, "ingots in the first input slot");

            // Extraction from an input slot is refused: those are the machine's to spend.
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(
                        view.extract(ItemResource.of(Items.IRON_INGOT), 10, transaction), 0,
                        "ingredients taken back out through the capability");
            }

            // A result in the output slot is extractable, and nothing may be inserted there.
            assembler.inventory().set(AssemblerBlockEntity.OUTPUT_SLOT, ItemResource.of(Items.IRON_BLOCK), 1);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(
                        view.insert(AssemblerBlockEntity.OUTPUT_SLOT, ItemResource.of(Items.IRON_BLOCK), 1, transaction),
                        0, "items inserted into the output slot");
                helper.assertValueEqual(
                        view.extract(ItemResource.of(Items.IRON_BLOCK), 1, transaction), 1,
                        "results taken out of the output slot");
            }

            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler holds items");
        }
    }

    /**
     * Milestone 1, in one assertion: three circuits, five gears and nine iron plates go in, and
     * half a second later an assembling machine comes out.
     *
     * <p>Those numbers are Factorio's, they come from the generated recipe rather than from this
     * file, and {@code checkRecipes} fails the build if the recipe on disk ever disagrees.
     */
    public static class AssemblerCraftsTest extends GameTestInstance {

        public static final MapCodec<AssemblerCraftsTest> CODEC =
                RecordCodecBuilder.<AssemblerCraftsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerCraftsTest::info))
                                .apply(i, AssemblerCraftsTest::new));

        public AssemblerCraftsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Item product = ModItems.ASSEMBLING_MACHINE_1.get();
            AssemblerBlockEntity assembler = machineMaking(helper, product);
            feedOneCraft(helper, assembler.automationView());

            // Ten ticks exactly: Factorio's half a second, from the generated recipe. Checked on
            // the tick it should land on rather than "eventually" - at nine this test fails, and
            // a craft time that silently drifted would fail it too.
            helper.runAfterDelay(CRAFT_TICKS, () -> {
                AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
                helper.assertValueEqual(
                        machine.inventory().getResource(AssemblerBlockEntity.OUTPUT_SLOT).getItem(),
                        product, "the item in the output slot");
                helper.assertValueEqual(
                        machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                        "assembling machines made");

                for (int slot = 0; slot < AssemblerBlockEntity.INPUT_SLOTS; slot++) {
                    helper.assertValueEqual(
                            machine.inventory().getAmountAsInt(slot), 0, "leftovers in input slot " + slot);
                }
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler crafts");
        }
    }

    /**
     * Non-negotiable #5, asserted rather than remembered: a machine with a recipe and nothing to
     * make it from must not be scheduled to tick at all.
     *
     * <p>Setting the recipe wakes it, so this proves both halves - that it woke, looked, and put
     * itself back to sleep, rather than that it never started.
     */
    public static class AssemblerSleepsTest extends GameTestInstance {

        public static final MapCodec<AssemblerSleepsTest> CODEC =
                RecordCodecBuilder.<AssemblerSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerSleepsTest::info))
                                .apply(i, AssemblerSleepsTest::new));

        public AssemblerSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            machineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());

            helper.runAfterDelay(CRAFT_TICKS, () -> {
                helper.assertFalse(isScheduled(helper),
                        "an assembler with a recipe but no ingredients is still scheduled to tick");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler sleeps");
        }
    }

    /**
     * A machine whose output slot is full holds onto its ingredients rather than voiding them.
     *
     * <p>The whole craft - paying the ingredients and banking the result - happens inside one
     * transaction for exactly this reason: a result that will not fit rolls the ingredients back
     * as though the craft never started. Getting this wrong destroys items in a way a player
     * notices only as a base that quietly runs short.
     */
    public static class AssemblerStallsWhenFullTest extends GameTestInstance {

        public static final MapCodec<AssemblerStallsWhenFullTest> CODEC =
                RecordCodecBuilder.<AssemblerStallsWhenFullTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerStallsWhenFullTest::info))
                                .apply(i, AssemblerStallsWhenFullTest::new));

        public AssemblerStallsWhenFullTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Item product = ModItems.ASSEMBLING_MACHINE_1.get();
            AssemblerBlockEntity assembler = machineMaking(helper, product);

            // A full output slot. Set directly rather than inserted, because the published view
            // refuses insertion here - which is what the previous test is about.
            int full = product.getDefaultMaxStackSize();
            assembler.inventory().set(AssemblerBlockEntity.OUTPUT_SLOT, ItemResource.of(product), full);
            feedOneCraft(helper, assembler.automationView());

            helper.runAfterDelay(CRAFT_TICKS * 3, () -> {
                AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
                helper.assertValueEqual(
                        machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), full,
                        "items in the output slot");

                int ingredients = 0;
                for (int slot = 0; slot < AssemblerBlockEntity.INPUT_SLOTS; slot++) {
                    ingredients += machine.inventory().getAmountAsInt(slot);
                }
                helper.assertValueEqual(ingredients, 3 + 5 + 9, "ingredients still waiting to be spent");

                helper.assertFalse(isScheduled(helper),
                        "an assembler that cannot put its result anywhere is still scheduled to tick");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler stalls when full");
        }
    }

    /**
     * Breaking a machine gives its contents back rather than eating them.
     *
     * <p>Cheap to test and expensive to get wrong: a machine that swallows a stack on every
     * break is the kind of bug a player reads as bad luck for a long time before reporting it.
     */
    public static class AssemblerSpillsWhenBrokenTest extends GameTestInstance {

        public static final MapCodec<AssemblerSpillsWhenBrokenTest> CODEC =
                RecordCodecBuilder.<AssemblerSpillsWhenBrokenTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerSpillsWhenBrokenTest::info))
                                .apply(i, AssemblerSpillsWhenBrokenTest::new));

        public AssemblerSpillsWhenBrokenTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeMachine(helper, MACHINE);
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.assertValueEqual(
                    insert(assembler.automationView(), Items.IRON_INGOT, 7), 7, "ingots accepted");

            // Not helper.destroyBlock, which passes dropBlock = false and so would never
            // exercise the loot table. A block that drops nothing is the plural-directory
            // failure in docs/API-26.2.md, and it is worth catching here.
            helper.getLevel().destroyBlock(helper.absolutePos(MACHINE), true);

            // A tick later: the item entities are not queryable in the tick that spawned them.
            helper.runAfterDelay(2, () -> {
                helper.assertItemEntityPresent(ModItems.ASSEMBLING_MACHINE_1.get(), MACHINE, 2.0);
                helper.assertItemEntityCountIs(Items.IRON_INGOT, MACHINE, 2.0, 7);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler spills when broken");
        }
    }

    /**
     * Opening the machine gives a menu that can point it at a recipe.
     *
     * <p>Everything a screen does that a test can reach: the menu is built, it is the one the
     * player has open, and {@code RecipeSelector.selectRecipe} - the method Facrafting's panel
     * calls through a payload - reaches the block entity. What it looks like is not testable and
     * is Yannic's to judge; that the wiring behind it works is, and this is where it breaks
     * silently otherwise.
     */
    public static class AssemblerMenuSelectsRecipeTest extends GameTestInstance {

        public static final MapCodec<AssemblerMenuSelectsRecipeTest> CODEC =
                RecordCodecBuilder.<AssemblerMenuSelectsRecipeTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerMenuSelectsRecipeTest::info))
                                .apply(i, AssemblerMenuSelectsRecipeTest::new));

        public AssemblerMenuSelectsRecipeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            placeMachine(helper, MACHINE);
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);

            // The menu is built the way MenuProvider builds it, rather than through
            // player.openMenu: opening a screen sends NeoForge's advanced_open_screen payload, and
            // a mock player's connection has never negotiated a payload registry to receive it.
            // What that would add over this is vanilla's own plumbing; what is below is ours.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            AbstractContainerMenu opened = assembler.createMenu(1, player.getInventory(), player);

            helper.assertTrue(opened instanceof AssemblerMenu,
                    "opening an assembler did not give an assembler menu");
            AssemblerMenu menu = (AssemblerMenu) opened;

            helper.assertValueEqual(menu.slots.size(), AssemblerBlockEntity.SLOT_COUNT + 36,
                    "slots on the assembler menu");
            helper.assertTrue(menu.selectedRecipe() == null, "a fresh machine is already making something");

            ResourceKey<Recipe<?>> recipe =
                    AssemblerBlockEntity.recipeProducing(level, ModItems.ASSEMBLING_MACHINE_1.get());
            helper.assertTrue(recipe != null, "no timed recipe makes an assembling machine");

            // The verb Facrafting's panel invokes, straight through the interface.
            menu.selectRecipe(recipe);
            helper.assertValueEqual(assembler.recipeKey(), recipe, "the recipe the machine was pointed at");
            helper.assertValueEqual(menu.selectedRecipe(), recipe, "the recipe the menu reports back");

            // And clicking it a second time turns the machine off again.
            menu.selectRecipe(null);
            helper.assertTrue(assembler.recipeKey() == null, "selecting nothing did not clear the recipe");

            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler menu selects recipe");
        }
    }

    /**
     * An assembler with everything except electricity makes nothing.
     *
     * <p>Factorio's assembling machine 1 is electric, and this is what makes the boiler, the
     * engine and the pole part of the factory rather than a demonstration standing beside it.
     *
     * <p>It also asserts the machine is <em>asleep</em> rather than merely stalled. A machine
     * that spins on a craft it cannot pay for costs exactly as much as one that works, and looks
     * identical from anywhere except a profiler.
     */
    public static class AssemblerNeedsPowerTest extends GameTestInstance {

        public static final MapCodec<AssemblerNeedsPowerTest> CODEC =
                RecordCodecBuilder.<AssemblerNeedsPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerNeedsPowerTest::info))
                                .apply(i, AssemblerNeedsPowerTest::new));

        public AssemblerNeedsPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            AssemblerBlockEntity assembler =
                    unpoweredMachineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());
            feedOneCraft(helper, assembler.automationView());

            helper.runAfterDelay(CRAFT_TICKS * 4, () -> {
                AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
                helper.assertValueEqual(
                        machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 0,
                        "items made by an assembler with no electricity");

                int ingredients = 0;
                for (int slot = 0; slot < AssemblerBlockEntity.INPUT_SLOTS; slot++) {
                    ingredients += machine.inventory().getAmountAsInt(slot);
                }
                helper.assertValueEqual(ingredients, 3 + 5 + 9,
                        "ingredients still waiting in an unpowered assembler");

                helper.assertFalse(isScheduled(helper),
                        "an assembler with no electricity is still scheduled to tick, so it is "
                                + "spinning on a craft it cannot pay for");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler needs power");
        }
    }

    /**
     * <b>The other half of sleeping, and the half that is easy to get wrong.</b>
     *
     * <p>A machine that stopped for want of power is not scheduled for anything, so nothing it
     * does can start it again - the wake has to arrive from outside, through the energy handler.
     * Deleting {@code MachinePower}'s callback leaves every other test in this file passing and
     * fails this one, which is the whole reason it is written separately: a factory that stops
     * for good the first time the coal runs out is a bug nobody sees until it happens.
     */
    public static class AssemblerWakesWhenPowerArrivesTest extends GameTestInstance {

        public static final MapCodec<AssemblerWakesWhenPowerArrivesTest> CODEC =
                RecordCodecBuilder.<AssemblerWakesWhenPowerArrivesTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerWakesWhenPowerArrivesTest::info))
                                .apply(i, AssemblerWakesWhenPowerArrivesTest::new));

        public AssemblerWakesWhenPowerArrivesTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            AssemblerBlockEntity assembler =
                    unpoweredMachineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());
            feedOneCraft(helper, assembler.automationView());

            helper.startSequence()
                    .thenExecuteAfter(CRAFT_TICKS * 2, () -> helper.assertFalse(isScheduled(helper),
                            "the machine did not stop, so this test cannot prove it restarts"))
                    .thenExecute(() -> {
                        charge(helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class));
                        helper.assertTrue(isScheduled(helper),
                                "electricity arrived and the machine was not woken - it will sleep "
                                        + "through the grid coming back");
                    })
                    .thenExecuteAfter(CRAFT_TICKS + 2, () -> helper.assertValueEqual(
                            helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class)
                                    .inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT),
                            1,
                            "items made after the power came back"))
                    .thenSucceed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler wakes when power arrives");
        }
    }

    /** A machine is not a battery: what fills it must not be able to empty it again. */
    public static class AssemblerGivesNoPowerBackTest extends GameTestInstance {

        public static final MapCodec<AssemblerGivesNoPowerBackTest> CODEC =
                RecordCodecBuilder.<AssemblerGivesNoPowerBackTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerGivesNoPowerBackTest::info))
                                .apply(i, AssemblerGivesNoPowerBackTest::new));

        public AssemblerGivesNoPowerBackTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            AssemblerBlockEntity assembler = machineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());
            helper.assertValueEqual(assembler.energyStored(), AssemblerBlockEntity.ENERGY_CAPACITY,
                    "charge in a machine the grid just filled");

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(assembler.gridView().extract(1000, transaction), 0,
                        "energy taken back out of a machine");
                transaction.commit();
            }
            helper.assertValueEqual(assembler.energyStored(), AssemblerBlockEntity.ENERGY_CAPACITY,
                    "charge after something tried to drain it");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler gives no power back");
        }
    }

    /**
     * A machine is ten blocks, they are the right ten, and only one of them holds anything.
     *
     * <p>The footprint is Factorio identity - three tiles by three - so this asserts the count and
     * the arrangement rather than trusting the shape class to have been read correctly. It also
     * asserts the thing that would otherwise be found by a crash: nine of the ten have no block
     * entity, and every one of them can still name the tenth.
     */
    public static class AssemblerIsTenBlocksTest extends GameTestInstance {

        public static final MapCodec<AssemblerIsTenBlocksTest> CODEC =
                RecordCodecBuilder.<AssemblerIsTenBlocksTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerIsTenBlocksTest::info))
                                .apply(i, AssemblerIsTenBlocksTest::new));

        public AssemblerIsTenBlocksTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            AssemblerBlock block = ModBlocks.ASSEMBLING_MACHINE_1.get();
            MachineShape shape = AssemblerShape.SHAPE;
            placeMachine(helper, MACHINE);

            helper.assertValueEqual(shape.cellCount(), 10, "blocks in an assembler");

            for (int part = 0; part < shape.cellCount(); part++) {
                BlockPos pos = MACHINE.offset(shape.offset(part, Direction.NORTH));

                helper.assertBlockPresent(block, pos);
                helper.assertValueEqual(helper.getBlockState(pos).getValue(shape.part()), part,
                        "which cell the block at " + pos + " says it is");

                // Every cell knows where the machine keeps its things, from its blockstate alone.
                helper.assertValueEqual(
                        Multiblock.anchorPos(block, helper.getBlockState(pos), helper.absolutePos(pos)),
                        helper.absolutePos(MACHINE), "anchor as seen from " + pos);

                boolean isAnchor = part == shape.anchor();
                boolean hasBlockEntity =
                        helper.getLevel().getBlockEntity(helper.absolutePos(pos)) != null;
                helper.assertValueEqual(hasBlockEntity, isAnchor,
                        "block entity at " + pos + ", where only the middle should have one");
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an assembler is ten blocks");
        }
    }

    /**
     * Break any one of the ten and the whole machine comes down, giving back exactly one machine.
     *
     * <p>A corner is broken rather than the middle, because the corner is the harder case: it is
     * two blocks from the anchor, it has no block entity, and its own loot table entry is
     * conditioned away. Everything after it is the teardown rule cascading, and the two ways that
     * goes wrong are both silent - blocks left standing with nothing to break them, or ten
     * machines dropped where one was placed.
     */
    public static class AssemblerBreaksAsOneTest extends GameTestInstance {

        public static final MapCodec<AssemblerBreaksAsOneTest> CODEC =
                RecordCodecBuilder.<AssemblerBreaksAsOneTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerBreaksAsOneTest::info))
                                .apply(i, AssemblerBreaksAsOneTest::new));

        public AssemblerBreaksAsOneTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeMachine(helper, MACHINE);

            // dropBlock = true, so the loot table actually runs. See the spill test.
            helper.getLevel().destroyBlock(helper.absolutePos(MACHINE.offset(-1, 0, -1)), true);

            helper.runAfterDelay(2, () -> {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        helper.assertBlockPresent(Blocks.AIR, MACHINE.offset(x, 0, z));
                    }
                }
                helper.assertBlockPresent(Blocks.AIR, MACHINE.above());

                helper.assertItemEntityCountIs(ModItems.ASSEMBLING_MACHINE_1.get(), MACHINE, 4.0, 1);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an assembler breaks as one");
        }
    }

    /**
     * An inserter can feed the machine from anywhere along it, which is the point of a footprint.
     *
     * <p>A one-block assembler had one place to stand next to. A 3x3 has twelve faces round its
     * edge and a roof, and Factorio expects all of them to work. This asserts that a far corner
     * and the top of the gearbox - the two cells furthest from the block entity - reach the same
     * inventory, which is what registering the capability against the block rather than the block
     * entity buys. See {@code ModCapabilities}.
     */
    public static class AssemblerFedFromAnyCellTest extends GameTestInstance {

        public static final MapCodec<AssemblerFedFromAnyCellTest> CODEC =
                RecordCodecBuilder.<AssemblerFedFromAnyCellTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerFedFromAnyCellTest::info))
                                .apply(i, AssemblerFedFromAnyCellTest::new));

        public AssemblerFedFromAnyCellTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            placeMachine(helper, MACHINE);
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);

            helper.assertValueEqual(insert(view(helper, MACHINE.offset(-1, 0, -1), Direction.WEST),
                    Items.IRON_INGOT, 4), 4, "ingots taken at the far corner");
            helper.assertValueEqual(insert(view(helper, MACHINE.above(), Direction.UP),
                    Items.IRON_INGOT, 3), 3, "ingots taken on top of the gearbox");

            int held = 0;
            for (int slot = 0; slot < assembler.inventory().size(); slot++) {
                held += assembler.inventory().getAmountAsInt(slot);
            }
            helper.assertValueEqual(held, 7, "ingots that reached the one inventory");
            helper.succeed();
        }

        /** What a hopper or an inserter against this face of this block would see. */
        private static ResourceHandler<ItemResource> view(GameTestHelper helper, BlockPos pos,
                Direction side) {
            ResourceHandler<ItemResource> handler = Capabilities.Item.BLOCK.getCapability(
                    helper.getLevel(), helper.absolutePos(pos), null, null, side);
            helper.assertTrue(handler != null, "no item capability at " + pos + " on its " + side);
            return handler;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("an assembler is fed from any of its blocks");
        }
    }

    /**
     * Two assemblers packed against each other, and you can still walk over them.
     *
     * <p>This is the requirement that shaped the machine, and it is one a person would find only
     * by building a factory and then getting stuck in it. A Factorio player tiles assemblers with
     * no gaps between them, and a field of 3x3 machines two solid blocks tall would be a wall -
     * you cannot jump two blocks, so there would be no way across your own base.
     *
     * <p>What is asserted is the walk itself, along the row where the two machines meet: no column
     * higher than one block, and no step between neighbouring columns larger than the 0.6 a player
     * climbs for free. The gearboxes are then asserted to be the two blocks tall they look, so
     * this cannot come out green by the machine quietly going flat.
     */
    public static class AssemblersTileWalkablyTest extends GameTestInstance {

        public static final MapCodec<AssemblersTileWalkablyTest> CODEC =
                RecordCodecBuilder.<AssemblersTileWalkablyTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblersTileWalkablyTest::info))
                                .apply(i, AssemblersTileWalkablyTest::new));

        public AssemblersTileWalkablyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        /** Vanilla's two numbers: what a player climbs without jumping, and how high they jump. */
        private static final double STEP = 0.6;
        private static final double JUMP = 1.25;

        @Override
        public void run(GameTestHelper helper) {
            BlockPos second = MACHINE.offset(3, 0, 0);
            placeMachine(helper, MACHINE);
            placeMachine(helper, second);

            // Both are still standing: neither mistook the other for part of itself, and no
            // teardown fired along the seam where they touch.
            helper.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_1.get(), MACHINE.offset(1, 0, 0));
            helper.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_1.get(), second.offset(-1, 0, 0));

            double previous = 0;
            for (int x = -1; x <= 4; x++) {
                BlockPos column = MACHINE.offset(x, 0, -1);
                double top = surface(helper, column, 2);

                helper.assertTrue(top <= JUMP,
                        "the lane at x=" + x + " stands " + top + " blocks high, which is more "
                                + "than the " + JUMP + " a player can jump onto");
                helper.assertTrue(x == -1 || Math.abs(top - previous) <= STEP,
                        "the step from x=" + (x - 1) + " to x=" + x + " is "
                                + Math.abs(top - previous) + " blocks, more than the " + STEP
                                + " a player takes for free");
                helper.assertTrue(helper.getBlockState(column.above(2)).isAir(),
                        "no headroom over the lane at x=" + x);
                previous = top;
            }

            // And the machines are not simply flat: each has a gearbox you walk around.
            helper.assertValueEqual(surface(helper, MACHINE, 2), 2.0, "height of the first gearbox");
            helper.assertValueEqual(surface(helper, second, 2), 2.0, "height of the second gearbox");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assemblers tile walkably");
        }
    }
}
