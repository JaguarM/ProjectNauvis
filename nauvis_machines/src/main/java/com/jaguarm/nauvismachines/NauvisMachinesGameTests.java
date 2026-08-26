package com.jaguarm.nauvismachines;

import java.util.List;

import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerMenu;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
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
        TEST_TYPES.register("assembler_takes_from_hand", () -> AssemblerTakesFromHandTest.CODEC);
        TEST_TYPES.register("assembler_menu_selects_recipe", () -> AssemblerMenuSelectsRecipeTest.CODEC);
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
        register(event, environment, "assembler_takes_from_hand", AssemblerTakesFromHandTest::new, 60);
        register(event, environment, "assembler_menu_selects_recipe", AssemblerMenuSelectsRecipeTest::new, 60);
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
        helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
        AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
        ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(helper.getLevel(), product);
        helper.assertTrue(recipe != null,
                "no timed recipe makes this item - is the generated recipe on disk, and are "
                        + "facrafting and neoprogressivematerials both loaded?");
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
            helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
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
            helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
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
            helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
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
     * A right-click does the job the machine needs doing: an ingredient it is short of goes in,
     * anything else re-points it.
     *
     * <p>One click doing two things is only safe if the rule is exact, which is why it is
     * asserted rather than described. Until inserters exist this is also the only way to feed a
     * machine by hand, so it is the difference between a playable milestone and a screenshot.
     */
    public static class AssemblerTakesFromHandTest extends GameTestInstance {

        public static final MapCodec<AssemblerTakesFromHandTest> CODEC =
                RecordCodecBuilder.<AssemblerTakesFromHandTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(AssemblerTakesFromHandTest::info))
                                .apply(i, AssemblerTakesFromHandTest::new));

        public AssemblerTakesFromHandTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);

            // An idle machine takes the click as "make this".
            helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
            ItemStack pointer = new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, pointer);
            helper.useBlock(MACHINE, player);

            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.assertTrue(assembler.recipeKey() != null, "a click with an item set no recipe");
            helper.assertValueEqual(pointer.getCount(), 1, "items taken by a click that only pointed");

            // Now that it is making assembling machines, iron plates are an ingredient and the
            // same click loads them instead.
            ItemStack iron = new ItemStack(Items.IRON_INGOT, 9);
            player.setItemInHand(InteractionHand.MAIN_HAND, iron);
            helper.useBlock(MACHINE, player);

            helper.assertValueEqual(iron.getCount(), 0, "iron plates left in hand");
            helper.assertValueEqual(assembler.inventory().getAmountAsInt(0), 9, "iron plates loaded");
            helper.assertTrue(assembler.recipeKey() != null,
                    "loading an ingredient must not change what the machine is making");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("assembler takes from hand");
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
            helper.setBlock(MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
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
}
