package com.jaguarm.nauvismachines;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.jaguarm.facrafting.machine.MachineCategories;
import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.nauvismachines.machine.furnace.ElectricFurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceShape;
import com.jaguarm.nauvismachines.machine.furnace.SteelFurnaceBlock;
import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismachines.registry.ModBlocks;
import com.jaguarm.nauvismachines.registry.ModItems;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The furnaces, headless. The assembler's tests are in {@link NauvisMachinesGameTests} and this
 * follows their shape; the two are separate files because each is long enough on its own.
 *
 * <p>Every smelt here is iron: raw iron to an ingot, Factorio's 3.2 seconds, from the generated
 * recipe in Nauvis Materials. The numbers are asserted on the tick they should land on,
 * so a craft time or a crafting speed that drifted fails here rather than being felt in a world.
 */
public final class NauvisMachinesFurnaceGameTests {

    private NauvisMachinesFurnaceGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisMachines.MODID, modEventBus);

        tests.batch("furnace");

        // Raw iron and a coal go in; sixty-four ticks later an iron ingot is there and the ore is not.
        //
        // Sixty-four is Factorio's 3.2 seconds at crafting speed 1, from the generated recipe. The
        // coal is checked too: one was taken from the slot, and what is left of it is exactly a coal
        // less the ticks worked, because a furnace here burns only while it smelts.
        tests.add("stone_furnace_smelts", 120, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            feed(helper, furnace, 2, 1);

            helper.startSequence()
                    .thenExecuteAfter(SMELT_TICKS - 1, () -> helper.assertValueEqual(output(furnace), 0,
                            "ingots a tick before the smelt should finish"))
                    .thenExecuteAfter(1, () -> {
                        helper.assertValueEqual(furnace.inventory().getResource(FurnaceBlockEntity.OUTPUT_SLOT).getItem(),
                                Items.IRON_INGOT, "the item in the output slot");
                        helper.assertValueEqual(output(furnace), 1, "ingots after " + SMELT_TICKS + " ticks");
                        helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.INPUT_SLOT), 0, "raw iron left");
                        helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.FUEL_SLOT), 1, "coal left in the slot");
                        helper.assertValueEqual(furnace.burnTime(), COAL_TICKS - SMELT_TICKS,
                                "what is left of the coal it lit");
                    })
                    .thenSucceed();
        });
        tests.add("steel_furnace_is_twice_as_fast", SteelFurnaceIsTwiceAsFastTest::new, 120, PADDING);

        // An electric furnace with ore and no charge makes nothing and sleeps; charge it and it wakes,
        // and thirty-two ticks later there is an ingot. The two halves of sleeping, in one test.
        tests.add("electric_furnace_runs_on_power", 200, PADDING, helper -> {
            FurnaceBlockEntity furnace = place(helper, FURNACE, ModBlocks.ELECTRIC_FURNACE.get());
            helper.assertFalse(furnace.isBurner(), "an electric furnace thinks it burns fuel");
            helper.assertValueEqual(insert(furnace.automationView(), Items.COAL, 1), 0,
                    "coal accepted by an electric furnace");
            feed(helper, furnace, 0, 1);

            helper.startSequence()
                    .thenExecuteAfter(STEEL_SMELT_TICKS * 2, () -> {
                        helper.assertValueEqual(output(furnace), 0, "ingots made with no electricity");
                        helper.assertValueEqual(furnace.status(), FurnaceBlockEntity.Status.NO_POWER, "status");
                        helper.assertFalse(isScheduled(helper, FURNACE),
                                "an electric furnace with no electricity is still scheduled to tick");
                    })
                    .thenExecute(() -> {
                        charge(furnace);
                        helper.assertTrue(isScheduled(helper, FURNACE),
                                "electricity arrived and the furnace was not woken");
                    })
                    .thenExecuteAfter(STEEL_SMELT_TICKS + 2, () -> {
                        helper.assertValueEqual(output(furnace), 1, "ingots made after the power came");
                        helper.assertTrue(furnace.energyStored() < furnace.energyCapacity(),
                                "the buffer was not spent on the smelt");
                        helper.assertValueEqual(furnace.energyPerTick(), ElectricFurnaceBlock.ENERGY_PER_TICK,
                                "what a tick of smelting costs");
                    })
                    .thenSucceed();
        });

        // The electric furnace has two module slots, the burner furnaces none, and two speed modules
        // smelt a plate in twenty-three ticks instead of thirty-two.
        //
        // Sixty-four ticks over a crafting speed of two times 1.4 is 22.9, so twenty-three, and the
        // draw is twice the tier's - half again for each module, added. The stone furnace is asserted
        // too: a slot it does not have is a slot a module cannot be put in.
        tests.add("electric_furnace_takes_modules", 100, PADDING, helper -> {
            FurnaceBlockEntity furnace = place(helper, FURNACE, ModBlocks.ELECTRIC_FURNACE.get());
            helper.assertValueEqual(furnace.modules().size(), ElectricFurnaceBlock.MODULE_SLOTS,
                    "module slots on an electric furnace");
            for (int slot = 0; slot < ElectricFurnaceBlock.MODULE_SLOTS; slot++) {
                try (Transaction transaction = Transaction.openRoot()) {
                    helper.assertValueEqual(
                            furnace.modules().insert(slot, ItemResource.of(ModItems.SPEED_MODULE.get()), 1, transaction),
                            1, "a speed module taken by slot " + slot);
                    transaction.commit();
                }
            }
            helper.assertValueEqual(furnace.currentEnergyPerTick(), 2 * ElectricFurnaceBlock.ENERGY_PER_TICK,
                    "the draw under two speed modules");
            charge(furnace);
            feed(helper, furnace, 0, 1);

            // Sixty-four over two times 1.4 is twenty-three; bare it would be thirty-two.
            helper.startSequence()
                    .thenExecuteAfter(20, () -> helper.assertValueEqual(output(furnace), 0,
                            "ingots before even a modded smelt could have finished"))
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(output(furnace), 1,
                                "ingots twenty-five ticks in - a bare electric furnace would still be smelting");
                        helper.assertValueEqual(place(helper, FURNACE.offset(4, 0, 0), ModBlocks.STONE_FURNACE.get())
                                .modules().size(), 0, "module slots on a stone furnace");
                    })
                    .thenSucceed();
        });

        // A furnace with ore and no coal sleeps saying so, and the coal arriving is what wakes it.
        //
        // Deleting the inventory's wake-up leaves every other test here passing and fails this
        // one: a furnace that stopped for want of fuel is scheduled for nothing, so the wake has to
        // come from the slot.
        tests.add("furnace_wakes_when_fuel_arrives", 200, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            feed(helper, furnace, 0, 1);

            helper.startSequence()
                    .thenExecuteAfter(10, () -> {
                        helper.assertValueEqual(furnace.status(), FurnaceBlockEntity.Status.NO_FUEL, "status");
                        helper.assertFalse(isScheduled(helper, FURNACE),
                                "a furnace with nothing to burn is still scheduled to tick");
                        helper.assertFalse(helper.getBlockState(FURNACE).getValue(FurnaceBlock.LIT),
                                "a furnace with nothing to burn is lit");
                    })
                    .thenExecute(() -> {
                        feed(helper, furnace, 1, 0);
                        helper.assertTrue(isScheduled(helper, FURNACE), "coal arrived and the furnace was not woken");
                    })
                    .thenExecuteAfter(SMELT_TICKS + 2, () -> helper.assertValueEqual(output(furnace), 1,
                            "ingots made after the coal came"))
                    .thenSucceed();
        });

        // Factorio's rule, not vanilla's: a burner spends fuel only while it works. A furnace with
        // coal and nothing to smelt keeps the coal where it is, and sleeps rather than burning it.
        tests.add("furnace_keeps_its_coal_when_idle", 60, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            feed(helper, furnace, 3, 0);

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.FUEL_SLOT), 3, "coal in the slot");
                helper.assertValueEqual(furnace.burnTime(), 0, "coal lit for nothing");
                helper.assertValueEqual(furnace.status(), FurnaceBlockEntity.Status.IDLE, "status");
                helper.assertFalse(isScheduled(helper, FURNACE), "a furnace with nothing to smelt is still ticking");
                helper.succeed();
            });
        });

        // The slots are fussy, which is what lets a furnace have no recipe selector: ore goes in the
        // input, coal in the fuel slot, and anything a furnace can smelt nothing with is refused at
        // the capability - so an inserter holding a brick simply waits. Nothing comes back out of
        // either, and the output is the only slot anything comes out of.
        tests.add("furnace_takes_only_what_it_can_use", 20, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            ResourceHandler<ItemResource> view = furnace.automationView();

            // A brick and a diamond: neither smelts into anything here and neither burns. Not a
            // stick, which is fuel in vanilla and which the fuel slot rightly takes.
            helper.assertValueEqual(insert(view, Items.BRICK, 1), 0, "bricks accepted");
            helper.assertValueEqual(insert(view, Items.DIAMOND, 1), 0, "diamonds accepted");
            helper.assertValueEqual(insert(view, Items.RAW_IRON, 5), 5, "raw iron accepted");
            helper.assertValueEqual(insert(view, Items.COAL, 2), 2, "coal accepted");
            helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.INPUT_SLOT), 5, "raw iron in the input slot");
            helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.FUEL_SLOT), 2, "coal in the fuel slot");

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(view.extract(ItemResource.of(Items.RAW_IRON), 5, transaction), 0,
                        "ore taken back out through the capability");
                helper.assertValueEqual(view.extract(ItemResource.of(Items.COAL), 2, transaction), 0,
                        "coal taken back out through the capability");
            }

            furnace.inventory().set(FurnaceBlockEntity.OUTPUT_SLOT, ItemResource.of(Items.IRON_INGOT), 3);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(view.extract(ItemResource.of(Items.IRON_INGOT), 3, transaction), 3,
                        "ingots taken out of the output slot");
            }
            helper.succeed();
        });

        // A full output slot holds the ore rather than voiding it - and holds the coal too. The
        // smelt is refused before any fuel is lit, so a furnace nobody empties burns nothing.
        tests.add("furnace_stalls_when_full", 240, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            int full = Items.IRON_INGOT.getDefaultMaxStackSize();
            furnace.inventory().set(FurnaceBlockEntity.OUTPUT_SLOT, ItemResource.of(Items.IRON_INGOT), full);
            feed(helper, furnace, 1, 1);

            helper.runAfterDelay(SMELT_TICKS * 3, () -> {
                helper.assertValueEqual(output(furnace), full, "ingots in the output slot");
                helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.INPUT_SLOT), 1, "raw iron still waiting");
                helper.assertValueEqual(slot(furnace, FurnaceBlockEntity.FUEL_SLOT), 1, "coal still in the slot");
                helper.assertValueEqual(furnace.status(), FurnaceBlockEntity.Status.OUTPUT_FULL, "status");
                helper.assertFalse(isScheduled(helper, FURNACE),
                        "a furnace that cannot put its plate anywhere is still scheduled to tick");
                helper.succeed();
            });
        });
        tests.add("furnace_lights_up", FurnaceLightsUpTest::new, 120, PADDING);
        tests.add("furnace_reports_what_it_made", FurnaceReportsWhatItMadeTest::new, 120, PADDING);

        // Two by two and a stack: five blocks, the right five, and only one of them holds anything.
        tests.add("furnace_is_five_blocks", 20, PADDING, helper -> {
            FurnaceBlock block = ModBlocks.STONE_FURNACE.get();
            MachineShape shape = FurnaceShape.SHAPE;
            stoneFurnace(helper);

            helper.assertValueEqual(shape.cellCount(), 5, "blocks in a furnace");
            for (int part = 0; part < shape.cellCount(); part++) {
                BlockPos pos = FURNACE.offset(shape.offset(part, Direction.NORTH));
                helper.assertBlockPresent(block, pos);
                helper.assertValueEqual(helper.getBlockState(pos).getValue(shape.part()), part,
                        "which cell the block at " + pos + " says it is");
                helper.assertValueEqual(
                        Multiblock.anchorPos(block, helper.getBlockState(pos), helper.absolutePos(pos)),
                        helper.absolutePos(FURNACE), "anchor as seen from " + pos);
                boolean hasBlockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos)) != null;
                helper.assertValueEqual(hasBlockEntity, part == shape.anchor(),
                        "block entity at " + pos + ", where only the hearth should have one");
            }
            // The stack stands on the hearth, one up.
            helper.assertBlockPresent(block, FURNACE.above());
            helper.succeed();
        });

        // Break the far corner and the whole furnace comes down, giving back exactly one furnace.
        tests.add("furnace_breaks_as_one", 40, PADDING, helper -> {
            stoneFurnace(helper);
            helper.getLevel().destroyBlock(helper.absolutePos(FURNACE.offset(1, 0, 1)), true);

            helper.runAfterDelay(2, () -> {
                for (int x = 0; x <= 1; x++) {
                    for (int z = 0; z <= 1; z++) {
                        helper.assertBlockPresent(Blocks.AIR, FURNACE.offset(x, 0, z));
                    }
                }
                helper.assertBlockPresent(Blocks.AIR, FURNACE.above());
                helper.assertItemEntityCountIs(ModItems.STONE_FURNACE.get(), FURNACE, 4.0, 1);
                helper.succeed();
            });
        });

        // Breaking a furnace gives its ore and coal back rather than eating them.
        tests.add("furnace_spills_when_broken", 60, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            helper.assertValueEqual(insert(furnace.automationView(), Items.RAW_IRON, 7), 7, "raw iron accepted");
            helper.assertValueEqual(insert(furnace.automationView(), Items.COAL, 4), 4, "coal accepted");

            helper.getLevel().destroyBlock(helper.absolutePos(FURNACE), true);

            helper.runAfterDelay(2, () -> {
                helper.assertItemEntityPresent(ModItems.STONE_FURNACE.get(), FURNACE, 2.0);
                helper.assertItemEntityCountIs(Items.RAW_IRON, FURNACE, 2.0, 7);
                helper.assertItemEntityCountIs(Items.COAL, FURNACE, 2.0, 4);
                helper.succeed();
            });
        });
        tests.add("only_the_electric_furnace_takes_power", OnlyTheElectricFurnaceTakesPowerTest::new, 20, PADDING);

        // Sand goes in and glass comes out, at vanilla's two hundred ticks: a furnace runs vanilla's
        // furnace recipes for whatever Factorio has no recipe for, so the pack's only furnace is not
        // one that cannot make a window. Factorio's recipes are asked first - iron is tested above at
        // Factorio's sixty-four, not vanilla's two hundred.
        tests.add("furnace_smelts_vanilla_recipes", 260, PADDING, helper -> {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            helper.assertValueEqual(insert(furnace.automationView(), Items.COAL, 1), 1, "coal accepted");
            helper.assertValueEqual(insert(furnace.automationView(), Items.SAND, 1), 1, "sand accepted");

            helper.startSequence()
                    .thenExecuteAfter(VANILLA_SMELT_TICKS - 1, () -> helper.assertValueEqual(output(furnace), 0,
                            "glass a tick before vanilla's cooking time is up"))
                    .thenExecuteAfter(1, () -> {
                        helper.assertValueEqual(furnace.inventory().getResource(FurnaceBlockEntity.OUTPUT_SLOT).getItem(),
                                Items.GLASS, "the item in the output slot");
                        helper.assertValueEqual(output(furnace), 1, "glass after " + VANILLA_SMELT_TICKS + " ticks");
                    })
                    .thenSucceed();
        });

        // The three furnaces have told Facrafting that they run smelting, which is what the crafting
        // panel prints under a smelting recipe as "Made in:". A furnace that forgot to say so would
        // leave the panel telling a player that iron plates cannot be crafted by hand and nothing else.
        tests.add("furnaces_say_where_smelting_happens", 20, PADDING, helper -> {
            List<Component> machines = MachineCategories.machinesFor(FurnaceBlockEntity.SMELTING);
            helper.assertValueEqual(machines.size(), 3, "machines registered as running smelting");
            helper.assertValueEqual(machines.get(0).getString(),
                    ModBlocks.STONE_FURNACE.get().getName().getString(), "the first of them");
            helper.succeed();
        });
    }

    /** Where every test puts its furnace: one block up, so it is not inside the floor. */
    private static final BlockPos FURNACE = new BlockPos(0, 1, 0);

    /**
     * Factorio's 3.2 seconds for an iron plate, at the stone furnace's crafting speed of 1.
     * Sixty-four, from the generated recipe; a steel furnace at speed 2 takes half.
     */
    private static final int SMELT_TICKS = 64;
    private static final int STEEL_SMELT_TICKS = 32;

    /** What a coal is worth to a furnace, in ticks: vanilla's number, spent only while working. */
    private static final int COAL_TICKS = 1600;

    /** Vanilla's cooking time, for a recipe Factorio has no opinion about. */
    private static final int VANILLA_SMELT_TICKS = 200;

    private static final int PADDING = 24;

    /** Puts a whole furnace in, anchored here. See the assembler's placeMachine for why not setBlock. */
    private static FurnaceBlockEntity place(GameTestHelper helper, BlockPos anchor, FurnaceBlock block) {
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor), block.defaultBlockState());
        return helper.getBlockEntity(anchor, FurnaceBlockEntity.class);
    }

    private static FurnaceBlockEntity stoneFurnace(GameTestHelper helper) {
        return place(helper, FURNACE, ModBlocks.STONE_FURNACE.get());
    }

    /** Puts items in the way an inserter will: through the published capability view. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** Coal and raw iron, the way a burner inserter would hand them over. */
    private static void feed(GameTestHelper helper, FurnaceBlockEntity furnace, int coal, int ore) {
        if (coal > 0) {
            helper.assertValueEqual(insert(furnace.automationView(), Items.COAL, coal), coal, "coal accepted");
        }
        if (ore > 0) {
            helper.assertValueEqual(insert(furnace.automationView(), Items.RAW_IRON, ore), ore, "raw iron accepted");
        }
    }

    private static int output(FurnaceBlockEntity furnace) {
        return furnace.inventory().getAmountAsInt(FurnaceBlockEntity.OUTPUT_SLOT);
    }

    private static int slot(FurnaceBlockEntity furnace, int index) {
        return furnace.inventory().getAmountAsInt(index);
    }

    /** Whether the furnace has a block tick coming, which is what "awake" means here. */
    private static boolean isScheduled(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(pos), helper.getBlockState(pos).getBlock());
    }

    private static void charge(FurnaceBlockEntity furnace) {
        try (Transaction transaction = Transaction.openRoot()) {
            furnace.gridView().insert(furnace.energyCapacity(), transaction);
            transaction.commit();
        }
    }

    /**
     * Factorio's steel furnace: crafting speed 2 against the stone furnace's 1, on the same fuel.
     * Thirty-two ticks against sixty-four, checked on the tick each lands on.
     */
    public static class SteelFurnaceIsTwiceAsFastTest extends PackGameTest {

        private static final BlockPos SECOND = FURNACE.offset(4, 0, 0);

        SteelFurnaceIsTwiceAsFastTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            FurnaceBlockEntity stone = stoneFurnace(helper);
            FurnaceBlockEntity steel = place(helper, SECOND, ModBlocks.STEEL_FURNACE.get());
            feed(helper, stone, 1, 1);
            feed(helper, steel, 1, 1);

            helper.assertValueEqual(steel.craftingSpeed(), SteelFurnaceBlock.CRAFTING_SPEED, "the steel furnace's speed");

            helper.startSequence()
                    .thenExecuteAfter(STEEL_SMELT_TICKS, () -> {
                        helper.assertValueEqual(output(steel), 1, "the steel furnace's ingots after " + STEEL_SMELT_TICKS);
                        helper.assertValueEqual(output(stone), 0, "the stone furnace's ingots after " + STEEL_SMELT_TICKS
                                + " - it is half the speed and should still be working");
                    })
                    .thenExecuteAfter(SMELT_TICKS - STEEL_SMELT_TICKS, () -> helper.assertValueEqual(
                            output(stone), 1, "the stone furnace's ingots after " + SMELT_TICKS))
                    .thenSucceed();
        }

    }

    /**
     * Every cell is lit while a smelt is under way and dark once there is nothing left to do.
     *
     * <p>All five, not the anchor: the property is what the models are dispatched over, and a
     * furnace column that lights up is how a player sees which furnaces are working from across
     * a base.
     */
    public static class FurnaceLightsUpTest extends PackGameTest {

        FurnaceLightsUpTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            FurnaceBlockEntity furnace = stoneFurnace(helper);
            feed(helper, furnace, 1, 1);

            helper.startSequence()
                    .thenExecuteAfter(10, () -> assertLit(helper, true))
                    .thenExecuteAfter(SMELT_TICKS + 5, () -> {
                        helper.assertValueEqual(output(furnace), 1, "ingots made");
                        assertLit(helper, false);
                    })
                    .thenSucceed();
        }

        private static void assertLit(GameTestHelper helper, boolean lit) {
            MachineShape shape = FurnaceShape.SHAPE;
            for (int part = 0; part < shape.cellCount(); part++) {
                BlockPos pos = FURNACE.offset(shape.offset(part, Direction.NORTH));
                helper.assertValueEqual(helper.getBlockState(pos).getValue(FurnaceBlock.LIT), lit,
                        "lit, on the cell at " + pos);
            }
        }

    }

    /**
     * Every plate a furnace finishes is reported through Facrafting's machine listener, which is
     * how "craft fifty iron plates" can ever be heard: plates are never crafted by hand.
     *
     * <p>The listener is global and other tests in the same batch smelt too, so what is asserted
     * is that the count of iron ingots reported rose while this furnace made one - which is the
     * property, not which furnace it was.
     */
    public static class FurnaceReportsWhatItMadeTest extends PackGameTest {

        /** Iron ingots any machine has reported making, for the life of the run. */
        private static final AtomicInteger REPORTED = new AtomicInteger();
        private static boolean listening;

        FurnaceReportsWhatItMadeTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            synchronized (FurnaceReportsWhatItMadeTest.class) {
                if (!listening) {
                    CraftListeners.addMachine((level, result) -> {
                        if (result.is(Items.IRON_INGOT)) {
                            REPORTED.addAndGet(result.getCount());
                        }
                    });
                    listening = true;
                }
            }
            int before = REPORTED.get();

            FurnaceBlockEntity furnace = stoneFurnace(helper);
            feed(helper, furnace, 1, 1);

            helper.runAfterDelay(SMELT_TICKS + 2, () -> {
                helper.assertValueEqual(output(furnace), 1, "ingots made");
                helper.assertTrue(REPORTED.get() > before,
                        "the furnace made an ingot and nobody listening to Facrafting heard about it");
                helper.succeed();
            });
        }

    }

    /**
     * A burner furnace offers no energy capability, so a pole beside a stone furnace never
     * counts it as a machine to supply; the electric one offers it from every cell.
     */
    public static class OnlyTheElectricFurnaceTakesPowerTest extends PackGameTest {

        private static final BlockPos ELECTRIC = FURNACE.offset(4, 0, 0);

        OnlyTheElectricFurnaceTakesPowerTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            stoneFurnace(helper);
            place(helper, ELECTRIC, ModBlocks.ELECTRIC_FURNACE.get());

            helper.assertTrue(energyAt(helper, FURNACE.offset(1, 0, 1)) == null,
                    "a stone furnace offers an energy capability");
            helper.assertTrue(energyAt(helper, ELECTRIC.offset(-1, 0, -1)) != null,
                    "an electric furnace's far corner offers no energy capability");
            helper.assertTrue(energyAt(helper, ELECTRIC.above()) != null,
                    "an electric furnace's hood offers no energy capability");
            helper.succeed();
        }

        private static Object energyAt(GameTestHelper helper, BlockPos pos) {
            return Capabilities.Energy.BLOCK.getCapability(helper.getLevel(), helper.absolutePos(pos),
                    null, null, Direction.UP);
        }

    }

}
