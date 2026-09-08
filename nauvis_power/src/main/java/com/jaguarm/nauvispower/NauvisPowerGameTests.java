package com.jaguarm.nauvispower;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.SolarPanelBlockEntity;
import com.jaguarm.nauvispower.generator.BoilerMenu;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvispower.grid.PowerNetwork;
import com.jaguarm.nauvispower.grid.PowerNetworkManager;
import com.jaguarm.nauvispower.grid.BigPoleShape;
import com.jaguarm.nauvispower.grid.ElectricPoleBlock;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvispower.grid.ElectricPoleBlockEntity;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.jaguarm.nauvispower.registry.ModItems;
import com.jaguarm.nauvispower.storage.AccumulatorBlockEntity;
import com.jaguarm.nauvispower.storage.AccumulatorShape;
import com.jaguarm.nauvislib.transfer.EnergyBuffer;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless.
 *
 * <p>The one that matters most is {@code power_chain_sleeps}. Making energy is easy to check and
 * hard to get wrong; <em>stopping</em> is neither. Non-negotiable #5 across a chain means an engine
 * with a full buffer stops drawing steam, which lets the boiler's buffer fill, which stops it
 * burning coal - three blocks that each have to decide to do nothing, in order, from the far end.
 */
public final class NauvisPowerGameTests {

