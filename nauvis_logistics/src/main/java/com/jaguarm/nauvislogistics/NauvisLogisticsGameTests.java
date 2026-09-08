package com.jaguarm.nauvislogistics;

import java.util.List;

import com.jaguarm.nauvislogistics.registry.ModBlocks;
import com.jaguarm.nauvislogistics.storage.IronChestBlock;
import com.jaguarm.nauvislogistics.storage.MetalChestBlockEntity;
import com.jaguarm.nauvislogistics.storage.SteelChestBlock;
import com.jaguarm.nauvislogistics.transport.InserterBlock;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.BurnerInserterMenu;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlock;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.FastInserterBlock;
import com.jaguarm.nauvislogistics.transport.InserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.LongHandedInserterBlock;
import com.jaguarm.nauvislogistics.transport.BulkInserterBlock;
import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Tests that run inside a real server, headless, reporting pass or fail on exit. */
public final class NauvisLogisticsGameTests {

    private NauvisLogisticsGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisLogistics.MODID, modEventBus);

        // One item goes from the chest behind to the chest in front, and the inserter burns for it.
        tests.add("inserter_moves_items", 200, PADDING, helper -> {
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
        });

        // A fuelled inserter with an empty chest behind it costs nothing at all.
        tests.add("inserter_sleeps", 100, PADDING, helper -> {
            buildLine(helper, true);
            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isScheduled(helper),
                        "an inserter with nothing to move is still scheduled to tick");
                helper.succeed();
            });
        });

        // The test this design exists to pass.
        //
        // A sleeping inserter is woken by a chest it does not own gaining an item, with nothing
        // polling and nothing subscribing - purely because a container that changes calls
        // setChanged, and NeoForge routes that to all six neighbours as
        // onNeighborChange. The wake is asserted in the same tick as the insert, because if it
        // needed a tick of slack it would not be a signal, it would be a poll wearing a disguise.
        tests.add("inserter_wakes_when_source_fills", 200, PADDING, helper -> {
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
        });

        // No coal, no work - and no ticking while it waits for some.
        tests.add("inserter_needs_fuel", 200, PADDING, helper -> {
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
        });

        // A burner inserter with an empty slot, standing beside coal, fuels itself out of the coal.
        //
        // Factorio calls it leeching, and it is the reason the burner tier is playable at all: the
        // first inserter a player ever places is fed by hand once and then feeds itself, rather than
        // dying quietly the moment its lump runs out. Without it the burner inserter is a block you
        // have to keep visiting.
        //
        // The second half of the test is the half that would be missed. It must take one
        // lump, not fill its slot - the wiki triggers leeching only "whenever the internal fuel
        // inventory reaches zero" - because an inserter that hoards is an inserter taking coal out of
        // a line that is feeding a furnace.
        tests.add("burner_inserter_fuels_itself", 200, PADDING, helper -> {
            buildLine(helper, false);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.COAL, 8), 8,
                    "coal put in the source chest");

            helper.runAfterDelay(BurnerInserterBlockEntity.SWING_TICKS + 5, () -> {
                BurnerInserterBlockEntity inserter =
                        helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class);

                helper.assertTrue(inserter.burnTime() > 0,
                        "an inserter nobody fuelled, with coal right behind it, never lit itself");
                helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.COAL), 1,
                        "coal delivered by an inserter that fuelled itself");
                helper.assertValueEqual(
                        inserter.fuel().getAmountAsInt(BurnerInserterBlockEntity.FUEL_SLOT), 0,
                        "coal hoarded in the fuel slot - it should take one lump and burn it, not "
                                + "fill up out of a line that is feeding something else");

                // One lump for itself and one delivered: the source is down exactly two.
                helper.assertValueEqual(countIn(container(helper, SOURCE), Items.COAL), 6,
                        "coal left in the source chest");
                helper.succeed();
            });
        });

        // A container beside the inserter that is neither its source nor its destination does not wake
        // it.
        //
        // onNeighborChange arrives from all six sides, and in a packed base most of those
        // are machines saving themselves for reasons this inserter cannot act on. Waking anyway is
        // correct and wasteful - it costs a tick to discover there is nothing to do - so the block
        // filters on the two positions it can use before looking anything up. This test is what stops
        // that filter from being quietly removed as a simplification.
        tests.add("inserter_ignores_bystanders", 100, PADDING, helper -> {
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
        });

        // Each chest is a container of the size it claims, and automation can reach it.
        //
        // Both tiers in one test, because what they share is a class and what differs is a number
        // on the block - so the thing worth asserting is that the number arrives. A steel chest that
        // reported thirty-six slots would look perfectly correct from the outside and lose eighteen
        // rows' worth of items on the first save.
        tests.add("chests_hold_items", 60, PADDING, helper -> {
            helper.setBlock(SOURCE, ModBlocks.IRON_CHEST.get());
            helper.setBlock(DESTINATION, ModBlocks.STEEL_CHEST.get());
            helper.getBlockEntity(SOURCE, MetalChestBlockEntity.class);
            helper.getBlockEntity(DESTINATION, MetalChestBlockEntity.class);

            // Through the capability, not the Container interface: NeoForge only wraps a
            // hard-coded list of vanilla block entity types, so a modded Container that forgets
            // to register one is invisible to every inserter in the game while looking fine.
            ResourceHandler<ItemResource> iron = container(helper, SOURCE);
            ResourceHandler<ItemResource> steel = container(helper, DESTINATION);
            helper.assertValueEqual(iron.size(), IronChestBlock.ROWS * 9, "slots on an iron chest");
            helper.assertValueEqual(steel.size(), SteelChestBlock.ROWS * 9, "slots on a steel chest");

            helper.assertValueEqual(insert(iron, Items.IRON_INGOT, 100), 100, "ingots accepted");
            helper.assertValueEqual(countIn(iron, Items.IRON_INGOT), 100, "ingots held");
            helper.assertValueEqual(insert(steel, Items.IRON_INGOT, 100), 100, "ingots accepted by steel");
            helper.assertValueEqual(countIn(steel, Items.IRON_INGOT), 100, "ingots held by steel");

            // Two side by side stay two. Being a ChestBlock brings vanilla's pairing with it, and
            // a pair combines the two containers - so a double steel chest would ask for a
            // hundred-and-eight-slot screen that does not exist, and the crash would be on the
            // first player to put two down in a row. MetalChestBlock#chestCanConnectTo is the one
            // line stopping it.
            helper.setBlock(INSERTER, ModBlocks.IRON_CHEST.get());
            for (BlockPos side : List.of(SOURCE, INSERTER)) {
                helper.assertValueEqual(helper.getBlockState(side).getValue(ChestBlock.TYPE),
                        ChestType.SINGLE, "chest type with another chest beside it");
            }
            helper.succeed();
        });

        // Milestone 1, in the only three blocks that are ours: chest, inserter, chest.
        //
        // Both containers here are iron chests rather than vanilla ones, which makes this a
        // different claim from inserter_moves_items. That one proves the inserter can talk to
        // a container somebody else wrote; this proves ours behaves like one - that it publishes its
        // capability, and that changing it wakes the inserter beside it the same way a vanilla chest
        // does.
        tests.add("inserter_fills_iron_chest", 200, PADDING, helper -> {
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
        });

        // The electric inserter does the same job on electricity, and does it faster.
        //
        // The speed is asserted rather than waited out. Twenty-four ticks against the burner's
        // thirty is the whole reason to build one, and a tier that quietly swings at the same rate as
        // the one it replaces would pass any test that only checked the item arrived.
        tests.add("electric_inserter_moves_items", 200, PADDING, helper -> {
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
        });

        // Why this item waited for the grid.
        //
        // An electric inserter with no pole in range must be a paperweight - otherwise it is
        // strictly better than the burner for free, and the tier it is meant to be an upgrade from
        // has no reason to exist.
        //
        // The second half is the wake, checked the same way the assembler's is: it has to be
        // asleep first, or the restart proves nothing.
        tests.add("electric_inserter_needs_power", 200, PADDING, helper -> {
            buildElectricLine(helper, false);
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 1), 1,
                    "iron accepted by the source chest");

            helper.startSequence()
                    .thenExecuteAfter(ElectricInserterBlock.SWING_TICKS * 2, () -> {
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
                    .thenExecuteAfter(ElectricInserterBlock.SWING_TICKS + 2, () ->
                            helper.assertValueEqual(
                                    countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                                    "items moved after the power came back"))
                    .thenSucceed();
        });
        // An electric inserter that swung to a full chest holds its arm there and sleeps: no tick
        // and no energy until either side changes, and then the item goes over at once.
        tests.add("electric_inserter_holds_without_spending", 200, PADDING, helper -> {
            buildElectricLine(helper, true);
            ElectricInserterBlockEntity inserter = helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class);
            insert(container(helper, SOURCE), Items.IRON_INGOT, 1);
            int[] held = new int[1];

            // Mid-swing, the destination fills up behind its back: twenty-seven stacks of stone.
            helper.runAfterDelay(5, () -> helper.assertValueEqual(
                    insert(container(helper, DESTINATION), Items.STONE, 27 * 64), 27 * 64,
                    "stone put in the destination chest"));

            helper.runAfterDelay(ElectricInserterBlock.SWING_TICKS + 10, () -> {
                helper.assertValueEqual(countIn(container(helper, SOURCE), Items.IRON_INGOT), 1,
                        "the ingot should still be in the source chest");
                helper.assertValueEqual(inserter.swing(), ElectricInserterBlock.SWING_TICKS,
                        "the swing should be held at its end");
                helper.assertFalse(isElectricScheduled(helper),
                        "an inserter with nowhere to put its item is still ticking");
                held[0] = inserter.energyStored();
            });
            helper.runAfterDelay(ElectricInserterBlock.SWING_TICKS + 40, () -> {
                helper.assertValueEqual(inserter.energyStored(), held[0], "energy spent holding an item over a full chest");
                helper.assertFalse(isElectricScheduled(helper), "an inserter holding an item woke on its own");
                try (Transaction transaction = Transaction.openRoot()) {
                    container(helper, DESTINATION).extract(ItemResource.of(Items.STONE), 27 * 64, transaction);
                    transaction.commit();
                }
            });
            helper.runAfterDelay(ElectricInserterBlock.SWING_TICKS + 45, () -> {
                helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                        "the ingot did not go over once there was room");
                helper.assertValueEqual(inserter.swing(), 0, "the swing did not finish");
                helper.succeed();
            });
        });

        tests.add("burner_inserter_opens_a_screen", BurnerInserterOpensAScreenTest::new, 60, PADDING);

        // The long arm takes from two blocks behind and gives two blocks in front, over whatever is
        // in between and without touching it.
        //
        // Both gaps are filled, and with different things on purpose. Ahead of it is stone, which
        // is the ordinary case - reaching over a wall or a walkway. Behind it is a chest holding
        // something else, which is the case that would fail quietly: a long-handed inserter that
        // had kept the basic arm's reach would find that chest, move the wrong item, and look like it
        // was working. Asserting the gold is untouched is the difference between testing that it
        // moves things and testing that it moves the right ones.
        //
        // The delivery deadline is the basic arm's swing time, so a long arm that quietly swung at
        // the speed of the tier it upgrades from fails here rather than passing on throughput nobody
        // measured.
        tests.add("long_handed_inserter_reaches_over", 200, PADDING, helper -> {
            helper.setBlock(LONG_BEHIND, Blocks.CHEST);
            helper.setBlock(LONG_AHEAD, Blocks.STONE);
            buildLongLine(helper, true);

            helper.assertValueEqual(insert(container(helper, LONG_SOURCE), Items.IRON_INGOT, 1), 1,
                    "iron accepted by the chest two blocks behind");
            helper.assertValueEqual(insert(container(helper, LONG_BEHIND), Items.GOLD_INGOT, 1), 1,
                    "gold accepted by the chest it is reaching over");

            helper.startSequence()
                    .thenExecuteAfter(ElectricInserterBlock.SWING_TICKS - 2, () -> {
                        helper.assertValueEqual(
                                countIn(container(helper, LONG_DESTINATION), Items.IRON_INGOT), 1,
                                "iron delivered two blocks ahead, over the stone, by the time the "
                                        + "basic arm would still be swinging");
                        helper.assertValueEqual(
                                countIn(container(helper, LONG_SOURCE), Items.IRON_INGOT), 0,
                                "iron left in the chest two blocks behind");
                        helper.assertValueEqual(
                                countIn(container(helper, LONG_BEHIND), Items.GOLD_INGOT), 1,
                                "the long arm robbed the chest it should have reached over - its "
                                        + "reach is one, not two");
                    })
                    .thenSucceed();
        });

        // Nothing within one block of a long-handed inserter is its business, and nothing two
        // blocks away can tell it anything. Both halves of that are asserted here, and the second
        // one is the reason InserterBlockEntity#IDLE_RECHECK_TICKS exists.
        //
        // The short inserter's twin of this test - inserter_ignores_bystanders - proves a
        // filter that rejects a chest it cannot reach and still accepts its source. This one cannot
        // end that way, because the filter's two positions have moved out of earshot:
        // updateNeighbourForOutputSignal walks the six blocks touching whatever changed and
        // stops, so a chest two away reaches nobody here. A filter that had been widened to "any
        // neighbour" instead of moved would therefore wake on the useless six and still never hear
        // the useful two, which is the worst of both and passes any test that only watches items
        // arrive.
        //
        // Left with no power for the whole test, because an unpowered inserter is the one state
        // where a scheduled tick means only one thing. A powered one always has the re-check coming
        // and could not tell the two apart.
        tests.add("long_handed_inserter_ignores_its_neighbours", 100, PADDING, helper -> {
            helper.setBlock(LONG_BEHIND, Blocks.CHEST);
            buildLongLine(helper, false);

            helper.runAfterDelay(20, () -> {
                helper.assertFalse(isLongScheduled(helper),
                        "an inserter with no power never went to sleep");

                insert(container(helper, LONG_BEHIND), Items.IRON_INGOT, 1);
                helper.assertFalse(isLongScheduled(helper),
                        "the chest it reaches over woke it - the filter in "
                                + "InserterBlock.onNeighborChange has been widened rather than moved");

                // Not a bug being pinned, a fact: this is the wake that does not arrive, and the
                // re-check in InserterBlockEntity is the whole answer to it.
                insert(container(helper, LONG_SOURCE), Items.IRON_INGOT, 1);
                helper.assertFalse(isLongScheduled(helper),
                        "a block two away notified this one, which Minecraft does not do - if that "
                                + "has changed, the re-check can go");

                // The wake that does arrive, so the test cannot pass by the inserter being broken.
                charge(helper.getBlockEntity(LONG_INSERTER, ElectricInserterBlockEntity.class));
                helper.assertTrue(isLongScheduled(helper),
                        "electricity arriving did not wake it either");
                helper.succeed();
            });
        });

        // An item that appears two blocks behind a long-handed inserter that has gone quiet is picked
        // up anyway, because it looks again.
        //
        // This is the one that earns its keep, and it is the same shape of test as
        // belt_wakes_an_inserter_when_an_item_arrives: the work arrives from out of
        // earshot rather than being handed over while the machine is already awake. Every other
        // inserter test here fills a chest the inserter is touching, which is a wake-up; this one
        // fills a chest that says nothing to anybody, and the only thing that can find it is
        // InserterBlockEntity#IDLE_RECHECK_TICKS.
        //
        // Delete the re-check and this is the only test that goes red - and a long-handed inserter
        // would then work perfectly right up until the first gap in its supply and never move again.
        tests.add("long_handed_inserter_looks_again", 200, PADDING, helper -> {
            buildLongLine(helper, true);

            helper.startSequence()
                    // Long enough to have woken, found an empty chest and settled into looking.
                    .thenExecuteAfter(40, () -> {
                        helper.assertTrue(isLongScheduled(helper),
                                "a powered long-handed inserter stopped looking, so nothing will "
                                        + "ever tell it about an item two blocks behind");
                        helper.assertValueEqual(insert(
                                container(helper, LONG_SOURCE), Items.IRON_INGOT, 1), 1,
                                "iron accepted by the chest two blocks behind");
                    })
                    .thenExecuteAfter(
                            InserterBlockEntity.IDLE_RECHECK_TICKS + LongHandedInserterBlock.SWING_TICKS + 8,
                            () -> helper.assertValueEqual(
                                    countIn(container(helper, LONG_DESTINATION), Items.IRON_INGOT), 1,
                                    "an item that turned up two blocks behind was never noticed"))
                    .thenSucceed();
        });

        // A fast inserter delivers in nine ticks, where the basic arm would still be swinging.
        //
        // Factorio's fast inserter turns at 864 degrees a second against the basic arm's 302, and
        // the tick counts follow: a fast swing is over well before a basic one is half done. Both
        // halves are asserted - not yet at six ticks, done by twelve - because a tier that swung at
        // the basic speed would pass a test that only waited long enough.
        tests.add("fast_inserter_swings_faster", 100, PADDING, helper -> {
            buildTierLine(helper, ModBlocks.FAST_INSERTER.get());
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 1), 1,
                    "iron accepted by the source chest");

            helper.startSequence()
                    .thenExecuteAfter(FastInserterBlock.SWING_TICKS - 3, () -> helper.assertValueEqual(
                            countIn(container(helper, DESTINATION), Items.IRON_INGOT), 0,
                            "iron delivered before a fast swing could have finished"))
                    .thenExecuteAfter(6, () -> {
                        helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT), 1,
                                "iron delivered by a fast inserter twelve ticks after it was given - "
                                        + "a basic arm would still be swinging");
                        helper.assertTrue(
                                helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class).energyStored()
                                        <= ElectricInserterBlockEntity.ENERGY_CAPACITY
                                                - FastInserterBlock.ENERGY_PER_TICK * FastInserterBlock.SWING_TICKS,
                                "a fast inserter swung for less than its own draw");
                    })
                    .thenSucceed();
        });
        tests.add("bulk_inserter_moves_a_handful", BulkInserterMovesAHandfulTest::new, 100, PADDING);
    }

    /** A chest, an inserter pointing east, and a chest. The whole of milestone 1 in three blocks. */
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos INSERTER = new BlockPos(1, 1, 0);
    private static final BlockPos DESTINATION = new BlockPos(2, 1, 0);

    /** Beside the inserter but not in its way: a container it must learn to ignore. */
    private static final BlockPos BYSTANDER = new BlockPos(1, 1, 1);

    /**
     * The long arm's line: chest, gap, inserter, gap, chest. Five blocks for a reach of two, and
     * the two gaps are the point - a long-handed inserter is bought to reach <em>over</em>
     * something, so the tests put something there.
     */
    private static final BlockPos LONG_SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos LONG_BEHIND = new BlockPos(1, 1, 0);
    private static final BlockPos LONG_INSERTER = new BlockPos(2, 1, 0);
    private static final BlockPos LONG_AHEAD = new BlockPos(3, 1, 0);
    private static final BlockPos LONG_DESTINATION = new BlockPos(4, 1, 0);

    /**
     * Room around each test.
     *
     * <p>Zero was survivable while every test here was three blocks long. The long arm's line is
     * five, and a test whose blocks land in the next test along fails whichever of the two ran
     * second - see docs/PITFALLS.md.
     */
    private static final int PADDING = 8;

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

    /**
     * The long arm's line, with nothing yet in either gap.
     *
     * @param charged false to leave it with an empty buffer and no pole anywhere near it.
     */
    private static void buildLongLine(GameTestHelper helper, boolean charged) {
        helper.setBlock(LONG_SOURCE, Blocks.CHEST);
        helper.setBlock(LONG_DESTINATION, Blocks.CHEST);
        helper.setBlock(LONG_INSERTER, ModBlocks.LONG_HANDED_INSERTER.get().defaultBlockState()
                .setValue(InserterBlock.FACING, Direction.EAST));

        if (charged) {
            charge(helper.getBlockEntity(LONG_INSERTER, ElectricInserterBlockEntity.class));
        }
    }

    private static boolean isLongScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(LONG_INSERTER), ModBlocks.LONG_HANDED_INSERTER.get());
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

    /**
     * The burner inserter opens, its fuel slot is in there, and it takes fuel and nothing else.
     *
     * <p>All three used to be right-click handling: coal in hand to fuel it, empty hand for a line
     * of text, and a burn-time check buried in the interaction code. The screen replaces the first
     * two and {@code InserterFuel#isValid} replaces the third - which is why the filter is
     * asserted through the capability rather than through the menu, since a hopper pointed at the
     * inserter has to be told the same thing.
     */
    public static class BurnerInserterOpensAScreenTest extends PackGameTest {

        private static final int PLAYER_SLOTS = 36;

        BurnerInserterOpensAScreenTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            buildLine(helper, true);
            BurnerInserterBlockEntity inserter =
                    helper.getBlockEntity(INSERTER, BurnerInserterBlockEntity.class);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            AbstractContainerMenu menu = inserter.createMenu(1, player.getInventory(), player);
            helper.assertTrue(menu instanceof BurnerInserterMenu,
                    "the burner inserter opened something else");
            helper.assertValueEqual(menu.slots.size(),
                    BurnerInserterBlockEntity.SLOT_COUNT + PLAYER_SLOTS,
                    "slots in the burner inserter's menu");
            helper.assertValueEqual(
                    menu.getSlot(BurnerInserterBlockEntity.FUEL_SLOT).getItem().getItem(), Items.COAL,
                    "what the first slot of the inserter's menu is showing");

            helper.assertValueEqual(insert(inserter.fuelAccess(), Items.DIAMOND, 1), 0,
                    "diamonds accepted by a fuel slot");
            helper.succeed();
        }

    }

    /** Chest, an electric tier of choice, chest - charged, so what differs is the arm alone. */
    private static void buildTierLine(GameTestHelper helper, ElectricInserterBlock tier) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(DESTINATION, Blocks.CHEST);
        helper.setBlock(INSERTER, tier.defaultBlockState().setValue(InserterBlock.FACING, Direction.EAST));
        charge(helper.getBlockEntity(INSERTER, ElectricInserterBlockEntity.class));
    }

    /**
     * A bulk inserter moves as many items in one swing as the world's research says its hand
     * holds.
     */
    public static class BulkInserterMovesAHandfulTest extends PackGameTest {

        private static final int BONUS = 4;

        BulkInserterMovesAHandfulTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            Bonuses.Source world = Bonuses.source();
            Bonuses.install((level, effect) -> BulkInserterBlock.CAPACITY_BONUS.equals(effect) ? BONUS : 0);

            helper.assertValueEqual(
                    ModBlocks.BULK_INSERTER.get().handSize(helper.getLevel()), 1 + BONUS,
                    "a bulk inserter's hand with four capacity bonuses researched");
            helper.assertValueEqual(ModBlocks.INSERTER.get().handSize(helper.getLevel()), 1,
                    "an ordinary inserter's hand, which the bulk inserter's bonus does not grow");

            buildTierLine(helper, ModBlocks.BULK_INSERTER.get());
            helper.assertValueEqual(insert(container(helper, SOURCE), Items.IRON_INGOT, 12), 12,
                    "iron accepted by the source chest");

            helper.startSequence()
                    .thenExecuteAfter(BulkInserterBlock.SWING_TICKS + 3, () -> {
                        try {
                            helper.assertValueEqual(countIn(container(helper, DESTINATION), Items.IRON_INGOT),
                                    1 + BONUS, "iron delivered by one swing of a bulk inserter");
                            helper.assertValueEqual(countIn(container(helper, SOURCE), Items.IRON_INGOT),
                                    12 - 1 - BONUS, "iron left behind after one swing");
                        } finally {
                            Bonuses.install(world);
                        }
                    })
                    .thenSucceed();
        }

    }
}
