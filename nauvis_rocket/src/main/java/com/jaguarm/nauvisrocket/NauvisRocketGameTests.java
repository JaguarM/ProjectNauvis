package com.jaguarm.nauvisrocket;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisrocket.registry.ModBlocks;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.silo.Launch;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;
import com.jaguarm.nauvisrocket.silo.RocketSiloMenu;
import com.jaguarm.nauvisrocket.silo.RocketSiloShape;
import com.jaguarm.nauvisrocket.silo.RocketSiloStatus;
import com.jaguarm.nauvislib.test.GameTests;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests that run inside a real server, headless: the silo stands, builds a rocket part out of
 * what it is fed, launches when it has a rocket and a satellite, and sleeps when it has nothing
 * to do.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}, which puts every mod in the pack on one
 * classpath, or {@code :nauvis_rocket:runGameTestServer} for this mod alone.
 */
public final class NauvisRocketGameTests {

    private NauvisRocketGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisRocket.MODID, modEventBus);

        // The silo is the size Factorio made it, with its block entity in the middle of the pad.
        tests.add("a_silo_is_a_hundred_and_thirty_five_blocks", 20, PADDING, helper -> {
            placeSilo(helper);
            int blocks = 0;
            for (BlockPos pos : RocketSiloShape.SHAPE.positions(helper.absolutePos(SILO), net.minecraft.core.Direction.NORTH)) {
                if (helper.getLevel().getBlockState(pos).is(ModBlocks.ROCKET_SILO.get())) {
                    blocks++;
                }
            }
            helper.assertValueEqual(blocks, RocketSiloShape.CELL_COUNT, "blocks of silo placed");
            helper.assertValueEqual(RocketSiloShape.SHAPE.width(), 9, "the silo's width");
            helper.assertValueEqual(RocketSiloShape.SHAPE.depth(), 9, "the silo's depth");
            helper.succeed();
        });

        // Fed one rocket part's worth and charged, a silo builds one rocket part into its rocket in
        // the recipe's three seconds, and spends the ingredients doing it.
        tests.add("a_silo_builds_a_rocket_part", 120, PADDING, helper -> {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            ResourceHandler<ItemResource> view = silo.automationView();
            helper.assertValueEqual(insert(view, item(helper, "nauvis_materials:low_density_structure"), 10), 10,
                    "low density structures accepted");
            helper.assertValueEqual(insert(view, item(helper, "nauvis_materials:rocket_control_unit"), 10), 10,
                    "rocket control units accepted");
            helper.assertValueEqual(insert(view, item(helper, "nauvis_materials:rocket_fuel"), 10), 10,
                    "rocket fuel accepted");
            // One ingredient a slot, in the recipe's order, so a belt of one thing cannot fill the machine.
            helper.assertValueEqual(silo.inventory().getAmountAsInt(0), 10, "low density structures in the first slot");
            helper.assertValueEqual(silo.inventory().getAmountAsInt(1), 10, "rocket control units in the second slot");
            helper.assertValueEqual(silo.inventory().getAmountAsInt(2), 10, "rocket fuel in the third slot");
            helper.assertValueEqual(insert(view, item(helper, "nauvis_materials:low_density_structure"), 200), 10,
                    "more low density structures accepted: automation stops at twice a part's worth and the other slots refuse it");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(silo.inventory().insert(0,
                        ItemResource.of(item(helper, "nauvis_materials:low_density_structure")), 200, transaction), 30,
                        "low density structures a hand may add past the limit: up to the stack of fifty");
                // Not committed: the part below is built from the twenty an inserter left.
            }
            helper.assertValueEqual(insert(view, Items.STICK, 1), 0, "sticks accepted by a silo");

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(silo.status(), RocketSiloStatus.BUILDING, "status while building");
                helper.assertValueEqual(silo.parts(), 0, "parts before the recipe's three seconds are up");
            });
            // Three seconds is sixty ticks; the first tick's check and a tick of slack.
            helper.runAfterDelay(70, () -> {
                helper.assertValueEqual(silo.parts(), 1, "rocket parts built");
                // One part's worth taken from each slot: the first held two parts' worth and keeps one.
                helper.assertValueEqual(silo.inventory().getAmountAsInt(0), 10, "low density structures left after one part");
                helper.assertValueEqual(silo.inventory().getAmountAsInt(1), 0, "rocket control units left after one part");
                helper.assertValueEqual(silo.inventory().getAmountAsInt(2), 0, "rocket fuel left after one part");
                helper.assertTrue(silo.energyStored() < RocketSiloBlockEntity.ENERGY_CAPACITY,
                        "the silo built a part for nothing");
                helper.succeed();
            });
        });

        // A silo with a complete rocket and a satellite in its slot launches on its own: the
        // satellite goes, the rocket empties, a thousand space science packs are owed and the
        // output slot fills with the first stack of them, and the advancement is the game's end.
        tests.add("a_silo_launches_a_satellite", RocketSiloBlockEntity.LAUNCH_TICKS + 60, PADDING, helper -> {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            silo.loadRocket(100);
            helper.assertValueEqual(insert(silo.automationView(), ModItems.SATELLITE.get(), 2), 1,
                    "satellites accepted: a rocket carries one");

            helper.runAfterDelay(5, () -> helper.assertValueEqual(silo.status(), RocketSiloStatus.LAUNCHING,
                    "status once the rocket has its satellite"));
            helper.runAfterDelay(RocketSiloBlockEntity.LAUNCH_TICKS + 10, () -> {
                helper.assertValueEqual(silo.launches(), 1, "rockets launched");
                helper.assertValueEqual(silo.parts(), 0, "parts left in the rocket after the launch");
                helper.assertValueEqual(silo.inventory().getAmountAsInt(RocketSiloBlockEntity.SATELLITE_SLOT), 0,
                        "the satellite stayed behind");
                int inOutput = silo.inventory().getAmountAsInt(RocketSiloBlockEntity.OUTPUT_SLOT);
                helper.assertTrue(silo.inventory().getResource(RocketSiloBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.SPACE_SCIENCE_PACK.get()), "the output holds something other than space science");
                helper.assertValueEqual(inOutput + silo.owed(), 1000, "space science sent back, in the output and owed");
                helper.assertTrue(inOutput > 0, "none of the science reached the output slot");

                Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
                helper.assertTrue(Launch.award((ServerPlayer) player), "the launch advancement could not be awarded");
                helper.succeed();
            });
        });

        // A silo with nothing in its slots schedules nothing: non-negotiable #5.
        tests.add("a_silo_with_nothing_to_build_sleeps", 40, PADDING, helper -> {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(silo.status(), RocketSiloStatus.NO_INGREDIENTS, "status of an empty silo");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                        helper.absolutePos(SILO), ModBlocks.ROCKET_SILO.get()), "an empty silo is still ticking");
                helper.succeed();
            });
        });

        // With automatic launch off, a complete rocket with a satellite waits; the Launch button - the
        // menu's button, as the screen presses it - sends it up, and the satellite goes with it. A
        // second rocket launched by the button with no satellite goes too, and nothing comes back.
        tests.add("a_silo_launches_by_the_button", 2 * RocketSiloBlockEntity.LAUNCH_TICKS + 100, PADDING, helper -> {
            RocketSiloBlockEntity silo = placeSilo(helper);
            charge(silo);
            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
            RocketSiloMenu menu = (RocketSiloMenu) silo.createMenu(1, player.getInventory(), player);

            helper.assertTrue(silo.autoLaunch(), "a new silo does not launch on its own");
            helper.assertTrue(menu.clickMenuButton(player, RocketSiloMenu.BUTTON_AUTO_LAUNCH), "the toggle did nothing");
            helper.assertFalse(silo.autoLaunch(), "the toggle left automatic launch on");

            silo.loadRocket(100);
            helper.assertValueEqual(insert(silo.automationView(), ModItems.SATELLITE.get(), 1), 1, "a satellite accepted");
            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(silo.status(), RocketSiloStatus.READY, "a complete rocket with automatic launch off");
                helper.assertValueEqual(silo.launches(), 0, "rockets launched before the button");
                helper.assertTrue(menu.clickMenuButton(player, RocketSiloMenu.BUTTON_LAUNCH), "the button did nothing");
            });
            helper.runAfterDelay(25, () -> helper.assertValueEqual(silo.status(), RocketSiloStatus.LAUNCHING,
                    "status after the button"));
            helper.runAfterDelay(RocketSiloBlockEntity.LAUNCH_TICKS + 30, () -> {
                helper.assertValueEqual(silo.launches(), 1, "rockets launched by the button");
                helper.assertValueEqual(silo.inventory().getAmountAsInt(RocketSiloBlockEntity.SATELLITE_SLOT), 0,
                        "the satellite stayed behind");
                int came = silo.inventory().getAmountAsInt(RocketSiloBlockEntity.OUTPUT_SLOT) + silo.owed();
                helper.assertValueEqual(came, 1000, "space science from a rocket launched by hand with its satellite");

                // An empty rocket, by the button: it goes, and nothing comes back.
                silo.loadRocket(100);
                silo.requestLaunch();
            });
            helper.runAfterDelay(RocketSiloBlockEntity.LAUNCH_TICKS + 40, () -> helper.assertValueEqual(
                    silo.status(), RocketSiloStatus.LAUNCHING, "status of an empty rocket sent by the button"));
            helper.runAfterDelay(2 * RocketSiloBlockEntity.LAUNCH_TICKS + 60, () -> {
                helper.assertValueEqual(silo.launches(), 2, "rockets launched");
                int came = silo.inventory().getAmountAsInt(RocketSiloBlockEntity.OUTPUT_SLOT) + silo.owed();
                helper.assertValueEqual(came, 1000, "space science after an empty launch: no more than before");
                helper.succeed();
            });
        });

        // What an inserter fills the slots to survives a save: twice a part's worth of a ten-stack
        // ingredient, and a launch's science in the output.
        tests.add("a_silo_keeps_its_slots_through_a_save", 20, PADDING, helper -> {
            RocketSiloBlockEntity silo = placeSilo(helper);
            Item unit = item(helper, "nauvis_materials:rocket_control_unit");
            helper.assertValueEqual(insert(silo.automationView(), unit, 20), 20,
                    "two parts' worth of rocket control units accepted");
            try (Transaction transaction = Transaction.openRoot()) {
                silo.inventory().insert(RocketSiloBlockEntity.OUTPUT_SLOT,
                        ItemResource.of(ModItems.SPACE_SCIENCE_PACK.get()), 1000, transaction);
                transaction.commit();
            }
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            CompoundTag saved = silo.saveWithoutMetadata(registries);
            RocketSiloBlockEntity reloaded = new RocketSiloBlockEntity(silo.getBlockPos(), silo.getBlockState());
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
            helper.assertValueEqual(reloaded.inventory().getAmountAsInt(1), 20,
                    "rocket control units after a save and reload; saved as " + saved.get("Inventory"));
            helper.assertValueEqual(reloaded.inventory().getAmountAsInt(RocketSiloBlockEntity.OUTPUT_SLOT), 1000,
                    "space science after a save and reload; saved as " + saved.get("Inventory"));
            helper.succeed();
        });
    }

    /** Where every test puts its silo: one block up, so it is not inside the floor. */
    private static final BlockPos SILO = new BlockPos(0, 1, 0);

    /**
     * How much empty world to leave around each test. A silo is nine blocks across and its
     * anchor is in the middle, so four each way is the machine and the rest is room.
     */
    private static final int PADDING = 24;

    /** A whole silo, anchored here. */
    private static RocketSiloBlockEntity placeSilo(GameTestHelper helper) {
        Multiblock.place(ModBlocks.ROCKET_SILO.get(), helper.getLevel(), helper.absolutePos(SILO),
                ModBlocks.ROCKET_SILO.get().defaultBlockState());
        return helper.getBlockEntity(SILO, RocketSiloBlockEntity.class);
    }

    /** Fills the silo's buffer the way a power pole would. */
    private static void charge(RocketSiloBlockEntity silo) {
        try (Transaction transaction = Transaction.openRoot()) {
            silo.gridView().insert(RocketSiloBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    /** An item by id, so a test can name another mod's item without a compile-time dependency. */
    private static Item item(GameTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
        return item;
    }

    /** Puts items in the way an inserter will: through the published capability view. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

}