    private NauvisPowerGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisPower.MODID, modEventBus);
        tests.add("boiler_turns_as_one", BoilerTurnsAsOneTest::new, 40, PADDING);

        // Break one block of an engine and all seventeen go, giving back exactly one engine.
        //
        // A flank is broken rather than the middle: it is two blocks from the block entity, it has
        // no loot of its own, and everything that happens after it is the teardown rule crossing the
        // footprint. Seventeen is also the first machine big enough for that cascade to be worth
        // doubting - the two ways it fails are blocks left standing that nothing can break, and
        // seventeen engines dropped where one was placed.
        tests.add("engine_breaks_as_one", 40, PADDING, helper -> {
            placeEngine(helper, ENGINE);

            int standing = 0;
            for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                helper.assertBlockPresent(ModBlocks.STEAM_ENGINE.get(), cell);
                standing++;
            }
            helper.assertValueEqual(standing, 17, "blocks in a steam engine");

            // A corner of the west flank, as far from the block entity as anything gets.
            helper.getLevel().destroyBlock(helper.absolutePos(ENGINE.offset(-1, 0, -2)), true);

            helper.runAfterDelay(2, () -> {
                for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                    helper.assertBlockPresent(Blocks.AIR,
                            SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH));
                }
                helper.assertItemEntityCountIs(
                        ModItems.STEAM_ENGINE.get(), ENGINE, 6.0, 1);
                helper.succeed();
            });
        });
        tests.add("power_machines_tile_walkably", PowerMachinesTileWalkablyTest::new, 40, PADDING);

        // Coal in, steam out.
        tests.add("boiler_makes_steam", 100, PADDING, helper -> {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            helper.assertValueEqual(boiler.steam(), 0, "steam in a cold boiler");
            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.COAL, 1), 1, "coal accepted");
            helper.assertValueEqual(water(boiler, BoilerBlockEntity.WATER_CAPACITY),
                    BoilerBlockEntity.WATER_CAPACITY, "water accepted");

            helper.runAfterDelay(20, () -> {
                BoilerBlockEntity fired = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
                helper.assertTrue(fired.steam() > 0, "a boiler with coal and water in it made no steam");
                helper.assertTrue(fired.burnTime() > 0, "it made steam without burning anything");
                // Factorio's boiler: one water in for one steam out.
                helper.assertValueEqual(fired.water(), BoilerBlockEntity.WATER_CAPACITY - fired.steam(),
                        "water left against steam made");
                helper.succeed();
            });
        });

        // No water, no steam - and no coal burnt waiting for it. Then water, and one steam for one water.
        //
        // Factorio's boiler does nothing without water, and that is the rule that makes the
        // offshore pump worth building out to. The coal is the detail worth asserting: a boiler that
        // burnt fuel while dry would eat a chest of it waiting for a pipe, and one that kept asking for
        // ticks while dry would cost a tick a second for every boiler in a base that has run out.
        tests.add("boiler_needs_water", 100, PADDING, helper -> {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            insert(boiler.fuelAccess(), Items.COAL, 1);

            helper.startSequence()
                    .thenExecuteAfter(20, () -> {
                        helper.assertValueEqual(boiler.steam(), 0, "steam from a dry boiler");
                        helper.assertValueEqual(boiler.burnTime(), 0, "a dry boiler lit its coal");
                        helper.assertFalse(isScheduled(helper, BOILER, ModBlocks.BOILER.get()),
                                "a dry boiler is still asking for ticks");
                        water(boiler, BoilerBlockEntity.WATER_CAPACITY);
                    })
                    .thenExecuteAfter(20, () -> {
                        helper.assertTrue(boiler.steam() > 0, "water arriving did not start the boiler");
                        helper.assertTrue(boiler.burnTime() > 0, "it made steam without burning anything");
                        helper.assertValueEqual(boiler.water(), BoilerBlockEntity.WATER_CAPACITY - boiler.steam(),
                                "water left against steam made: Factorio's is one for one");
                    })
                    .thenSucceed();
        });
        tests.add("boilers_pass_water_along", BoilersPassWaterAlongTest::new, 100, PADDING);

        // Steam in, electricity out - the first energy this pack has ever made.
        tests.add("steam_engine_makes_power", 100, PADDING, helper -> {
            buildChain(helper, true);

            helper.runAfterDelay(20, () -> {
                SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);
                helper.assertTrue(engine.energyStored() > 0,
                        "a steam engine beside a burning boiler stored no energy");
                helper.succeed();
            });
        });

        // Non-negotiable #5, across three blocks.
        //
        // Nobody is drawing, so the engine fills and stops. Having stopped it draws no more steam,
        // so the boiler fills and stops too, and stops burning coal. Each of those is a separate
        // decision made at a different end of the chain, and a mistake in any one of them looks exactly
        // like a factory that works - right up until there are a thousand of them.
        tests.add("power_chain_sleeps", 400, PADDING, helper -> {
            buildChain(helper, true);

            // The engine fills in 100 ticks and the boiler in rather less once it stops being
            // drained. 250 leaves room for both and for the tick each spends discovering it.
            helper.runAfterDelay(250, () -> {
                SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);
                BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);

                helper.assertValueEqual(engine.energyStored(), SteamEngineBlockEntity.ENERGY_CAPACITY,
                        "the engine's charge");
                helper.assertValueEqual(boiler.steam(), BoilerBlockEntity.STEAM_CAPACITY,
                        "the boiler's steam");

                helper.assertFalse(isScheduled(helper, ENGINE, ModBlocks.STEAM_ENGINE.get()),
                        "a full steam engine is still scheduled to tick");
                helper.assertFalse(isScheduled(helper, BOILER, ModBlocks.BOILER.get()),
                        "a boiler nobody is drawing from is still scheduled to tick, so it is still "
                                + "burning coal into a buffer that cannot take it");
                helper.succeed();
            });
        });

        // A generator is not a battery: the grid cannot push energy back into it.
        tests.add("steam_engine_takes_no_power", 60, PADDING, helper -> {
            placeEngine(helper, ENGINE);
            SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(engine.cableView().insert(1000, transaction), 0,
                        "energy pushed into a generator");
                transaction.commit();
            }
            helper.assertValueEqual(engine.energyStored(), 0, "charge in an engine nobody fuelled");
            helper.succeed();
        });
        tests.add("pole_network_merges_and_splits", PoleNetworkMergesAndSplitsTest::new, 200, PADDING);
        tests.add("pole_finds_a_machine", PoleFindsAMachineTest::new, 100, PADDING);
        tests.add("pole_finds_a_later_machine", PoleFindsALaterMachineTest::new, 200, PADDING);
        tests.add("power_network_sleeps", PowerNetworkSleepsTest::new, 200, PADDING);
        tests.add("pole_stands_four_blocks_tall", PoleStandsFourBlocksTallTest::new, 100, PADDING);
        tests.add("pole_breaks_as_one", PoleBreaksAsOneTest::new, 200, PADDING);
        tests.add("pole_needs_headroom", PoleNeedsHeadroomTest::new, 100, PADDING);
        tests.add("pole_wires_link_up", PoleWiresLinkUpTest::new, 200, PADDING);
        tests.add("medium_pole_reaches_further", MediumPoleReachesFurtherTest::new, 200, PADDING);
        tests.add("big_pole_stands_two_by_two", BigPoleStandsTwoByTwoTest::new, 200, PADDING);
        tests.add("long_reach_pole_is_found_from_afar", LongReachPoleIsFoundFromAfarTest::new, 200, WIDE_PADDING);
        tests.add("substation_covers_more_ground", SubstationCoversMoreGroundTest::new, 200, WIDE_PADDING);
        tests.add("pole_wire_bounds_reach_both_ends", PoleWireBoundsTest::new, 100, PADDING);
        tests.add("boiler_opens_a_screen", BoilerOpensAScreenTest::new, 60, PADDING);

        // The fuel slot takes fuel and nothing else.
        //
        // This used to be checked in the right-click handler, which is gone. Without it the slot
        // would happily accept a diamond and then sit there doing nothing, and an inserter pointed at
        // the boiler would keep feeding it whatever it had. One isValid closes the screen, the
        // hopper and the inserter at once, which is why it is asserted through the automation view
        // rather than through the menu.
        tests.add("boiler_refuses_what_will_not_burn", 60, PADDING, helper -> {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);

            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.DIAMOND, 1), 0,
                    "diamonds accepted by a fuel slot");
            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.COAL, 1), 1,
                    "coal accepted by a fuel slot");
            helper.succeed();
        });
        tests.add("steam_engines_chain", SteamEnginesChainTest::new, 200, PADDING);

        // An engine takes steam through its two ends and nowhere else.
        //
        // The other half of the same claim. If the connection were on all six faces the facing
        // would be decoration, a row would be no different from a heap, and you could feed an engine
        // by burying a boiler under it. The engine here lies north-south with the boiler due west.
        tests.add("steam_engine_ignores_its_sides", 100, PADDING, helper -> {
            place(helper, BOILER, ModBlocks.BOILER.get());
            insert(helper.getBlockEntity(BOILER, BoilerBlockEntity.class).fuelAccess(), Items.COAL, 1);
            keepWatered(helper, BOILER);

            // Across the line rather than along it. The engine sits where a working one would,
            // and is turned a quarter turn - so its two open ends now point east and west, at
            // nothing, while the boiler's steam leaves to the north of it against a flank the
            // engine offers nothing on. Turning a machine has to be able to break a connection,
            // or its facing means nothing.
            place(helper, ENGINE, ModBlocks.STEAM_ENGINE.get().defaultBlockState()
                    .setValue(SteamEngineBlock.FACING, Direction.EAST));

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored(), 0,
                        "charge in an engine fed through its side, which has no connection");
                helper.succeed();
            });
        });
        tests.add("steam_engine_connects_on_two_faces", SteamEngineFacesTest::new, 60, PADDING);

        // A panel under the noon sun makes its peak, every tick.
        //
        // Factorio's 60 kW at the pack's ratio is eight a tick, and the rate is asserted as a
        // difference over twenty ticks rather than as a total, because the tick a fresh panel wakes on
        // is not a fact worth pinning. Eight a tick for twenty ticks is a hundred and sixty exactly.
        tests.add("solar_panel_makes_power_by_day", 100, PADDING, true, helper -> {
            assertNoon(helper);
            SolarPanelBlockEntity panel = placePanel(helper, BOILER);
            int[] seen = new int[1];

            helper.startSequence()
                    .thenExecuteAfter(30, () -> {
                        helper.assertTrue(panel.seesSky(helper.getLevel()), "the test has no sky over it");
                        helper.assertValueEqual(panel.lastOutput(), SolarPanelBlockEntity.PEAK,
                                "what a panel makes a tick at noon");
                        seen[0] = panel.energyStored();
                        helper.assertTrue(seen[0] > 0, "a panel at noon stored nothing");
                    })
                    .thenExecuteAfter(20, () -> {
                        helper.assertValueEqual(panel.energyStored() - seen[0], 20 * SolarPanelBlockEntity.PEAK,
                                "energy made over twenty ticks of noon");
                    })
                    .thenSucceed();
        });

        // What a panel makes follows the sky: the peak at noon, nothing at midnight, part way in rain,
        // and nothing at all under a roof.
        //
        // On the formula rather than the world, because the gametest world's sky cannot be darkened
        // - see #assertNoon. The numbers are Factorio's 60 kW at the pack's ratio, scaled by
        // Level.getSkyDarken(), which runs 0 at noon to 11 at midnight and sits around 4 in
        // rain.
        tests.add("solar_panel_follows_the_sky", 20, PADDING, helper -> {
            int full = SolarPanelBlockEntity.FULL_DARK;
            helper.assertValueEqual(SolarPanelBlockEntity.elevenths(0, true),
                    SolarPanelBlockEntity.PEAK * full, "a panel at noon, in elevenths of the peak");
            helper.assertValueEqual(SolarPanelBlockEntity.elevenths(full, true), 0, "a panel at midnight");
            helper.assertValueEqual(SolarPanelBlockEntity.elevenths(4, true),
                    SolarPanelBlockEntity.PEAK * (full - 4), "a panel in the rain - about two thirds");
            helper.assertValueEqual(SolarPanelBlockEntity.elevenths(0, false), 0, "a panel under a roof at noon");
            helper.assertValueEqual(SolarPanelBlockEntity.elevenths(99, true), 0,
                    "a darkening past full dark, which the game never gives but a mod might");
            helper.succeed();
        });

        // A panel under a roof makes nothing at noon.
        //
        // The one thing Minecraft can say about solar power that Factorio cannot. One block over
        // the middle is enough, because the middle is where the panel looks.
        tests.add("solar_panel_needs_the_sky", 100, PADDING, true, helper -> {
            assertNoon(helper);
            helper.setBlock(BOILER.above(3), Blocks.STONE);

            // The roof first, and a moment for the light to know about it: sky light is the light
            // engine's, and it settles a tick or two after the block goes in. A panel placed on the
            // same tick as its roof made one tick of noon before the shade arrived.
            helper.startSequence()
                    .thenExecuteAfter(5, () -> placePanel(helper, BOILER))
                    .thenExecuteAfter(30, () -> {
                        SolarPanelBlockEntity panel = helper.getBlockEntity(BOILER, SolarPanelBlockEntity.class);
                        helper.assertFalse(panel.seesSky(helper.getLevel()), "a roofed panel thinks it sees the sky");
                        helper.assertValueEqual(panel.energyStored(), 0, "energy a roofed panel made at noon");
                        helper.assertValueEqual(panel.lastOutput(), 0, "what a roofed panel makes a tick");
                        // Asleep but for the long look-up - non-negotiable #5 for a machine whose work
                        // has no event to arrive on.
                        helper.assertTrue(isScheduled(helper, BOILER, ModBlocks.SOLAR_PANEL.get()),
                                "a roofed panel has no tick coming, so the roof coming off would never reach it");
                    })
                    .thenSucceed();
        });
        tests.add("accumulator_is_a_buffer", AccumulatorIsABufferTest::new, 20, PADDING);
        tests.add("accumulator_charges_from_surplus", AccumulatorChargesFromSurplusTest::new, 200, PADDING);
        tests.add("accumulators_do_not_feed_each_other", AccumulatorsDoNotFeedEachOtherTest::new, 100, PADDING);
    }

    /** Where the chain stands, now that a boiler is seven blocks and an engine is seventeen. */
    private static final BlockPos BOILER = new BlockPos(0, 1, 0);
    private static final BlockPos ENGINE = new BlockPos(0, 1, 3);

    /** How much empty world to leave around each test. */
    private static final int PADDING = 24;

    /** Room for the two tests that stand poles thirty blocks apart. See {@link #registerWide}. */
    private static final int WIDE_PADDING = 72;

    /**
     * A boiler with coal in it, and an engine lying along the line to it.
     *
     * <p>The facing matters now. An engine takes steam through the two faces on its own axis, so
     * one laid north-south beside a boiler to its west connects to nothing at all - which is the
     * point of it being directional, and a thing every test here has to respect.
     */
    private static void buildChain(GameTestHelper helper, boolean fuelled) {
        place(helper, BOILER, ModBlocks.BOILER.get());
        placeEngine(helper, ENGINE);
        if (fuelled) {
            insert(helper.getBlockEntity(BOILER, BoilerBlockEntity.class).fuelAccess(), Items.COAL, 1);
            keepWatered(helper, BOILER);
        }
    }

    /** Water into a boiler the way a pipe run puts it there: through the handler at its water ports. */
    private static int water(BoilerBlockEntity boiler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = boiler.waterAccess().insert(FluidResource.of(Fluids.WATER), amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * A lake, as far as a boiler is concerned: its tank topped up every tick, which is what a pipe
     * from an offshore pump does. The pump is another mod's and this one may not name it; the pack
     * mod's {@code power_reaches_a_machine} runs the real one.
     */
    private static void keepWatered(GameTestHelper helper, BlockPos boiler) {
        helper.onEachTick(() -> water(helper.getBlockEntity(boiler, BoilerBlockEntity.class),
                BoilerBlockEntity.WATER_CAPACITY));
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
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

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    /**
     * Places a block the way a player does, {@code setPlacedBy} included.
     *
     * <p>{@code helper.setBlock} writes one block state and stops, which is the whole of most
     * blocks and none of a pole: a pole is three blocks tall and the upper two are placed from
     * {@code setPlacedBy}. A test that skipped it would be testing a pole that cannot exist.
     */
    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        place(helper, pos, block.defaultBlockState());
    }

    /**
     * The same, for a block that has to be turned a particular way.
     *
     * <p>The state must carry the machine's <em>anchor</em> part, which the default state does, so
     * that {@code setPlacedBy} builds the rest of the machine around this position rather than
     * around some corner of it.
     */
    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state);
        BlockPos absolute = helper.absolutePos(pos);
        state.getBlock().setPlacedBy(helper.getLevel(), absolute,
                helper.getLevel().getBlockState(absolute), null, ItemStack.EMPTY);
    }

    /** An engine lying north-south, which is the axis its two steam ends are on. */
    private static void placeEngine(GameTestHelper helper, BlockPos pos) {
        place(helper, pos, ModBlocks.STEAM_ENGINE.get().defaultBlockState()
                .setValue(SteamEngineBlock.FACING, Direction.NORTH));
    }

    /** Which cell of a pole, if any, stands at a test-relative position; -1 for no pole. */
    private static int partAt(GameTestHelper helper, BlockPos pos, ElectricPoleBlock pole) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(pos));
        return state.is(pole) ? Multiblock.part(pole, state) : -1;
    }

    /** The poles this one is wired to, which is what the client draws from. */
    private static long[] links(GameTestHelper helper, BlockPos pole) {
        return helper.getBlockEntity(pole, ElectricPoleBlockEntity.class).links();
    }

    private static void assertWiredTo(GameTestHelper helper, BlockPos from, BlockPos to) {
        long wanted = helper.absolutePos(to).asLong();
        for (long link : links(helper, from)) {
            if (link == wanted) {
                return;
            }
        }
        helper.fail("the pole at " + from + " is not wired to the one at " + to);
    }

    private static PowerNetworkManager grid(GameTestHelper helper) {
        return PowerNetworkManager.of(helper.getLevel());
    }

    /** The network the pole at a test-relative position belongs to, or null if it has none. */
    private static @Nullable PowerNetwork networkAt(GameTestHelper helper, BlockPos pos) {
        return grid(helper).networkAt(helper.absolutePos(pos));
    }

    private static PowerNetwork requireNetwork(GameTestHelper helper, BlockPos pos, String what) {
        PowerNetwork network = networkAt(helper, pos);
        helper.assertTrue(network != null, what);
        return network;
    }

    /** <b>The graph, and the two operations that are expensive to get wrong.</b> */
    public static class PoleNetworkMergesAndSplitsTest extends PackGameTest {

        private static final BlockPos LOWER = new BlockPos(0, 1, 0);
        private static final BlockPos MIDDLE = new BlockPos(0, 5, 0);
        private static final BlockPos UPPER = new BlockPos(0, 9, 0);

        PoleNetworkMergesAndSplitsTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, LOWER, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, UPPER, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole has no network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole has no network");
                        helper.assertFalse(lower == upper,
                                "two poles eight blocks apart joined one network, so wire reach is "
                                        + "not being measured");
                        helper.assertValueEqual(lower.poleCount(), 1, "poles in the lower network");
                    })
                    .thenExecute(() -> place(helper, MIDDLE, ModBlocks.SMALL_ELECTRIC_POLE.get()))
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole lost its network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole lost its network");
                        helper.assertTrue(lower == upper,
                                "a pole bridging two networks did not merge them");
                        helper.assertValueEqual(lower.poleCount(), 3, "poles in the merged network");
                    })
                    .thenExecute(() -> helper.setBlock(MIDDLE, Blocks.AIR))
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork lower = requireNetwork(helper, LOWER, "the lower pole lost its network");
                        PowerNetwork upper = requireNetwork(helper, UPPER, "the upper pole lost its network");
                        helper.assertFalse(lower == upper,
                                "breaking the only pole joining two halves left them on one network");
                        helper.assertValueEqual(lower.poleCount(), 1, "poles in the lower half");
                        helper.assertValueEqual(upper.poleCount(), 1, "poles in the upper half");
                        helper.assertTrue(networkAt(helper, MIDDLE) == null,
                                "a pole that no longer exists is still in the index");
                    })
                    .thenSucceed();
        }

    }

    /** A pole finds the machines already standing in its supply area when it loads. */
    public static class PoleFindsAMachineTest extends PackGameTest {

        private static final BlockPos POLE = new BlockPos(3, 1, 0);
        private static final BlockPos OUT_OF_RANGE = new BlockPos(0, 5, 0);

        PoleFindsAMachineTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // The near engine has blocks inside the pole's 5x5 supply area; the other is four
            // above it, outside - a supply area that was really the wire reach in disguise would
            // pick up both, and nothing else would notice.
            //
            // Note what a footprint changed here. The engine is powered because part of it is in
            // the area, not because its middle is: its anchor is three blocks from the pole and
            // out of range on its own. That is Factorio's rule - a pole powers a machine its area
            // touches - and it is why every block of a machine publishes the energy capability.
            // What that costs is a machine seen several times over, which PowerNetwork settles by
            // counting distinct handlers rather than positions.
            placeEngine(helper, ENGINE);
            placeEngine(helper, OUT_OF_RANGE);
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.endpointCount(), 1,
                        "machines a pole found in its supply area");
                boolean foundTheEngine = false;
                for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                    BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                    foundTheEngine |= network.hasEndpoint(helper.absolutePos(cell).asLong());
                }
                helper.assertTrue(foundTheEngine,
                        "the machine beside the pole is not the one it found");
                helper.succeed();
            });
        }

    }

    /**
     * A machine built <em>after</em> the pole is found too.
     *
     * <p>This is the case with no obvious hook. The machine is two blocks away, so it is in
     * nobody's neighbourhood; it lives in another mod, so it cannot announce itself without that
     * mod learning what a pole is; and a pole that went looking on a schedule would be the
     * per-tick scan the whole design exists to avoid. Deleting the {@code NeighborNotifyEvent}
     * subscription in {@code PowerGridEvents} fails this test and nothing else.
     */
    public static class PoleFindsALaterMachineTest extends PackGameTest {

        private static final BlockPos POLE = new BlockPos(3, 1, 0);

        PoleFindsALaterMachineTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(10, () -> helper.assertValueEqual(
                            requireNetwork(helper, POLE, "the pole has no network").endpointCount(),
                            0,
                            "machines found beside a pole standing on its own"))
                    .thenExecute(() -> placeEngine(helper, ENGINE))
                    .thenExecuteAfter(10, () -> helper.assertValueEqual(
                            requireNetwork(helper, POLE, "the pole lost its network").endpointCount(),
                            1,
                            "machines found after one was built two blocks from the pole"))
                    .thenSucceed();
        }

    }

    /** <b>Non-negotiable #5, for a graph.</b> */
    public static class PowerNetworkSleepsTest extends PackGameTest {

        private static final BlockPos POLE = new BlockPos(2, 1, 0);

        PowerNetworkSleepsTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(150, () -> {
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.endpointCount(), 1, "machines on the network");
                helper.assertValueEqual(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored(),
                        SteamEngineBlockEntity.ENERGY_CAPACITY,
                        "the engine's charge");
                helper.assertFalse(grid(helper).isActive(network),
                        "a network with a full generator and nothing to spend it on is still being "
                                + "ticked every tick");
                helper.succeed();
            });
        }

    }

    /**
     * A pole is four blocks, and only the foot is a pole as far as the grid is concerned.
     *
     * <p>The second assertion is the one worth having. A multi-block that put a block entity in
     * every part would work perfectly and cost three times the memory, and nothing else here would
     * ever notice.
     */
    public static class PoleStandsFourBlocksTallTest extends PackGameTest {

        private static final BlockPos FOOT = new BlockPos(0, 1, 0);

        PoleStandsFourBlocksTallTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            ElectricPoleBlock pole = ModBlocks.SMALL_ELECTRIC_POLE.get();
            place(helper, FOOT, pole);

            helper.assertValueEqual(pole.height(), 4, "blocks in a small pole");
            for (int height = 0; height < pole.height(); height++) {
                helper.assertValueEqual(partAt(helper, FOOT.above(height), pole), height,
                        "the cell " + height + " blocks up");
                helper.assertTrue(
                        (helper.getLevel().getBlockEntity(
                                helper.absolutePos(FOOT.above(height))) != null) == (height == 0),
                        "block entity " + height + " blocks up a pole - only the foot should have "
                                + "one, or a base of poles pays four times over");
            }

            helper.runAfterDelay(5, () -> {
                PowerNetwork network = requireNetwork(helper, FOOT, "the pole foot has no network");
                helper.assertValueEqual(network.poleCount(), 1,
                        "poles in the network - four blocks are one pole");
                helper.assertTrue(networkAt(helper, FOOT.above()) == null,
                        "the middle of a pole joined the network as a pole of its own");
                helper.succeed();
            });
        }

    }

    /**
     * Break any part of a pole and the whole pole goes - once, and for one item back.
     *
     * <p>The middle is the interesting one to hit: it is neither the part that holds the block
     * entity nor the part that drops the item, so it is the case where a teardown that only
     * handled "broken from the bottom" would leave a pole floating with nothing under it.
     */
    public static class PoleBreaksAsOneTest extends PackGameTest {

        private static final BlockPos FOOT = new BlockPos(0, 1, 0);

        PoleBreaksAsOneTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, FOOT, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(networkAt(helper, FOOT) != null, "the pole has no network");
                        helper.setBlock(FOOT.above(), Blocks.AIR);
                    })
                    .thenExecuteAfter(5, () -> {
                        for (int height = 0; height < ModBlocks.SMALL_ELECTRIC_POLE.get().height(); height++) {
                            helper.assertTrue(
                                    partAt(helper, FOOT.above(height),
                                            ModBlocks.SMALL_ELECTRIC_POLE.get()) < 0,
                                    "part of the pole is still standing " + height
                                            + " blocks up after the middle was broken");
                        }
                        helper.assertTrue(networkAt(helper, FOOT) == null,
                                "a pole that no longer exists is still in the grid index");

                        // One item, not three and not none. The middle and the top drop nothing
                        // by a loot-table condition; the bottom's own destruction is what pays
                        // the player back, whichever part they actually hit.
                        helper.assertItemEntityCountIs(
                                ModItems.SMALL_ELECTRIC_POLE.get(), FOOT, 4.0, 1);
                    })
                    .thenSucceed();
        }

    }

    /**
     * A pole will not go under a low ceiling rather than going in half-built.
     *
     * <p>Refusing at {@code getStateForPlacement} is what makes the teardown rule safe to state so
     * bluntly: if a pole could ever be placed with no room for its top, that rule would delete it
     * again the instant anything nudged it, and the player would have watched a pole vanish.
     */
    public static class PoleNeedsHeadroomTest extends PackGameTest {

        private static final BlockPos GROUND = new BlockPos(0, 1, 0);
        private static final BlockPos FOOT = new BlockPos(0, 2, 0);

        PoleNeedsHeadroomTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(GROUND, Blocks.STONE);

            helper.assertTrue(placementState(helper) != null,
                    "a pole refused to go somewhere with three blocks of clear air above it");

            // A ceiling where the pole's own top would be.
            helper.setBlock(FOOT.above(2), Blocks.STONE);
            helper.assertTrue(placementState(helper) == null,
                    "a pole went in under a ceiling too low for it, so two thirds of it is missing");

            helper.succeed();
        }

        /** What the block would place as, asked exactly the way a right-click asks it. */
        private static @Nullable BlockState placementState(GameTestHelper helper) {
            BlockPos below = helper.absolutePos(GROUND);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
            BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.SMALL_ELECTRIC_POLE.get()), hit);
            return ModBlocks.SMALL_ELECTRIC_POLE.get().getStateForPlacement(context);
        }

    }

    /** <b>Poles wire themselves up.</b> */
    /**
     * A medium pole spans a gap two small poles cannot, and a small pole at the other end is
     * still wired to it.
     */
    public static class MediumPoleReachesFurtherTest extends PackGameTest {

        /** Eight apart: past the small pole's 7.5 and inside the medium pole's 9. */
        private static final BlockPos WEST = new BlockPos(0, 1, 0);
        private static final BlockPos EAST = new BlockPos(8, 1, 0);

        MediumPoleReachesFurtherTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, WEST, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, EAST, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(links(helper, WEST).length, 0,
                                "wires between two small poles eight blocks apart");
                        helper.assertValueEqual(links(helper, EAST).length, 0,
                                "wires between two small poles eight blocks apart");
                    })
                    // Swap only the east one. The gap has not changed; the reach at one end has.
                    .thenExecute(() -> {
                        for (int height = 0; height < ModBlocks.SMALL_ELECTRIC_POLE.get().height(); height++) {
                            helper.setBlock(EAST.above(height), Blocks.AIR);
                        }
                    })
                    .thenExecuteAfter(5,
                            () -> place(helper, EAST, ModBlocks.MEDIUM_ELECTRIC_POLE.get()))
                    .thenExecuteAfter(5, () -> {
                        assertWiredTo(helper, EAST, WEST);
                        assertWiredTo(helper, WEST, EAST);
                    })
                    .thenSucceed();
        }

    }

    /**
     * A big pole is twenty-four blocks and one item, and its wires come off one network node.
     *
     * <p>The two-by-two footprint is the part worth asserting. A pole was a column for as long as
     * there was one tier, so everything about it - placement, teardown, which cell drops the item,
     * where a wire attaches - had only ever been exercised in one dimension. All of it is
     * {@code Multiblock}'s now, and this is the first pole that would notice if it were not.
     */
    public static class BigPoleStandsTwoByTwoTest extends PackGameTest {

        private static final BlockPos FOOT = new BlockPos(0, 1, 0);

        BigPoleStandsTwoByTwoTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            ElectricPoleBlock pole = ModBlocks.BIG_ELECTRIC_POLE.get();
            place(helper, FOOT, pole);

            helper.assertValueEqual(pole.shape().cellCount(), 24, "blocks in a big pole");
            helper.assertValueEqual(pole.shape().width(), 2, "tiles across");
            helper.assertValueEqual(pole.shape().depth(), 2, "tiles deep");
            helper.assertValueEqual(pole.height(), 6, "blocks tall");

            for (int index = 0; index < pole.shape().cellCount(); index++) {
                BlockPos cell = pole.shape().cellPos(FOOT, index, Direction.NORTH);
                helper.assertValueEqual(partAt(helper, cell, pole), index,
                        "the cell at " + cell);
                helper.assertTrue(
                        (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null)
                                == (index == BigPoleShape.FOOT),
                        "block entity at cell " + index + " - only the foot should have one");
            }

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork network = requireNetwork(helper, FOOT, "the big pole has no network");
                        helper.assertValueEqual(network.poleCount(), 1,
                                "poles in the network - twenty-four blocks are one pole");
                        // A leg that is not the foot, so the teardown has to cross the footprint
                        // and not just run down a column.
                        helper.setBlock(FOOT.east().above(3), Blocks.AIR);
                    })
                    .thenExecuteAfter(5, () -> {
                        for (int index = 0; index < pole.shape().cellCount(); index++) {
                            BlockPos cell = pole.shape().cellPos(FOOT, index, Direction.NORTH);
                            helper.assertTrue(partAt(helper, cell, pole) < 0,
                                    "cell " + index + " is still standing after one leg was broken");
                        }
                        helper.assertTrue(networkAt(helper, FOOT) == null,
                                "a pole that no longer exists is still in the grid index");
                        helper.assertItemEntityCountIs(
                                ModItems.BIG_ELECTRIC_POLE.get(), FOOT, 6.0, 1);
                    })
                    .thenSucceed();
        }

    }

    /**
     * A small pole is wired to a big one that reaches it from further than the small one can
     * see.
     */
    public static class LongReachPoleIsFoundFromAfarTest extends PackGameTest {

        /** Inside the big pole's thirty and outside the small pole's seven and a half. */
        private static final int GAP = 17;

        /** The pole index's cell size. Not imported: this test is about what happens at its edges. */
        private static final int CELL = 16;

        LongReachPoleIsFoundFromAfarTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // Put the small pole on the last block of a cell, so the big pole seventeen east of it
            // lands two cells over.
            int align = Math.floorMod(helper.absolutePos(BlockPos.ZERO).getX(), CELL);
            BlockPos near = new BlockPos(Math.floorMod(CELL - 1 - align, CELL), 1, 0);
            BlockPos far = near.east(GAP);

            helper.assertValueEqual(
                    Math.abs((helper.absolutePos(far).getX() >> 4) - (helper.absolutePos(near).getX() >> 4)),
                    2, "cells between the two poles - the arrangement this test is about");

            // **The big pole goes down first**, and that ordering is the test.
            //
            // A pole joining looks at what it can see and merges with whatever it finds, so the
            // second one placed is the one whose scan has to be right. Put the big pole down
            // second and it finds the small one through its own two-cell span, the networks merge,
            // and the assertion below passes with the long-reach index deleted - which is exactly
            // what happened the first time this was written.
            place(helper, far, ModBlocks.BIG_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> place(helper, near, ModBlocks.SMALL_ELECTRIC_POLE.get()))
                    .thenExecuteAfter(5, () -> {
                        PowerNetwork network =
                                requireNetwork(helper, near, "the small pole has no network");
                        helper.assertValueEqual(network.poleCount(), 2,
                                "poles in the network - a big pole reaches a small one, and a wire "
                                        + "has one length whichever end you measure it from");

                        // And both ends know about the wire, which is a second answer from the
                        // same method: the client draws from `links`, and a pole that had joined
                        // the right network with no link would look detached and work perfectly.
                        assertWiredTo(helper, near, far);
                        assertWiredTo(helper, far, near);
                        helper.succeed();
                    })
                    .thenSucceed();
        }

    }

    /**
     * A substation supplies a machine nine blocks away and a small pole does not.
     *
     * <p>Supply is measured from the footprint outwards now rather than as a radius around the
     * foot, because Factorio's areas are 5x5 around a one-tile pole and 18x18 around a two-tile
     * one and neither is a radius. The one-tile case has to come out at exactly the 5x5 it always
     * was, so the small pole is the control rather than an afterthought.
     */
    public static class SubstationCoversMoreGroundTest extends PackGameTest {

        private static final BlockPos SUBSTATION = new BlockPos(0, 1, 0);
        private static final BlockPos SMALL = new BlockPos(0, 1, 24);

        /** Nine east of each: outside a 5x5 area, inside an 18x18 one. */
        private static final int GAP = 9;

        SubstationCoversMoreGroundTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            placeEngine(helper, SUBSTATION.east(GAP));
            placeEngine(helper, SMALL.east(GAP));
            place(helper, SUBSTATION, ModBlocks.SUBSTATION.get());
            place(helper, SMALL, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                helper.assertValueEqual(
                        requireNetwork(helper, SUBSTATION, "the substation has no network")
                                .endpointCount(),
                        1, "machines a substation found nine blocks away");
                helper.assertValueEqual(
                        requireNetwork(helper, SMALL, "the small pole has no network")
                                .endpointCount(),
                        0, "machines a small pole found nine blocks away");
                helper.succeed();
            });
        }

    }

    public static class PoleWiresLinkUpTest extends PackGameTest {

        private static final BlockPos NEAR = new BlockPos(0, 1, 0);
        /** Five apart, inside the 7.5 wire reach. */
        private static final BlockPos ALSO_NEAR = new BlockPos(5, 1, 0);
        /** Twelve from both, outside it. */
        private static final BlockPos FAR = new BlockPos(0, 1, 12);

        PoleWiresLinkUpTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, ALSO_NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, FAR, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        assertWiredTo(helper, NEAR, ALSO_NEAR);
                        assertWiredTo(helper, ALSO_NEAR, NEAR);
                        helper.assertValueEqual(links(helper, FAR).length, 0,
                                "wires from a pole twelve blocks from anything");
                    })
                    .thenExecute(() -> helper.setBlock(ALSO_NEAR, Blocks.AIR))
                    .thenExecuteAfter(5, () -> helper.assertValueEqual(links(helper, NEAR).length, 0,
                            "wires still hanging off a pole whose only neighbour was broken"))
                    .thenSucceed();
        }

    }

    /** <b>A pole claims enough space for the wires it draws, or they are culled away.</b> */
    public static class PoleWireBoundsTest extends PackGameTest {

        private static final BlockPos NEAR = new BlockPos(0, 1, 0);
        private static final BlockPos ALSO_NEAR = new BlockPos(6, 1, 0);

        PoleWireBoundsTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, ALSO_NEAR, ModBlocks.SMALL_ELECTRIC_POLE.get());

            helper.runAfterDelay(5, () -> {
                AABB bounds = helper.getBlockEntity(NEAR, ElectricPoleBlockEntity.class).wireBounds();

                BlockPos ownHead = helper.absolutePos(
                        NEAR.above(ModBlocks.SMALL_ELECTRIC_POLE.get().height() - 1));
                BlockPos farHead = helper.absolutePos(
                        ALSO_NEAR.above(ModBlocks.SMALL_ELECTRIC_POLE.get().height() - 1));

                helper.assertTrue(bounds.contains(Vec3.atCenterOf(ownHead)),
                        "a pole does not claim its own head, so its wires are culled the moment "
                                + "its foot leaves the screen");
                helper.assertTrue(bounds.contains(Vec3.atCenterOf(farHead)),
                        "a pole does not claim the far end of the wire it draws, so the wire "
                                + "disappears whenever the pole itself is out of view");
                helper.succeed();
            });
        }

    }

    /**
     * A boiler opens, and what opens has the fuel slot in it.
     *
     * <p>It used to be fuelled by right-clicking with coal in hand, which meant its contents were
     * invisible and could not be taken back out. The assertion that matters is the slot count: a
     * menu that came up with only the player's inventory in it would look like a working screen
     * and be useless.
     */
    public static class BoilerOpensAScreenTest extends PackGameTest {

        /** Six ingredient slots' worth of player inventory, plus the machine's own. */
        private static final int PLAYER_SLOTS = 36;

        BoilerOpensAScreenTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            insert(boiler.fuelAccess(), Items.COAL, 1);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            AbstractContainerMenu menu = boiler.createMenu(1, player.getInventory(), player);
            helper.assertTrue(menu instanceof BoilerMenu, "the boiler opened something else");
            helper.assertValueEqual(menu.slots.size(), BoilerBlockEntity.SLOT_COUNT + PLAYER_SLOTS,
                    "slots in the boiler's menu");
            helper.assertValueEqual(
                    menu.getSlot(BoilerBlockEntity.FUEL_SLOT).getItem().getItem(), Items.COAL,
                    "what the first slot of the boiler's menu is showing");
            helper.succeed();
        }

    }

    /**
     * <b>Engines chain.</b> One boiler, two engines in a line, and the far one runs.
     *
     * <p>This is Factorio's arrangement and the reason an engine is a length of pipe that happens
     * to consume rather than a thing with a private connection to a boiler: steam runs along the
     * row, and the engine at the end is fed by the one before it. An engine that only drew from
     * boilers would pass every other test in this file and leave the second engine dead.
     */
    public static class SteamEnginesChainTest extends PackGameTest {

        /**
         * The next engine along the same line, chained off the far end of the first.
         *
         * <p>Five blocks past {@link #ENGINE}: two to reach the end of its spine, one for the seam
         * where the two machines touch, and two more to the middle of the second. A row of engines
         * off one boiler is the arrangement this subsystem exists for, and this is what one costs
         * now that an engine is the size Factorio made it.
         */
        private static final BlockPos FAR_ENGINE = new BlockPos(0, 1, 8);

        SteamEnginesChainTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);
            placeEngine(helper, FAR_ENGINE);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(
                        helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class).energyStored() > 0,
                        "the engine beside the boiler made no power");
                helper.assertTrue(
                        helper.getBlockEntity(FAR_ENGINE, SteamEngineBlockEntity.class).energyStored() > 0,
                        "the second engine in the row made no power, so steam does not run along "
                                + "a line of them and only the first one is worth building");
                helper.succeed();
            });
        }

    }

    /**
     * The other side of the connection: what a <em>pipe</em> sees when it looks at an engine.
     *
     * <p>{@code steam_engine_ignores_its_sides} covers which way the engine looks. This covers
     * which faces answer when something looks at it, which is a separate registration and the one
     * that will decide whether a pipe run can join an engine end-on or barge into its flank. It is
     * asserted directly because nothing else reaches it until pipes exist - breaking the sided
     * registration leaves every other test in this file passing.
     */
    public static class SteamEngineFacesTest extends PackGameTest {

        SteamEngineFacesTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            placeEngine(helper, ENGINE);

            // The two open ends of the spine, and the face each one opens through. A north-facing
            // engine runs north to south, so its ends are two blocks either side of the middle.
            BlockPos north = SteamEngineShape.SHAPE.cellPos(
                    ENGINE, SteamEngineShape.NORTH_END, Direction.NORTH);
            BlockPos south = SteamEngineShape.SHAPE.cellPos(
                    ENGINE, SteamEngineShape.SOUTH_END, Direction.NORTH);

            helper.assertValueEqual(north, ENGINE.north(2), "where the north end of a spine is");
            helper.assertValueEqual(south, ENGINE.south(2), "where the south end of a spine is");

            helper.assertTrue(offersSteam(helper, north, Direction.NORTH),
                    "no steam at the north end of the engine, which is where a pipe goes");
            helper.assertTrue(offersSteam(helper, south, Direction.SOUTH),
                    "no steam at the south end of the engine");

            // Everywhere else, on every face: nothing. An engine is fed at its ends or not at
            // all, which is what makes a row of them a row rather than a heap - and now that it
            // has a footprint, "its ends" means two particular blocks rather than two faces of
            // one. The flanks are the interesting case: they are as close to a pipe as the ends
            // are, and they must still refuse it.
            for (int part = 0; part < SteamEngineShape.SHAPE.cellCount(); part++) {
                BlockPos cell = SteamEngineShape.SHAPE.cellPos(ENGINE, part, Direction.NORTH);
                for (Direction side : Direction.values()) {
                    boolean isPort = (cell.equals(north) && side == Direction.NORTH)
                            || (cell.equals(south) && side == Direction.SOUTH);
                    helper.assertValueEqual(offersSteam(helper, cell, side), isPort,
                            "whether the engine offers steam at " + cell + " on its " + side
                                    + " face");
                }
            }
            helper.succeed();
        }

        private static boolean offersSteam(GameTestHelper helper, BlockPos pos, Direction side) {
            return helper.getLevel().getCapability(
                    Capabilities.Fluid.BLOCK, helper.absolutePos(pos), side) != null;
        }

    }

    /** A boiler turned every way it can be, and everything about it turning with it. */
    public static class BoilerTurnsAsOneTest extends PackGameTest {

        /**
         * Facing, then the footprint it should occupy around the anchor, then where steam goes,
         * then the two blocks and faces water comes in at - the ends of the front row, which is
         * the row furthest from the steam.
         */
        private record Turned(Direction facing, int minX, int maxX, int minZ, int maxZ,
                Direction port, BlockPos waterA, Direction waterASide, BlockPos waterB,
                Direction waterBSide) {}

        private static final List<Turned> EXPECTED = List.of(
                new Turned(Direction.NORTH, -1, 1, -1, 0, Direction.SOUTH,
                        new BlockPos(-1, 0, -1), Direction.WEST, new BlockPos(1, 0, -1), Direction.EAST),
                new Turned(Direction.EAST, 0, 1, -1, 1, Direction.WEST,
                        new BlockPos(1, 0, -1), Direction.NORTH, new BlockPos(1, 0, 1), Direction.SOUTH),
                new Turned(Direction.SOUTH, -1, 1, 0, 1, Direction.NORTH,
                        new BlockPos(1, 0, 1), Direction.EAST, new BlockPos(-1, 0, 1), Direction.WEST),
                new Turned(Direction.WEST, -1, 0, -1, 1, Direction.EAST,
                        new BlockPos(-1, 0, 1), Direction.SOUTH, new BlockPos(-1, 0, -1), Direction.NORTH));

        /** One port, as a string a list can be asked about. */
        private static String port(BlockPos offset, Direction side) {
            return offset.toShortString() + " " + side;
        }

        BoilerTurnsAsOneTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (Turned expected : EXPECTED) {
                check(helper, expected);
            }
            helper.succeed();
        }

        private void check(GameTestHelper helper, Turned expected) {
            clear(helper);
            place(helper, BOILER, ModBlocks.BOILER.get().defaultBlockState()
                    .setValue(BoilerBlock.FACING, expected.facing()));

            int minX = 99;
            int maxX = -99;
            int minZ = 99;
            int maxZ = -99;
            int blocks = 0;
            List<String> ports = new ArrayList<>();

            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockPos pos = BOILER.offset(x, y, z);
                        if (!helper.getBlockState(pos).is(ModBlocks.BOILER.get())) {
                            continue;
                        }
                        blocks++;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);

                        for (Direction side : Direction.values()) {
                            if (helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                                    helper.absolutePos(pos), side) != null) {
                                ports.add(port(new BlockPos(x, y, z), side));
                            }
                        }
                    }
                }
            }

            String turned = "a boiler facing " + expected.facing();
            helper.assertValueEqual(blocks, 7, turned + " is not seven blocks");
            helper.assertValueEqual(minX, expected.minX(), turned + ": western edge");
            helper.assertValueEqual(maxX, expected.maxX(), turned + ": eastern edge");
            helper.assertValueEqual(minZ, expected.minZ(), turned + ": northern edge");
            helper.assertValueEqual(maxZ, expected.maxZ(), turned + ": southern edge");

            helper.assertValueEqual(ports.size(), 3,
                    turned + " offers fluid at " + ports + ", not at exactly three places");
            helper.assertTrue(ports.contains(port(BlockPos.ZERO, expected.port())),
                    turned + ": steam leaves by " + ports + ", not the " + expected.port() + " face of the anchor");
            helper.assertTrue(ports.contains(port(expected.waterA(), expected.waterASide())),
                    turned + ": no water port at " + expected.waterA().toShortString() + " "
                            + expected.waterASide() + ", only " + ports);
            helper.assertTrue(ports.contains(port(expected.waterB(), expected.waterBSide())),
                    turned + ": no water port at " + expected.waterB().toShortString() + " "
                            + expected.waterBSide() + ", only " + ports);
        }

        /** The machine from the last facing, out of the way of the next one. */
        private void clear(GameTestHelper helper) {
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockPos pos = BOILER.offset(x, y, z);
                        if (helper.getBlockState(pos).is(ModBlocks.BOILER.get())) {
                            helper.setBlock(pos, Blocks.AIR);
                        }
                    }
                }
            }
        }

    }

    /** A power plant you can walk across: boilers in a row, and an engine crossed at its waist. */
    public static class PowerMachinesTileWalkablyTest extends PackGameTest {

        private static final double STEP = 0.6;
        private static final double JUMP = 1.25;

        PowerMachinesTileWalkablyTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // Two boilers shoulder to shoulder, and the row across their front.
            place(helper, BOILER, ModBlocks.BOILER.get());
            place(helper, BOILER.offset(3, 0, 0), ModBlocks.BOILER.get());
            walk(helper, BOILER.offset(-1, 0, -1), Direction.EAST, 6, "the front of a boiler row");

            // An engine, crossed at the waist between its flywheels.
            placeEngine(helper, ENGINE);
            walk(helper, ENGINE.offset(-2, 0, 0), Direction.EAST, 5, "the waist of an engine");

            // And the flywheels really are two blocks tall, so this cannot pass by going flat.
            helper.assertValueEqual(surface(helper, ENGINE.north(1), 2), 2.0,
                    "height of the first flywheel");
            helper.assertValueEqual(surface(helper, ENGINE.south(1), 2), 2.0,
                    "height of the second flywheel");
            helper.assertValueEqual(surface(helper, BOILER, 2), 2.0, "height of a chimney");
            helper.succeed();
        }

        /** Walks a line one column at a time, and complains about the first step too big to take. */
        private void walk(GameTestHelper helper, BlockPos from, Direction along, int columns,
                String what) {
            double previous = 0;
            for (int step = 0; step < columns; step++) {
                BlockPos column = from.relative(along, step);
                double top = surface(helper, column, 2);

                helper.assertTrue(top <= JUMP, what + ": the column at " + column + " stands "
                        + top + " blocks high, more than the " + JUMP + " a player can jump onto");
                // Getting onto the machine in the first place is a jump, and is allowed to be
                // one. Everything after that has to be a step, or crossing a factory is hopping.
                // Only the climbs. Walking off the far side of a machine is a drop, and a
                // drop of one block costs a player nothing at all.
                double climb = top - previous;
                double allowed = previous == 0 ? JUMP : STEP;
                helper.assertTrue(step == 0 || climb <= allowed,
                        what + ": the step up onto " + column + " is " + climb + " blocks, more "
                                + "than the " + allowed + " a player manages from " + previous);
                helper.assertTrue(helper.getBlockState(column.above(2)).isAir(),
                        what + ": no headroom over " + column);
                previous = top;
            }
        }

    }

    /**
     * The gametest world stands at noon, and stays there whatever its world clock is set to.
     *
     * <p>Tried the other way first: {@code server.clockManager().moveToTimeMarker(clock, MIDNIGHT)}
     * moved the clock and the sky's darkening stayed at zero, so a panel made its full peak at
     * "midnight". So the day test asserts the noon it is given, and the night is tested on the
     * formula rather than the world - see {@link SolarPanelFollowsTheSkyTest}.
     */
    private static void assertNoon(GameTestHelper helper) {
        helper.assertValueEqual(helper.getLevel().getSkyDarken(), 0,
                "the gametest world's sky darkening - this test assumes it stands at noon");
    }

    private static SolarPanelBlockEntity placePanel(GameTestHelper helper, BlockPos anchor) {
        place(helper, anchor, ModBlocks.SOLAR_PANEL.get());
        return helper.getBlockEntity(anchor, SolarPanelBlockEntity.class);
    }

    /**
     * Boilers pass water along a row: the second is fed through the first.
     *
     * <p>Factorio's boilers have a water connection at each end and a row of them is piped once,
     * at one end. Here the second boiler draws from the first's tank through the face they share,
     * the way an engine draws steam from the engine before it. Only the first is watered, so the
     * second's steam can only have come through it.
     */
    public static class BoilersPassWaterAlongTest extends PackGameTest {

        private static final BlockPos FIRST = new BlockPos(0, 1, 0);

        /** End to end with the first: three tiles along, so its west end touches the first's east end. */
        private static final BlockPos SECOND = new BlockPos(3, 1, 0);

        BoilersPassWaterAlongTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, FIRST, ModBlocks.BOILER.get());
            place(helper, SECOND, ModBlocks.BOILER.get());
            BoilerBlockEntity first = helper.getBlockEntity(FIRST, BoilerBlockEntity.class);
            BoilerBlockEntity second = helper.getBlockEntity(SECOND, BoilerBlockEntity.class);
            insert(first.fuelAccess(), Items.COAL, 1);
            insert(second.fuelAccess(), Items.COAL, 1);
            keepWatered(helper, FIRST);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(second.water() > 0,
                        "the second boiler in a row got no water through the first");
                helper.assertTrue(second.steam() > 0,
                        "the second boiler in a row made no steam, so a row is fed at one end only");
                helper.succeed();
            });
        }

    }

    /**
     * An accumulator is four blocks, one handler, marked as a battery, and moves forty a tick each way.
     *
     * <p>The marker is the assertion that matters: without it the network would treat a battery as
     * a machine and a generator at once, and {@code accumulators_do_not_feed_each_other} below is
     * what that would break. The rate is Factorio's 300 kW at the pack's ratio, and it is the
     * handler's per-call limit, which is a per-tick limit only because the network asks once.
     */
    public static class AccumulatorIsABufferTest extends PackGameTest {

        private static final BlockPos ACCUMULATOR = new BlockPos(0, 1, 0);

        AccumulatorIsABufferTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, ACCUMULATOR, ModBlocks.ACCUMULATOR.get());
            helper.assertValueEqual(AccumulatorShape.SHAPE.width(), 2, "tiles across");
            helper.assertValueEqual(AccumulatorShape.SHAPE.depth(), 2, "tiles deep");

            int entities = 0;
            EnergyHandler first = null;
            for (int part = 0; part < AccumulatorShape.SHAPE.cellCount(); part++) {
                BlockPos cell = AccumulatorShape.SHAPE.cellPos(ACCUMULATOR, part, Direction.NORTH);
                helper.assertBlockPresent(ModBlocks.ACCUMULATOR.get(), cell);
                if (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null) {
                    entities++;
                }
                EnergyHandler handler = helper.getLevel()
                        .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(cell), null);
                helper.assertTrue(handler != null, "no energy capability at cell " + part);
                helper.assertTrue(handler instanceof EnergyBuffer,
                        "an accumulator's handler is not marked as a battery, so the network would "
                                + "fill it as a machine and drain it as a generator");
                if (first == null) {
                    first = handler;
                } else {
                    helper.assertTrue(handler == first,
                            "cell " + part + " hands out a different handler - the network would "
                                    + "count one accumulator as several");
                }
            }
            helper.assertValueEqual(entities, 1, "block entities in one accumulator");
            helper.assertValueEqual((int) first.getCapacityAsLong(), AccumulatorBlockEntity.CAPACITY,
                    "what an accumulator holds");

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(first.insert(1000, transaction), AccumulatorBlockEntity.RATE,
                        "what an accumulator takes in one go");
                helper.assertValueEqual(first.extract(1000, transaction), AccumulatorBlockEntity.RATE,
                        "what an accumulator gives in one go");
            }
            helper.succeed();
        }

    }

    /**
     * With an engine on the network and nothing to feed, the surplus goes into the accumulator, at
     * the accumulator's rate.
     *
     * <p>Factorio's first half: a battery takes what the generators leave over. Nothing else wants
     * power here, so all of the engine's output is surplus, and the accumulator's own rate is what
     * limits it - the engine makes three times as much.
     */
    public static class AccumulatorChargesFromSurplusTest extends PackGameTest {

        private static final BlockPos POLE = new BlockPos(2, 1, 0);
        /** East of the pole, inside its area, clear of the engine to the west. */
        private static final BlockPos ACCUMULATOR = new BlockPos(3, 1, 1);

        AccumulatorChargesFromSurplusTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, ACCUMULATOR, ModBlocks.ACCUMULATOR.get());

            helper.runAfterDelay(120, () -> {
                AccumulatorBlockEntity accumulator =
                        helper.getBlockEntity(ACCUMULATOR, AccumulatorBlockEntity.class);
                int stored = accumulator.energyStored();
                helper.assertTrue(stored >= AccumulatorBlockEntity.RATE * 60,
                        "an accumulator beside a running engine holds " + stored
                                + " after six seconds - the surplus is not reaching it");
                helper.assertTrue(stored <= AccumulatorBlockEntity.RATE * 120,
                        "an accumulator charged faster than its rate: " + stored);
                helper.assertTrue(accumulator.flow() > 0, "a charging accumulator reads as idle");
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.bufferCount(), 1, "accumulators the network counts");
                helper.assertValueEqual(network.endpointCount(), 2, "machines on the network, accumulator included");
                helper.succeed();
            });
        }

    }

    /**
     * Two accumulators, one full and one empty, on a network with nothing else: nothing moves,
     * and the network sleeps.
     */
    public static class AccumulatorsDoNotFeedEachOtherTest extends PackGameTest {

        private static final BlockPos POLE = new BlockPos(0, 1, 0);
        private static final BlockPos FULL = new BlockPos(1, 1, -2);
        private static final BlockPos EMPTY = new BlockPos(-2, 1, 1);

        AccumulatorsDoNotFeedEachOtherTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, POLE, ModBlocks.SMALL_ELECTRIC_POLE.get());
            place(helper, FULL, ModBlocks.ACCUMULATOR.get());
            place(helper, EMPTY, ModBlocks.ACCUMULATOR.get());
            AccumulatorBlockEntity full = helper.getBlockEntity(FULL, AccumulatorBlockEntity.class);
            AccumulatorBlockEntity empty = helper.getBlockEntity(EMPTY, AccumulatorBlockEntity.class);
            full.setStored(AccumulatorBlockEntity.CAPACITY);

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(full.energyStored(), AccumulatorBlockEntity.CAPACITY,
                        "charge of the full accumulator - it fed the empty one");
                helper.assertValueEqual(empty.energyStored(), 0,
                        "charge of the empty accumulator - it took from the full one");
                PowerNetwork network = requireNetwork(helper, POLE, "the pole has no network");
                helper.assertValueEqual(network.bufferCount(), 2, "accumulators the network counts");
                helper.assertFalse(grid(helper).isActive(network),
                        "a network of two idle accumulators is still being ticked every tick");
                helper.succeed();
            });
        }

    }
}
