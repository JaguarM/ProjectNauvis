package com.jaguarm.nauvismachines;

import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.transfer.MachinePower;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine2Block;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerMenu;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerShape;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine2Shape;
import com.jaguarm.nauvismachines.machine.radar.RadarBlockEntity;
import com.jaguarm.nauvismachines.item.RepairPackItem;
import com.jaguarm.nauvislib.health.Health;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
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
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Tests that run inside a real server, headless, reporting pass or fail on exit. */
public final class NauvisMachinesGameTests {

    private NauvisMachinesGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisMachines.MODID, modEventBus);

        // The assembling machine exists in the world, not merely in a registry.
        tests.add("assembler_places", 20, PADDING, helper -> {
            placeMachine(helper, MACHINE);
            helper.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_1.get(), MACHINE);
            helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.succeed();
        });

        // The inventory holds what is put in it, and the published view lets things in one end only.
        //
        // The asymmetry is the point. A hopper under an assembler must take the product and not
        // drain the ingredients it was just fed, and that rule lives in
        // com.jaguarm.nauvislib.transfer.MachineAccess rather than in the inventory.
        tests.add("assembler_holds_items", 20, PADDING, helper -> {
            placeMachine(helper, MACHINE);
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            ResourceHandler<ItemResource> view = assembler.automationView();

            // A machine with no recipe takes nothing, which is Factorio's rule: nothing an
            // inserter drops in can be for anything.
            helper.assertValueEqual(insert(view, Items.IRON_INGOT, 10), 0, "ingots accepted with no recipe");

            // With one, each slot is one ingredient's - the assembler's recipe is circuits, gears
            // and plates in that order - so plates land in the third slot and nowhere else, and a
            // stick, which the recipe has no use for, lands nowhere.
            assembler.setRecipe(AssemblerBlockEntity.recipeProducing(helper.getLevel(), ModItems.ASSEMBLING_MACHINE_1.get()));
            helper.assertValueEqual(insert(view, Items.IRON_INGOT, 10), 10, "ingots accepted");
            helper.assertValueEqual(assembler.inventory().getAmountAsInt(2), 10, "ingots in the plates' slot");
            helper.assertValueEqual(assembler.inventory().getAmountAsInt(0), 0, "ingots in the circuits' slot");
            helper.assertValueEqual(insert(view, Items.STICK, 1), 0, "sticks accepted by a machine that wants none");

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
        });

        // Milestone 1, in one assertion: three circuits, five gears and nine iron plates go in, and
        // half a second later an assembling machine comes out.
        //
        // Those numbers are Factorio's, they come from the generated recipe rather than from this
        // file, and checkRecipes fails the build if the recipe on disk ever disagrees.
        tests.add("assembler_crafts", 100, PADDING, helper -> {
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
        });
        tests.add("assembling_machine_2_is_faster", AssemblingMachine2IsFasterTest::new, 100, PADDING);
        tests.add("speed_modules_speed_an_assembler", SpeedModulesSpeedAnAssemblerTest::new, 100, PADDING);

        // Two productivity modules bank a free gear every twelve and a half crafts.
        //
        // Factorio's productivity bar: each craft adds the modules' bonus - two at a twenty-fifth is
        // two twenty-fifths - and when the bar fills the machine hands over one more product it never
        // paid for. Thirteen crafts' worth of plates go in; thirteen crafts fill the bar past one; and
        // fourteen gears come out, the last of them free. Twelve crafts in, there are exactly twelve.
        tests.add("productivity_modules_bank_a_free_craft", 300, PADDING, helper -> {
            Item gear = item(helper, "nauvis_materials:iron_gear_wheel");
            ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(helper.getLevel(), gear);
            helper.assertTrue(recipe != null, "no timed recipe makes an iron gear wheel");

            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            machine.setRecipe(recipe);
            module(helper, machine.modules(), 0, ModItems.PRODUCTIVITY_MODULE.get());
            module(helper, machine.modules(), 1, ModItems.PRODUCTIVITY_MODULE.get());
            // A gear is ten ticks; at 0.75 times 0.9 that is 14.8, so fifteen a craft.
            helper.onEachTick(() -> charge(machine));
            helper.assertValueEqual(insert(machine.automationView(), Items.IRON_INGOT, 26), 26,
                    "plates for thirteen gears accepted");

            helper.startSequence()
                    .thenExecuteAfter(12 * 15 + 6, () -> {
                        helper.assertValueEqual(
                                machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 12,
                                "gears after twelve crafts - the bar is at 0.96 and nothing is owed yet");
                        helper.assertTrue(Math.abs(machine.productivity().banked() - 0.96) < 1e-6,
                                "the productivity bar after twelve crafts: " + machine.productivity().banked());
                    })
                    .thenExecuteAfter(15, () -> {
                        helper.assertValueEqual(
                                machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 14,
                                "gears after thirteen crafts - the thirteenth fills the bar and one is free");
                        helper.assertValueEqual(machine.inventory().getAmountAsInt(0), 0,
                                "plates left - thirteen crafts paid for, not fourteen");
                        helper.assertTrue(Math.abs(machine.productivity().banked() - 0.04) < 1e-6,
                                "the bar after paying out: " + machine.productivity().banked());
                    })
                    .thenSucceed();
        });

        // A productivity module goes only into a machine making an intermediate product.
        //
        // Factorio's one restriction on modules, read off the recipe's crafting-menu tab: a gear is
        // an intermediate, a stone furnace is not. Refused at the slot, so the screen refuses it; and a
        // recipe that may not have them is refused while one sits in the machine, rather than the
        // module being thrown out or quietly ignored. A machine with no recipe takes one, as Factorio's
        // does.
        tests.add("productivity_module_needs_an_intermediate", 20, PADDING, helper -> {
            ResourceKey<Recipe<?>> gear = AssemblerBlockEntity.recipeProducing(
                    helper.getLevel(), item(helper, "nauvis_materials:iron_gear_wheel"));
            ResourceKey<Recipe<?>> furnace = AssemblerBlockEntity.recipeProducing(
                    helper.getLevel(), ModItems.STONE_FURNACE.get());
            helper.assertTrue(gear != null && furnace != null, "the gear and the stone furnace recipes");

            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            ItemResource productivity = ItemResource.of(ModItems.PRODUCTIVITY_MODULE.get());
            ItemResource speed = ItemResource.of(ModItems.SPEED_MODULE.get());

            helper.assertTrue(machine.modules().isValid(0, productivity),
                    "a productivity module refused by a machine with no recipe");

            machine.setRecipe(furnace);
            helper.assertFalse(machine.modules().isValid(0, productivity),
                    "a productivity module accepted by a machine making a stone furnace");
            helper.assertTrue(machine.modules().isValid(0, speed),
                    "a speed module refused by a machine making a stone furnace");

            machine.setRecipe(gear);
            helper.assertTrue(machine.modules().isValid(0, productivity),
                    "a productivity module refused by a machine making gears");
            module(helper, machine.modules(), 0, ModItems.PRODUCTIVITY_MODULE.get());

            machine.setRecipe(furnace);
            helper.assertValueEqual(machine.recipeKey(), gear,
                    "the recipe after choosing a stone furnace with a productivity module in - "
                            + "Factorio refuses the recipe, and so should this");
            helper.assertValueEqual(machine.modules().getAmountAsInt(0), 1,
                    "the productivity module, which must not have been thrown out");
            helper.succeed();
        });

        // The first machine has no fluid box and the second has two, at two faces and no others.
        //
        // Factorio's rule and the reason there are tiers. A pipe against the second machine's north
        // edge fills its input, one against the south edge drains its output, and a pipe on a flank or
        // a corner finds nothing - exactly as a refinery's flank offers nothing. The first machine
        // offers nothing anywhere and refuses a recipe with a fluid in it.
        tests.add("only_the_second_machine_has_fluid_boxes", 20, PADDING, helper -> {
            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
            AssemblerBlockEntity first = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.assertFalse(first.hasFluidBoxes(), "an assembling machine 1 has a fluid box");
            for (int part = 0; part < AssemblerShape.SHAPE.cellCount(); part++) {
                for (Direction side : Direction.values()) {
                    helper.assertTrue(fluidAt(helper, cell(AssemblerShape.SHAPE, part), side) == null,
                            "an assembling machine 1 offers a fluid handler at cell " + part + " " + side);
                }
            }

            helper.getLevel().destroyBlock(helper.absolutePos(MACHINE), false);
            helper.startSequence().thenExecuteAfter(3, () -> {
                placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
                AssemblerBlockEntity second = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
                helper.assertTrue(second.hasFluidBoxes(), "an assembling machine 2 has no fluid box");
                MachineShape shape = AssemblingMachine2Shape.SHAPE;
                helper.assertTrue(fluidAt(helper, cell(shape, AssemblingMachine2Shape.NORTH_EDGE), Direction.NORTH) != null,
                        "no fluid handler at the second machine's input port");
                helper.assertTrue(fluidAt(helper, cell(shape, AssemblingMachine2Shape.SOUTH_EDGE), Direction.SOUTH) != null,
                        "no fluid handler at the second machine's output port");
                helper.assertTrue(fluidAt(helper, cell(shape, AssemblingMachine2Shape.NORTH_EDGE), Direction.UP) == null,
                        "a fluid handler on top of the input cell, where no pipe is drawn");
                helper.assertTrue(fluidAt(helper, cell(shape, 5), Direction.EAST) == null,
                        "a fluid handler on the second machine's flank");
                helper.assertTrue(fluidAt(helper, cell(shape, 0), Direction.NORTH) == null,
                        "a fluid handler on the second machine's corner");

                // Drawing from the input, or filling the output, is refused: a pipe run must not
                // drain the lubricant back out, and must not pour into a box the machine fills.
                ResourceHandler<FluidResource> out = fluidAt(helper, cell(shape, AssemblingMachine2Shape.SOUTH_EDGE), Direction.SOUTH);
                helper.assertValueEqual(fill(out, Fluids.WATER, 10), 0, "water a pipe could pour into the output box");
            }).thenSucceed();
        });

        // An assembling machine 2 makes an electric engine unit: two circuits and an engine unit from
        // its slots, fifteen lubricant from its fluid box, ten seconds at 0.75.
        //
        // The first item in the pack made from a fluid in an assembler. The lubricant is another
        // mod's fluid and the recipe needs it, so this passes on the tier's refusal alone when the
        // recipe is not here - the standalone run - and runs the craft in the pack. The input box takes
        // only the recipe's fluid: water against it is refused, which is what keeps a wrong pipe from
        // filling a machine that could never use it.
        tests.add("assembling_machine_2_crafts_with_a_fluid", 400, PADDING, helper -> {
            Item product = BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("nauvis_materials", "electric_engine_unit"));
            Fluid lubricant = BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "lubricant"));
            ResourceKey<Recipe<?>> recipe = product == Items.AIR ? null
                    : AssemblerBlockEntity.recipeProducing(helper.getLevel(), product);
            if (recipe == null || lubricant == Fluids.EMPTY) {
                helper.succeed();
                return;
            }

            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_1.get());
            AssemblerBlockEntity first = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            first.setRecipe(recipe);
            helper.assertTrue(first.recipeKey() == null,
                    "an assembling machine 1 accepted a recipe with a fluid in it");
            helper.getLevel().destroyBlock(helper.absolutePos(MACHINE), false);

            // Registered up front, because the test framework's tick map cannot be added to from
            // inside one of its own callbacks. The machine arrives a few ticks in.
            AssemblerBlockEntity[] machine = new AssemblerBlockEntity[1];
            helper.onEachTick(() -> {
                if (machine[0] != null) {
                    charge(machine[0]);
                }
            });

            // Two hundred ticks over 0.75 is 266.7, so 267.
            int ticks = Math.round(200 / AssemblingMachine2Block.CRAFTING_SPEED);
            helper.startSequence()
                    .thenExecuteAfter(3, () -> {
                        placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
                        machine[0] = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
                        machine[0].setRecipe(recipe);
                        helper.assertValueEqual(machine[0].recipeKey(), recipe, "the recipe on an assembling machine 2");

                        ResourceHandler<FluidResource> in = fluidAt(helper,
                                cell(AssemblingMachine2Shape.SHAPE, AssemblingMachine2Shape.NORTH_EDGE), Direction.NORTH);
                        helper.assertTrue(in != null, "no handler at the input port");
                        helper.assertValueEqual(fill(in, Fluids.WATER, 100), 0, "water taken by a box pointed at lubricant");
                        helper.assertValueEqual(fill(in, lubricant, 100), 100, "lubricant taken by the input box");
                        helper.assertValueEqual(insert(machine[0].automationView(),
                                item(helper, "nauvis_materials:electronic_circuit"), 2), 2, "circuits accepted");
                        helper.assertValueEqual(insert(machine[0].automationView(),
                                item(helper, "nauvis_materials:engine_unit"), 1), 1, "an engine unit accepted");
                    })
                    .thenExecuteAfter(ticks + 3, () -> {
                        helper.assertValueEqual(machine[0].inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                                "electric engine units made after " + (ticks + 3) + " ticks");
                        helper.assertValueEqual(machine[0].fluidIn().getAmountAsInt(0), 85,
                                "lubricant left in the box after one craft of fifteen");
                    })
                    .thenSucceed();
        });

        // An assembling machine 2 fills a barrel from its input box and empties one into its output box.
        //
        // Factorio's barrels: an empty barrel and fifty water make a water barrel in a fifth of a
        // second, and the reverse gives the water back - through the output port, which a pipe drains
        // and nothing fills. The barrels are the fluids mod's items, so this passes on nothing when
        // they are not here and runs both ways in the pack.
        tests.add("assembling_machine_2_fills_and_empties_a_barrel", 100, PADDING, helper -> {
            Item empty = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "barrel"));
            Item full = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "water_barrel"));
            if (empty == Items.AIR || full == Items.AIR) {
                helper.succeed();
                return;
            }
            ResourceKey<Recipe<?>> fill = AssemblerBlockEntity.recipeProducing(helper.getLevel(), full);
            ResourceKey<Recipe<?>> drain = ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath("nauvis_fluids", "empty_water_barrel"));
            helper.assertTrue(fill != null, "no recipe fills a water barrel");

            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity machine = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            helper.onEachTick(() -> charge(machine));
            machine.setRecipe(fill);
            ResourceHandler<FluidResource> in = fluidAt(helper,
                    cell(AssemblingMachine2Shape.SHAPE, AssemblingMachine2Shape.NORTH_EDGE), Direction.NORTH);
            helper.assertValueEqual(fill(in, Fluids.WATER, 50), 50, "water taken by the input box");
            helper.assertValueEqual(insert(machine.automationView(), empty, 1), 1, "an empty barrel accepted");

            // A fifth of a second over 0.75 is 5.3, so five ticks.
            helper.startSequence()
                    .thenExecuteAfter(8, () -> {
                        helper.assertValueEqual(machine.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                                "water barrels made");
                        helper.assertTrue(machine.inventory().getResource(AssemblerBlockEntity.OUTPUT_SLOT).is(full),
                                "what the machine made is not a water barrel");
                        helper.assertValueEqual(machine.fluidIn().getAmountAsInt(0), 0, "water left after filling");

                        // Now the other way: the barrel back in, the water out through the output port.
                        machine.inventory().set(AssemblerBlockEntity.OUTPUT_SLOT, ItemResource.EMPTY, 0);
                        machine.setRecipe(drain);
                        helper.assertValueEqual(machine.recipeKey(), drain, "the emptying recipe on the machine");
                        helper.assertValueEqual(insert(machine.automationView(), full, 1), 1, "a water barrel accepted");
                    })
                    .thenExecuteAfter(8, () -> {
                        helper.assertTrue(machine.inventory().getResource(AssemblerBlockEntity.OUTPUT_SLOT).is(empty),
                                "what emptying a barrel left in the output slot");
                        helper.assertValueEqual(machine.fluidOut().getAmountAsInt(0), 50, "water in the output box");
                        ResourceHandler<FluidResource> out = fluidAt(helper,
                                cell(AssemblingMachine2Shape.SHAPE, AssemblingMachine2Shape.SOUTH_EDGE), Direction.SOUTH);
                        try (Transaction transaction = Transaction.openRoot()) {
                            helper.assertValueEqual(out.extract(FluidResource.of(Fluids.WATER), 50, transaction), 50,
                                    "water a pipe drew from the output port");
                            transaction.commit();
                        }
                    })
                    .thenSucceed();
        });

        // Non-negotiable #5, asserted rather than remembered: a machine with a recipe and nothing to
        // make it from must not be scheduled to tick at all.
        //
        // Setting the recipe wakes it, so this proves both halves - that it woke, looked, and put
        // itself back to sleep, rather than that it never started.
        tests.add("assembler_sleeps", 60, PADDING, helper -> {
            machineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());

            helper.runAfterDelay(CRAFT_TICKS, () -> {
                helper.assertFalse(isScheduled(helper),
                        "an assembler with a recipe but no ingredients is still scheduled to tick");
                helper.succeed();
            });
        });

        // A machine whose output slot is full holds onto its ingredients rather than voiding them.
        //
        // The whole craft - paying the ingredients and banking the result - happens inside one
        // transaction for exactly this reason: a result that will not fit rolls the ingredients back
        // as though the craft never started. Getting this wrong destroys items in a way a player
        // notices only as a base that quietly runs short.
        tests.add("assembler_stalls_when_full", 100, PADDING, helper -> {
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
        });

        // Breaking a machine gives its contents back rather than eating them.
        //
        // Cheap to test and expensive to get wrong: a machine that swallows a stack on every
        // break is the kind of bug a player reads as bad luck for a long time before reporting it.
        tests.add("assembler_spills_when_broken", 60, PADDING, helper -> {
            AssemblerBlockEntity assembler = unpoweredMachineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());
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
        });

        // Opening the machine gives a menu that can point it at a recipe.
        //
        // Everything a screen does that a test can reach: the menu is built, it is the one the
        // player has open, and RecipeSelector.selectRecipe - the method Facrafting's panel
        // calls through a payload - reaches the block entity. What it looks like is not testable and
        // is Yannic's to judge; that the wiring behind it works is, and this is where it breaks
        // silently otherwise.
        tests.add("assembler_menu_selects_recipe", 60, PADDING, helper -> {
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
        });

        // The second tier's screen stays open.
        //
        // It did not. The menu checked itself against the first machine's block, the way vanilla's
        // one-block helper does, and an assembling machine 2 failed that check on the tick after its
        // screen opened - the server closed it again before anyone could see. Every other test passed,
        // because none of them opens a menu on a tier 2, and a client boot is the only other thing
        // that would have found it.
        tests.add("assembler_menu_stays_open_on_tier_2", 20, PADDING, helper -> {
            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity assembler = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);

            // A mock player is made at the world's origin; the check is a reach check too.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos beside = helper.absolutePos(MACHINE.offset(2, 1, 0));
            player.setPos(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);

            AbstractContainerMenu menu = assembler.createMenu(1, player.getInventory(), player);
            helper.assertTrue(menu.stillValid(player),
                    "an assembling machine 2's menu reports itself invalid the moment it is built, "
                            + "so its screen closes on the next tick");
            helper.succeed();
        });

        // An assembler with everything except electricity makes nothing.
        //
        // Factorio's assembling machine 1 is electric, and this is what makes the boiler, the
        // engine and the pole part of the factory rather than a demonstration standing beside it.
        //
        // It also asserts the machine is asleep rather than merely stalled. A machine
        // that spins on a craft it cannot pay for costs exactly as much as one that works, and looks
        // identical from anywhere except a profiler.
        tests.add("assembler_needs_power", 100, PADDING, helper -> {
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
        });

        // The other half of sleeping, and the half that is easy to get wrong.
        //
        // A machine that stopped for want of power is not scheduled for anything, so nothing it
        // does can start it again - the wake has to arrive from outside, through the energy handler.
        // Deleting MachinePower's callback leaves every other test in this file passing and
        // fails this one, which is the whole reason it is written separately: a factory that stops
        // for good the first time the coal runs out is a bug nobody sees until it happens.
        tests.add("assembler_wakes_when_power_arrives", 100, PADDING, helper -> {
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
        });

        // A machine is not a battery: what fills it must not be able to empty it again.
        tests.add("assembler_gives_no_power_back", 40, PADDING, helper -> {
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
        });

        // A machine is ten blocks, they are the right ten, and only one of them holds anything.
        //
        // The footprint is Factorio identity - three tiles by three - so this asserts the count and
        // the arrangement rather than trusting the shape class to have been read correctly. It also
        // asserts the thing that would otherwise be found by a crash: nine of the ten have no block
        // entity, and every one of them can still name the tenth.
        tests.add("assembler_is_ten_blocks", 20, PADDING, helper -> {
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
        });

        // Break any one of the ten and the whole machine comes down, giving back exactly one machine.
        //
        // A corner is broken rather than the middle, because the corner is the harder case: it is
        // two blocks from the anchor, it has no block entity, and its own loot table entry is
        // conditioned away. Everything after it is the teardown rule cascading, and the two ways that
        // goes wrong are both silent - blocks left standing with nothing to break them, or ten
        // machines dropped where one was placed.
        tests.add("assembler_breaks_as_one", 40, PADDING, helper -> {
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
        });
        tests.add("assembler_fed_from_any_cell", AssemblerFedFromAnyCellTest::new, 20, PADDING);
        tests.add("assemblers_tile_walkably", AssemblersTileWalkablyTest::new, 20, PADDING);

        // A radar with power holds tickets on the chunks around it, and lets them go when it is
        // broken. The assertion is NeoForge's own count of forced chunks, which no other test here
        // touches: it is false before the radar ticks, true once it has, and false again after the
        // radar is gone - which is what stops a test world keeping forty-nine chunks alive for ever.
        tests.add("a_radar_keeps_its_chunks_loaded", 60, PADDING, helper -> {
            helper.assertFalse(ForcedChunkManager.hasForcedChunks(helper.getLevel()),
                    "something is forcing chunks before the radar exists");
            Multiblock.place(ModBlocks.RADAR.get(), helper.getLevel(), helper.absolutePos(MACHINE),
                    ModBlocks.RADAR.get().defaultBlockState());
            RadarBlockEntity radar = helper.getBlockEntity(MACHINE, RadarBlockEntity.class);
            try (Transaction transaction = Transaction.openRoot()) {
                radar.gridView().insert(RadarBlockEntity.ENERGY_CAPACITY, transaction);
                transaction.commit();
            }
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(radar.isCharting(), "a powered radar is not charting");
                helper.assertTrue(ForcedChunkManager.hasForcedChunks(helper.getLevel()),
                        "a charting radar holds no chunk tickets");
                helper.assertTrue(radar.energyStored() < RadarBlockEntity.ENERGY_CAPACITY,
                        "the radar charted without spending anything");
                helper.destroyBlock(MACHINE);
            });
            helper.runAfterDelay(10, () -> {
                helper.assertFalse(ForcedChunkManager.hasForcedChunks(helper.getLevel()),
                        "a broken radar left its chunk tickets behind");
                helper.succeed();
            });
        });

        // A radar with nothing in its buffer holds no tickets and schedules nothing: non-negotiable #5.
        tests.add("a_radar_without_power_sleeps", 40, PADDING, helper -> {
            Multiblock.place(ModBlocks.RADAR.get(), helper.getLevel(), helper.absolutePos(MACHINE),
                    ModBlocks.RADAR.get().defaultBlockState());
            RadarBlockEntity radar = helper.getBlockEntity(MACHINE, RadarBlockEntity.class);
            helper.runAfterDelay(10, () -> {
                helper.assertFalse(radar.isCharting(), "an unpowered radar is charting");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                        helper.absolutePos(MACHINE), ModBlocks.RADAR.get()), "an unpowered radar is still ticking");
                helper.succeed();
            });
        });

        // An assembler with no health of its own is worth three hundred, from its hardness of three,
        // and a repair pack spends itself mending up to its charge - and is kept when there is nothing
        // to mend.
        tests.add("a_repair_pack_mends_a_machine", 20, PADDING, helper -> {
            placeMachine(helper, MACHINE);
            BlockPos edge = helper.absolutePos(MACHINE.offset(1, 0, 0));
            helper.assertValueEqual(Health.maxHealth(helper.getLevel(), edge), 300.0F, "an assembler's health, from its hardness");

            ItemStack packs = new ItemStack(ModItems.REPAIR_PACK.get(), 3);
            helper.assertValueEqual(RepairPackItem.repair(helper.getLevel(), edge, packs, null), 0.0F,
                    "mended on a whole machine");
            helper.assertValueEqual(packs.getCount(), 3, "packs left after clicking a whole machine");

            Health.hurt(helper.getLevel(), edge, 120);
            helper.assertValueEqual(Health.health(helper.getLevel(), edge), 180.0F, "left after a hit");
            helper.assertValueEqual(RepairPackItem.repair(helper.getLevel(), edge, packs, null), 120.0F, "mended");
            helper.assertValueEqual(packs.getCount(), 2, "packs left after mending");
            helper.assertValueEqual(Health.health(helper.getLevel(), edge), 300.0F, "whole again");
            helper.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_1.get(), MACHINE);
            helper.succeed();
        });
    }

    /** Where every test puts its machine: one block up, so it is not inside the floor. */
    private static final BlockPos MACHINE = new BlockPos(0, 1, 0);

    /**
     * Factorio's craft time for an assembling machine 1: 0.5s, which is ten ticks. Written out
     * here so the test asserts the number rather than waiting long enough not to care.
     */
    /**
     * How long the test recipe takes in the first machine: a ten-tick recipe - Factorio's half a
     * second, from the generated file - at crafting speed 0.5. Twenty, not ten. The recipe's time
     * is the recipe's and the machine's speed is the machine's, and both are Factorio's numbers.
     */
    private static final int CRAFT_TICKS = 20;

    /** How much empty world to leave around each test. */
    private static final int PADDING = 24;

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
        placeMachine(helper, anchor, ModBlocks.ASSEMBLING_MACHINE_1.get());
    }

    private static void placeMachine(GameTestHelper helper, BlockPos anchor, AssemblerBlock block) {
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
                        + "facrafting and nauvis_materials both loaded?");
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
                insert(view, item(helper, "nauvis_materials:electronic_circuit"), 3), 3,
                "circuits accepted");
        helper.assertValueEqual(
                insert(view, item(helper, "nauvis_materials:iron_gear_wheel"), 5), 5,
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

    /**
     * An inserter can feed the machine from anywhere along it, which is the point of a footprint.
     *
     * <p>A one-block assembler had one place to stand next to. A 3x3 has twelve faces round its
     * edge and a roof, and Factorio expects all of them to work. This asserts that a far corner
     * and the top of the gearbox - the two cells furthest from the block entity - reach the same
     * inventory, which is what registering the capability against the block rather than the block
     * entity buys. See {@code ModCapabilities}.
     */
    public static class AssemblerFedFromAnyCellTest extends PackGameTest {

        AssemblerFedFromAnyCellTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            AssemblerBlockEntity assembler = unpoweredMachineMaking(helper, ModItems.ASSEMBLING_MACHINE_1.get());

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

    }

    /** Two assemblers packed against each other, and you can still walk over them. */
    public static class AssemblersTileWalkablyTest extends PackGameTest {

        /** Vanilla's two numbers: what a player climbs without jumping, and how high they jump. */
        private static final double STEP = 0.6;
        private static final double JUMP = 1.25;

        AssemblersTileWalkablyTest(Info info) { super(info); }

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
                // Getting onto the machine in the first place is a jump, and is allowed to be
                // one. Everything after that has to be a step, or crossing a factory is hopping.
                // Only the climbs. Walking off the far side of a machine is a drop, and a
                // drop of one block costs a player nothing at all.
                double climb = top - previous;
                double allowed = previous == 0 ? JUMP : STEP;
                helper.assertTrue(x == -1 || climb <= allowed,
                        "the step up from x=" + (x - 1) + " to x=" + x + " is " + climb
                                + " blocks, more than the " + allowed + " a player manages from "
                                + previous);
                helper.assertTrue(helper.getBlockState(column.above(2)).isAir(),
                        "no headroom over the lane at x=" + x);
                previous = top;
            }

            // And the machines are not simply flat: each has a gearbox you walk around.
            helper.assertValueEqual(surface(helper, MACHINE, 2), 2.0, "height of the first gearbox");
            helper.assertValueEqual(surface(helper, second, 2), 2.0, "height of the second gearbox");
            helper.succeed();
        }

    }

    /**
     * The second machine is a tier: the same recipe, half again as fast, twice the draw.
     *
     * <p>Factorio's crafting speeds are 0.5 and 0.75, so the ten-tick test recipe takes twenty
     * ticks in the first machine and thirteen in the second. Checked on the tick each should land
     * on, and checked <em>not</em> to have landed a tick early in the first, so a speed that
     * silently became 1.0 - which is what the assembler was before it had a tier - fails here.
     */
    public static class AssemblingMachine2IsFasterTest extends PackGameTest {

        private static final BlockPos SECOND = MACHINE.offset(4, 0, 0);

        AssemblingMachine2IsFasterTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            Item product = ModItems.ASSEMBLING_MACHINE_1.get();
            AssemblerBlockEntity first = machineMaking(helper, product);
            feedOneCraft(helper, first.automationView());

            placeMachine(helper, SECOND, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity second = helper.getBlockEntity(SECOND, AssemblerBlockEntity.class);
            second.setRecipe(AssemblerBlockEntity.recipeProducing(helper.getLevel(), product));
            charge(second);
            feedOneCraft(helper, second.automationView());

            helper.assertValueEqual(second.craftingSpeed(), AssemblingMachine2Block.CRAFTING_SPEED,
                    "the second machine's crafting speed");
            helper.assertValueEqual(second.energyPerTick(), 2 * first.energyPerTick(),
                    "the second machine's draw against the first's");

            int secondTicks = Math.round(10 / AssemblingMachine2Block.CRAFTING_SPEED);
            helper.startSequence()
                    .thenExecuteAfter(secondTicks, () -> {
                        helper.assertValueEqual(
                                second.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                                "what the second machine had made after " + secondTicks + " ticks");
                        helper.assertValueEqual(
                                first.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 0,
                                "what the first machine had made after " + secondTicks
                                        + " ticks - it is half speed and should still be working");
                    })
                    .thenExecuteAfter(CRAFT_TICKS - secondTicks, () -> helper.assertValueEqual(
                            first.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                            "what the first machine had made after " + CRAFT_TICKS + " ticks"))
                    .thenSucceed();
        }

    }

    /** Puts a module into a machine's slots the way a player does, through the handler. */
    private static void module(GameTestHelper helper, ModuleSlots slots, int slot, Item module) {
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertValueEqual(slots.insert(slot, ItemResource.of(module), 1, transaction), 1,
                    module + " accepted by module slot " + slot);
            transaction.commit();
        }
    }

    /**
     * Two speed modules make an assembling machine 2 two fifths faster and twice as hungry.
     *
     * <p>Factorio's arithmetic: the effects add, so two modules at a fifth are plus two fifths on
     * the speed and two halves on the draw. A ten-tick recipe at 0.75 takes thirteen ticks bare
     * and ten with the modules in, and the two machines are run side by side so the difference is
     * what is asserted rather than a number that happens to come out.
     */
    public static class SpeedModulesSpeedAnAssemblerTest extends PackGameTest {

        private static final BlockPos SECOND = MACHINE.offset(4, 0, 0);

        SpeedModulesSpeedAnAssemblerTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            Item product = ModItems.ASSEMBLING_MACHINE_1.get();
            ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(helper.getLevel(), product);
            helper.assertTrue(recipe != null, "no timed recipe makes an assembling machine 1");

            placeMachine(helper, MACHINE, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity bare = helper.getBlockEntity(MACHINE, AssemblerBlockEntity.class);
            placeMachine(helper, SECOND, ModBlocks.ASSEMBLING_MACHINE_2.get());
            AssemblerBlockEntity modded = helper.getBlockEntity(SECOND, AssemblerBlockEntity.class);

            helper.assertValueEqual(modded.modules().size(), AssemblingMachine2Block.MODULE_SLOTS,
                    "module slots on an assembling machine 2");
            module(helper, modded.modules(), 0, ModItems.SPEED_MODULE.get());
            module(helper, modded.modules(), 1, ModItems.SPEED_MODULE.get());
            helper.assertTrue(Math.abs(modded.modules().effect().speed() - 0.4) < 1e-9,
                    "two speed modules' speed adds to two fifths");
            helper.assertValueEqual(modded.currentEnergyPerTick(), 2 * AssemblingMachine2Block.ENERGY_PER_TICK,
                    "the draw under two speed modules - half again each, added");

            for (AssemblerBlockEntity machine : List.of(bare, modded)) {
                machine.setRecipe(recipe);
                charge(machine);
                feedOneCraft(helper, machine.automationView());
            }

            // Ten ticks over 0.75 times 1.4 is 9.5, so ten; bare it is 13.3, so thirteen.
            int moddedTicks = AssemblerBlockEntity.craftTicksFor(
                    helper.getLevel().getServer().getRecipeManager().byKey(recipe)
                            .map(holder -> (com.jaguarm.facrafting.recipe.FacraftRecipe) holder.value()).orElseThrow(),
                    (float) (AssemblingMachine2Block.CRAFTING_SPEED * modded.modules().effect().speedFactor()));
            helper.assertValueEqual(moddedTicks, 10, "ticks a ten-tick recipe takes with two speed modules");

            helper.runAfterDelay(moddedTicks + 1, () -> {
                helper.assertValueEqual(modded.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 1,
                        "what the machine with speed modules had made after " + (moddedTicks + 1) + " ticks");
                helper.assertValueEqual(bare.inventory().getAmountAsInt(AssemblerBlockEntity.OUTPUT_SLOT), 0,
                        "what the bare machine had made after " + (moddedTicks + 1)
                                + " ticks - it should still be working");
                helper.assertTrue(modded.energyStored() < bare.energyStored(),
                        "the machine with speed modules spent no more power than the bare one");
                helper.succeed();
            });
        }

    }

    /** The fluid handler a pipe would find at a test-relative position, from that face, or null. */
    private static @Nullable ResourceHandler<FluidResource> fluidAt(GameTestHelper helper, BlockPos pos,
            Direction side) {
        return helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), side);
    }

    /** Where a cell of a machine anchored at {@link #MACHINE} stands, for a machine facing north. */
    private static BlockPos cell(MachineShape shape, int part) {
        return shape.cellPos(MACHINE, part, Direction.NORTH);
    }

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

}
