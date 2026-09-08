package com.jaguarm.nauvisresearch;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.transfer.MachinePower;
import java.util.List;
import java.util.Set;

import com.jaguarm.nauvisresearch.lab.LabBlock;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;
import com.jaguarm.nauvisresearch.lab.LabShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisresearch.registry.ModBlocks;
import com.jaguarm.nauvisresearch.registry.ModItems;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.ResearchState;
import com.jaguarm.nauvisresearch.research.Technology;
import com.jaguarm.nauvisresearch.research.TechnologyLayout;
import com.jaguarm.facrafting.progress.MiningListeners;
import com.jaguarm.facrafting.queue.CraftTicker;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Tests that run inside a real server, headless, reporting pass or fail on exit. */
public final class NauvisResearchGameTests {

    private NauvisResearchGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisResearch.MODID, modEventBus);

        // A lab is ten blocks in a 3x3, with one block entity, and every block knows where it is.
        tests.add("lab_is_ten_blocks", 20, PADDING, helper -> {
            LabBlock block = ModBlocks.LAB.get();
            placeLab(helper);

            helper.assertValueEqual(LabShape.SHAPE.width(), 3, "tiles across");
            helper.assertValueEqual(LabShape.SHAPE.depth(), 3, "tiles deep");
            helper.assertValueEqual(LabShape.SHAPE.cellCount(), 10, "blocks in a lab");

            int entities = 0;
            for (int part = 0; part < LabShape.SHAPE.cellCount(); part++) {
                BlockPos cell = LabShape.SHAPE.cellPos(LAB, part, Direction.NORTH);
                helper.assertBlockPresent(block, cell);
                helper.assertValueEqual(
                        Multiblock.anchorPos(block, helper.getBlockState(cell),
                                helper.absolutePos(cell)),
                        helper.absolutePos(LAB), "anchor as seen from " + cell);
                if (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null) {
                    entities++;
                }
            }
            helper.assertValueEqual(entities, 1, "block entities in one lab");

            // Fed from a far corner, which is the point of having a footprint: an inserter can
            // stand anywhere along a lab rather than at one privileged block.
            ResourceHandler<ItemResource> corner = Capabilities.Item.BLOCK.getCapability(
                    helper.getLevel(), helper.absolutePos(LAB.offset(-1, 0, -1)), null, null,
                    Direction.WEST);
            helper.assertTrue(corner != null, "no item capability at a lab's corner");
            helper.assertValueEqual(insert(corner, ModItems.SCIENCE_PACK_1.get(), 3), 3,
                    "packs taken at the far corner");
            helper.succeed();
        });

        // Given packs, power and something to research, a lab finishes a unit on time and the world
        // hears about it.
        //
        // Both halves matter and they used to be one. The lab's own count is a readout for whoever
        // is looking at that machine; the world's is the research, and it is what makes twelve labs
        // worth more than one. A lab that counted its own cycles and told nobody would pass every
        // assertion this test made before the tree existed.
        tests.add("lab_researches", 300, PADDING, helper -> {
            // Automation: ten units of red science at ten seconds each, and Factorio's first
            // technology. The numbers come from the generated tree rather than from here, so a
            // regenerated dump moves the test with it instead of breaking it.
            research(helper, "automation");
            int unitTicks = technology(helper, "automation").ticksPerUnit();

            LabBlockEntity lab = placeLab(helper);
            charge(lab);
            helper.assertValueEqual(insert(lab.automationView(),
                    ModItems.SCIENCE_PACK_1.get(), 2), 2, "packs accepted");

            helper.assertValueEqual(lab.cycles(), 0, "research before it has done any");

            // One tick short: still working, and the pack not yet spent.
            helper.runAfterDelay(unitTicks - 1, () -> {
                helper.assertValueEqual(lab.cycles(), 0,
                        "research a tick before the unit is due");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 2,
                        "packs a tick before the unit is due");
            });

            helper.runAfterDelay(unitTicks + 2, () -> {
                helper.assertValueEqual(lab.cycles(), 1, "research after one unit");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 1,
                        "packs left after one unit");
                helper.assertTrue(lab.energyStored() < LabBlockEntity.ENERGY_CAPACITY,
                        "a lab that researched without spending any power");
                helper.assertTrue(Research.state(helper.getLevel().getServer()).units() >= 1,
                        "the world heard nothing about a unit the lab finished");
                helper.succeed();
            });
        });

        // With packs but no power, a lab does nothing at all.
        //
        // Worth its own test because the failure is silent in the wrong direction: a lab that
        // researched on nothing would be strictly better than one with a wire to it, and nobody
        // would report that as a bug.
        tests.add("lab_needs_power", 60, PADDING, helper -> {
            // Everything but the wire: a technology chosen and the packs for it in the slots.
            // Without the research this would pass for the wrong reason.
            research(helper, "automation");

            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 4);

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(lab.progress(), 0, "progress on an unpowered lab");
                helper.assertValueEqual(lab.cycles(), 0, "research done on nothing");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 4,
                        "packs an unpowered lab ate");
                helper.succeed();
            });
        });

        // An idle lab schedules nothing, and power arriving is what wakes it.
        //
        // Non-negotiable #5, and the half that is easy to get wrong: a lab that stopped for want of
        // power is not ticking, so it cannot notice the grid coming back by itself. The wake comes
        // from MachinePower, on whatever thread of control filled the buffer. Delete that callback
        // and this is the test that goes red.
        tests.add("lab_sleeps", 60, PADDING, helper -> {
            LabBlockEntity lab = placeLab(helper);

            // Empty and unpowered: after the tick it asks for on placement, it should stop.
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(isScheduled(helper), "an empty lab is still ticking");

                insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 1);
                helper.assertTrue(isScheduled(helper), "a pack arriving did not wake the lab");

                helper.runAfterDelay(5, () -> {
                    // Packs but no power: it stops again, and only energy can restart it.
                    helper.assertFalse(isScheduled(helper),
                            "a lab with no power is still ticking");

                    charge(lab);
                    helper.assertTrue(isScheduled(helper),
                            "power arriving did not wake the lab - see MachinePower");
                    helper.succeed();
                });
            });
        });

        // A hopper cannot pull the science back out of a lab.
        //
        // A lab has no output, so its automation view has to be one-way. Without that rule a hopper
        // put under a lab to feed it would drain it instead, which looks like the lab eating nothing.
        tests.add("lab_keeps_its_packs", 40, PADDING, helper -> {
            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 5);

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(
                        lab.automationView().extract(
                                ItemResource.of(ModItems.SCIENCE_PACK_1.get()), 5, transaction),
                        0, "packs taken back out of a lab");
                transaction.commit();
            }
            helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 5, "packs still in the lab");
            helper.succeed();
        });

        // Break any one of the ten and the whole lab comes down, giving back one lab and its packs.
        tests.add("lab_breaks_as_one", 40, PADDING, helper -> {
            LabBlockEntity lab = placeLab(helper);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 7);

            // A corner, which has no block entity and no loot entry of its own.
            helper.getLevel().destroyBlock(helper.absolutePos(LAB.offset(-1, 0, -1)), true);

            helper.runAfterDelay(2, () -> {
                for (int part = 0; part < LabShape.SHAPE.cellCount(); part++) {
                    helper.assertBlockPresent(Blocks.AIR,
                            LabShape.SHAPE.cellPos(LAB, part, Direction.NORTH));
                }
                helper.assertItemEntityCountIs(ModItems.LAB.get(), LAB, 4.0, 1);
                helper.assertItemEntityCountIs(ModItems.SCIENCE_PACK_1.get(), LAB, 4.0, 7);
                helper.succeed();
            });
        });

        // The generated tree is on disk, loads, and still says what Factorio says.
        //
        // checkTechnologies already diffs the files against the generator, so what this
        // adds is that they load: a datapack registry entry whose codec rejects it does not
        // fail the build, it fails at world load with one line in a log nobody reads, and the
        // research list is then quietly short.
        //
        // Automation is the one asserted by hand because it is the technology this pack's players
        // meet first - ten units of red science at ten seconds, unlocking the assembling machine and
        // the long-handed inserter - and because a mistake in it would be a mistake in every number
        // the generator produces.
        tests.add("technology_tree_loads", 20, PADDING, helper -> {
            var registry = ModTechnologies.registry(helper.getLevel().registryAccess());
            helper.assertTrue(registry.size() >= 20,
                    "only " + registry.size() + " technologies loaded");

            // The first one, and the only kind that can start an empty world: no prerequisites,
            // no cost, and a trigger naming something a player can make with a pickaxe.
            Technology steam = technology(helper, "steam_power");
            helper.assertTrue(steam != null, "no nauvis_research:steam_power in the tree");
            helper.assertTrue(steam.isTriggered(), "steam power is not triggered");
            helper.assertTrue(steam.prerequisites().isEmpty(),
                    "steam power has prerequisites; nothing could ever start it");
            helper.assertValueEqual(steam.trigger().orElseThrow().target(),
                    Identifier.withDefaultNamespace("iron_ingot"), "what steam power watches for");
            helper.assertValueEqual(steam.trigger().orElseThrow().count(), 50,
                    "how many steam power watches for");
            helper.assertTrue(steam.unlocks().contains(recipe("nauvis_power", "boiler")),
                    "steam power does not unlock the boiler");

            // And one paid for the ordinary way, which is what a lab is for.
            Technology automation = technology(helper, "automation");
            helper.assertTrue(automation != null, "no nauvis_research:automation in the tree");
            helper.assertFalse(automation.isTriggered(), "automation is triggered; it has a cost");
            helper.assertValueEqual(automation.units(), 10, "units of automation");
            helper.assertValueEqual(automation.ticksPerUnit(), 200, "ticks a unit of automation");
            helper.assertValueEqual(automation.packs().size(), 1, "kinds of pack automation wants");
            helper.assertValueEqual(automation.packs().get(0),
                    Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "science_pack_1"),
                    "the pack automation wants");
            helper.assertTrue(
                    automation.unlocks().contains(recipe("nauvis_machines", "assembling_machine_1")),
                    "automation does not unlock the assembling machine");

            // Every technology is one or the other. Neither means it can never be finished.
            for (Holder.Reference<Technology> holder : registry.listElements().toList()) {
                helper.assertTrue(
                        holder.value().isTriggered() != (holder.value().units() > 0),
                        holder.key().identifier() + " has both a cost and a trigger, or neither");
            }

            // The tree is a graph and every edge has to land somewhere. A dangling prerequisite
            // is a technology nobody can ever start, and it shows in the list looking normal.
            for (Holder.Reference<Technology> holder : registry.listElements().toList()) {
                for (ResourceKey<Technology> prerequisite : holder.value().prerequisites()) {
                    helper.assertTrue(registry.get(prerequisite).isPresent(),
                            holder.key().identifier() + " needs " + prerequisite.identifier()
                                    + ", which is not in the tree");
                }
            }
            helper.succeed();
        });

        // Finishing a technology unlocks its recipes, and says so loudly enough for an open panel.
        //
        // Two assertions, and the second is the one this pack has been bitten by twice. An unlock
        // that is only ever tested by asking a player who already has it is not tested: what actually
        // happens is that a technology completes while the crafting panel is open, and the
        // newly unlocked recipe has to appear without the screen being closed and reopened. Nothing
        // headless can look at a panel, but the mechanism it watches is
        // Research#revision, and this asserts that it moves.
        //
        // Steel processing rather than automation, deliberately: research is per-world and the
        // world is shared with every other test in the run, so completing a technology some other test
        // has made its current research would clear that research out from under it. See
        // #research.
        tests.add("research_unlocks_a_recipe", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> steel = ModTechnologies.key("steel_processing");
            ResourceKey<Recipe<?>> steelPlate = recipe("nauvis_materials", "steel_plate");
            ResourceKey<Recipe<?>> belt = recipe("nauvis_logistics", "transport_belt");

            Research.state(server).forget(steel);
            Research.invalidate();

            helper.assertFalse(Research.isUnlocked(server, steelPlate),
                    "steel plate is craftable before steel processing is researched");

            // A recipe no technology mentions is not gated by anything. That is Factorio's rule
            // and it is what makes a belt craftable in the first minute; a lock that defaulted
            // to closed would leave a new world with nothing to build at all.
            helper.assertTrue(Research.isUnlocked(server, belt),
                    "a belt, which no technology unlocks, is locked");

            int before = Research.revision();
            Research.state(server).complete(steel);
            Research.changedExternally(server);

            helper.assertTrue(Research.isUnlocked(server, steelPlate),
                    "steel plate is still locked after researching steel processing");
            helper.assertTrue(Research.revision() != before,
                    "the revision did not move, so an open crafting panel would never notice");
            helper.succeed();
        });

        // A lab eats the packs the technology asks for and nothing else.
        //
        // Before there was a tree, a cycle consumed one of every kind of item the lab happened to
        // be holding, which was right when the only item that could be in there was a science pack.
        // It is wrong now, and wrong in the expensive direction: a lab fed anything at all would eat
        // it. This puts a redstone in a lab researching automation and expects it to sit there.
        tests.add("lab_ignores_the_wrong_packs", 120, PADDING, helper -> {
            research(helper, "automation");

            LabBlockEntity lab = placeLab(helper);
            charge(lab);
            insert(lab.automationView(), Items.REDSTONE, 4);

            // Well past a unit of automation would take if it were counting this as science.
            helper.runAfterDelay(60, () -> {
                helper.assertValueEqual(lab.cycles(), 0, "research done on redstone");
                helper.assertValueEqual(lab.progress(), 0, "progress on a lab with no packs");
                helper.assertValueEqual(lab.inventory().getAmountAsInt(0), 4,
                        "redstone a lab ate");
                helper.succeed();
            });
        });

        tests.add("the_crafting_gate_is_installed", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> logistics = ModTechnologies.key("logistics");
            ResourceKey<Recipe<?>> splitter = recipe("nauvis_logistics", "splitter");
            ResourceKey<Recipe<?>> belt = recipe("nauvis_logistics", "transport_belt");

            Research.state(server).forget(logistics);
            Research.changedExternally(server);

            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

            helper.assertFalse(RecipeLocks.isOpen(),
                    "no recipe lock is installed - see NauvisResearch's ModList check");

            // The other half of the same wiring, and the half that fails silently: without it a
            // craft in Facrafting's panel tells research nothing, so the technologies triggered by
            // crafting - red science among them - could never finish. Everything else would keep
            // working, which is why it is asserted rather than assumed.
            helper.assertTrue(com.jaguarm.facrafting.queue.CraftListeners.installed() > 0,
                    "no craft listener is installed, so crafting cannot finish a triggered "
                            + "technology - see FacraftingLock.install");
            helper.assertFalse(RecipeLocks.isUnlocked(player, splitter),
                    "the splitter is craftable before Logistics is researched");
            helper.assertTrue(RecipeLocks.isUnlocked(player, belt),
                    "a belt, which no technology unlocks, is locked");

            Research.state(server).complete(logistics);
            Research.changedExternally(server);
            helper.assertTrue(RecipeLocks.isUnlocked(player, splitter),
                    "the splitter is still locked after researching Logistics");

            // Put it back: research is per-world and this world is shared with every other test.
            Research.state(server).forget(logistics);
            Research.changedExternally(server);
            helper.succeed();
        });

        // The pack's own gating, and the guard that keeps it from locking a world out of research.
        //
        // <h2>Both halves are the point</h2>
        //
        // The electric mining drill is the one that was reported - craftable on a fresh world,
        // and it should not have been. It has a technology of its own now.
        //
        // The other half is the guard, and it is the half worth keeping. Gating is one edit away
        // from a world that can never research anything: what is left free has to be enough to mine by
        // hand, smelt, and reach the first trigger. The generator walks that graph and refuses a tree
        // it cannot bootstrap; this asserts the same thing at run time, against the recipes
        // actually loaded, because the generator's answer and the server's could drift and only one of
        // them is the one a player meets.
        tests.add("research_gates_the_early_machines", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> drillTech = ModTechnologies.key("electric_mining_drill");
            ResourceKey<Recipe<?>> drill = recipe("nauvis_mining", "electric_mining_drill");

            Research.state(server).forget(drillTech);
            Research.changedExternally(server);

            helper.assertFalse(Research.isUnlocked(server, drill),
                    "the electric drill is craftable with nothing researched");

            // What a new world can still do, and it has to be enough to start: mine by hand, put
            // ore in a furnace, and make the handful of things nothing gates. If one of these were
            // ever gated the world could never reach its first trigger - and every other test here
            // would still pass, because they all begin with a lab already placed.
            for (String[] free : new String[][] {
                    {"nauvis_logistics", "transport_belt"}, {"nauvis_logistics", "burner_inserter"},
                    {"nauvis_logistics", "chest"}, {"nauvis_machines", "stone_furnace"},
                    {"nauvis_materials", "iron_gear_wheel"},
                    {"nauvis_materials", "iron_ingot"}}) {
                helper.assertTrue(Research.isUnlocked(server, recipe(free[0], free[1])),
                        free[0] + ":" + free[1] + " is gated, but nothing in the tree unlocks it, "
                                + "so a new world could never craft it at all");
            }

            Research.state(server).complete(drillTech);
            Research.changedExternally(server);
            helper.assertTrue(Research.isUnlocked(server, drill),
                    "the electric drill is still locked after researching it");

            Research.state(server).forget(drillTech);
            Research.changedExternally(server);
            helper.succeed();
        });

        // Making something finishes the technology that was waiting for it.
        //
        // <h2>This is what makes the opening possible at all</h2>
        //
        // The first technologies have no cost and no science: craft fifty iron plates and
        // the boiler and the steam engine are yours. Without that a new world would have to build a
        // lab to research the things a lab is built out of, which is a circle - and the pack would be
        // back to handing everything over at the start, which is the complaint this whole tree exists
        // to answer.
        //
        // It is also the one part of research that is not driven by a machine, so nothing
        // else here would catch it breaking. A lab test cannot: a triggered technology never reaches
        // a lab.
        //
        // Steam power is used deliberately - it has no prerequisites, so this test needs no setup
        // beyond forgetting it, and nothing else in the run sets it as current research.
        tests.add("a_trigger_finishes_research", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResourceKey<Technology> steam = ModTechnologies.key("steam_power");
            ResourceKey<Recipe<?>> boiler = recipe("nauvis_power", "boiler");
            Technology.Trigger trigger = technology(helper, "steam_power").trigger().orElseThrow();

            ResearchState state = Research.state(server);
            state.forget(steam);
            state.recordMade(trigger.target(), -state.made(trigger.target()));
            Research.changedExternally(server);

            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler is craftable before steam power");

            // One short. The count is the whole of the condition, so being able to stop one below
            // it is what says the number is read rather than ignored.
            Research.recordMade(helper.getLevel(), trigger.target(), trigger.count() - 1);
            helper.assertFalse(Research.state(server).isCompleted(steam),
                    "steam power finished one item short of its trigger");
            helper.assertFalse(Research.isUnlocked(server, boiler),
                    "the boiler unlocked one item short of steam power's trigger");

            Research.recordMade(helper.getLevel(), trigger.target(), 1);
            helper.assertTrue(Research.state(server).isCompleted(steam),
                    "steam power did not finish when its trigger was met - a new world would have "
                            + "nothing it could ever research");
            helper.assertTrue(Research.isUnlocked(server, boiler),
                    "the boiler is still locked after steam power finished");

            // A lab cannot be pointed at one of these: it finishes by being made, not by science.
            helper.assertFalse(Research.setCurrent(server, steam),
                    "a triggered technology was accepted as a lab's current research");
            helper.succeed();
        });

        // A craft finished in Facrafting's panel reaches the trigger with the item still in it.
        //
        // <h2>The bug this exists for looked exactly like nothing</h2>
        //
        // CraftTicker used to hand the listeners the same ItemStack it had just
        // passed to placeItemBackInInventory - which empties it, slot by slot, as it puts it
        // away. So every listener was told an empty stack, ResearchTriggers dropped it on its
        // first line, and "craft one lab" sat at zero done for ever while the lab itself
        // appeared in the inventory. Nothing logged, nothing failed, and the panel is where this pack
        // does nearly all of its crafting.
        //
        // CraftingGateIsInstalledTest could not catch it: the listener really was
        // installed. What was wrong was what it got handed. So this drives the delivery itself and
        // asserts the world's counter moved - the smallest thing that is true only if the whole seam
        // works.
        //
        // Iron plates rather than a lab, deliberately, and it is the same hazard every test
        // here works around. Research is per-world and shared with every other test in the run: one
        // lab would finish Science pack 1 and clear whatever another test had set as its current
        // research. Steam power's trigger is fifty plates deep, so two of them move a counter and
        // finish nothing - and the count is put back afterwards either way.
        //
        // It is also the only trigger item this mod can reach on its own. The other three belong to
        // sibling mods, and :nauvis_research:runGameTestServer runs this mod alone.
        tests.add("a_panel_craft_counts", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            Technology.Trigger trigger = technology(helper, "steam_power").trigger().orElseThrow();
            Item plate = BuiltInRegistries.ITEM.getValue(trigger.target());
            helper.assertTrue(plate != Items.AIR,
                    "no item is registered as " + trigger.target() + ", so the trigger watches for "
                            + "something nobody can make");

            ResearchState state = Research.state(server);
            int before = state.made(trigger.target());

            // Not a mock *server* player, which is the one a test would reach for first: putting
            // a stack away sends a slot packet, and a mock server player has no connection to
            // send it down. A plain mock in a server level skips the packet and is still on the
            // server, which is all a trigger asks about.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            try {
                CraftTicker.deliver(player, new ItemStack(plate, 2));

                int held = 0;
                for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                    if (stack.is(plate)) {
                        held += stack.getCount();
                    }
                }
                helper.assertTrue(held == 2,
                        "a finished craft put " + held + " items in the player's inventory, not 2");
                helper.assertTrue(Research.state(server).made(trigger.target()) == before + 2,
                        "a craft finished in the panel counted "
                                + (state.made(trigger.target()) - before) + " towards its trigger "
                                + "instead of 2 - a triggered technology can never finish, and "
                                + "nothing says so");
            } finally {
                // Put it back: research is per-world and this world is shared with every other
                // test in the run.
                state.recordMade(trigger.target(), before - state.made(trigger.target()));
                Research.changedExternally(server);
            }
            helper.succeed();
        });
        tests.add("technology_layout_is_sound", TechnologyLayoutTest::new, 20, PADDING);

        // A lab has two module slots, and speed modules in them shorten a unit.
        //
        // The modules are the machines mod's items and this mod does not name it, so the test finds
        // them by id and passes when they are not there - which is the standalone run. With them, two
        // speed modules make a lab two fifths faster: a 600-tick unit is 429 ticks, on top of whatever
        // research speed the world has, and the lab draws half again as much twice over.
        tests.add("lab_takes_modules", 20, PADDING, helper -> {
            Item speed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_machines", "speed_module"));
            LabBlockEntity lab = placeLab(helper);
            helper.assertValueEqual(lab.modules().size(), LabBlockEntity.MODULE_SLOTS, "module slots on a lab");
            if (speed == Items.AIR) {
                // Standalone: no modules to put in. The slots exist, which is what this mod owns.
                helper.succeed();
                return;
            }

            helper.assertFalse(lab.modules().isValid(0, ItemResource.of(Items.REDSTONE)),
                    "a lab's module slot took something that is not a module");
            for (int slot = 0; slot < LabBlockEntity.MODULE_SLOTS; slot++) {
                try (Transaction transaction = Transaction.openRoot()) {
                    helper.assertValueEqual(lab.modules().insert(slot, ItemResource.of(speed), 2, transaction), 1,
                            "speed modules taken by one slot - a slot holds one module");
                    transaction.commit();
                }
            }

            Technology engine = technology(helper, "electric_engine");
            int bare = LabBlockEntity.cycleTicksFor(engine, helper.getLevel());
            int modded = LabBlockEntity.cycleTicksFor(engine, helper.getLevel(), lab.modules().effect());
            helper.assertValueEqual(modded, (int) Math.round(bare / 1.4),
                    "ticks a unit takes with two speed modules, against " + bare + " without");
            helper.succeed();
        });

        tests.batch("alone");
        tests.add("research_command_moves_the_tree", ResearchCommandTest::new, 20, PADDING);

        // Research put down and picked up again is where it was left.
        //
        // This is the bug a playtest found, and it was a decision rather than an accident: units
        // were one counter beside the current technology and switching zeroed it, which was written
        // down as Factorio's rule and is not - Factorio keeps a per-technology progress and hands it
        // back when you return. A player who clicked something else to read its tooltip could throw
        // away an hour of labs, and nothing said so before or after.
        //
        // Alone in its batch, because there is one current research per world and every lab test in
        // the other batch is pointing it at something of its own.
        tests.add("research_keeps_its_progress", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> automation = ModTechnologies.key("automation");
            ResourceKey<Technology> logistics = ModTechnologies.key("logistics");

            // Both cost ten units of red science and neither is anywhere near that after two, so
            // nothing here completes and the numbers stay comparable.
            research(helper, "automation");
            Research.addUnit(helper.getLevel());
            Research.addUnit(helper.getLevel());
            helper.assertValueEqual(state.units(), 2, "units on the research being worked on");

            research(helper, "logistics");
            helper.assertValueEqual(state.units(), 0, "units on a research just started");
            helper.assertValueEqual(state.units(automation), 2,
                    "units kept on the research the labs were pointed away from");

            Research.addUnit(helper.getLevel());
            helper.assertValueEqual(state.units(logistics), 1, "units on the second research");
            helper.assertValueEqual(state.units(automation), 2,
                    "units on the first research, while a second one is being worked on");

            Research.setCurrent(server, automation);
            helper.assertValueEqual(state.units(), 2, "units picked up where they were left");

            // And finishing one takes it out of the tally rather than leaving it there to be
            // handed back if the technology is ever forgotten and researched again.
            for (int i = state.units(); i < technology(helper, "automation").units(); i++) {
                Research.addUnit(helper.getLevel());
            }
            helper.assertTrue(state.isCompleted(automation), "automation finished");
            helper.assertValueEqual(state.units(automation), 0,
                    "units left over on a finished technology");

            state.forget(automation);
            state.forget(logistics);
            state.setCurrent(null);
            Research.changedExternally(server);
            helper.succeed();
        });

        // A technology's modifiers are summed for the world, and reach a machine through the library.
        //
        // Two research-speed technologies at a fifth and three tenths are half again as fast, and a
        // lab's unit is shorter by exactly that. The inserter capacity line is the other one anything
        // reads: the stack inserter's technology and its first two bonus levels make three, and the
        // ordinary inserter's single bonus arrives with the second level and not before.
        //
        // Asked through Bonuses as well as Research, because the wiring between the
        // two is a line in the mod constructor that nothing else here would notice going missing - and
        // with it gone every inserter in the pack would hold one item for ever, saying nothing.
        //
        // Alone in its batch: it completes chains that run back through automation, which is the
        // technology the lab tests point their labs at. Puts the tree back exactly afterwards.
        tests.add("bonuses_reach_the_world", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            ResourceKey<Technology> current = state.current();
            try {
                helper.assertTrue(Bonuses.installed(),
                        "nothing has installed itself as the world's bonuses - see NauvisResearch");

                state.forget(ModTechnologies.key("research_speed_1"));
                state.forget(ModTechnologies.key("research_speed_2"));
                state.forget(ModTechnologies.key("inserter_capacity_bonus_2"));
                Research.changedExternally(server);
                helper.assertTrue(Research.bonus(server, LabBlockEntity.LABORATORY_SPEED) == 0,
                        "research speed with neither speed technology researched");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 0,
                        "an ordinary inserter's bonus with nothing researched");

                finish(server, state, ModTechnologies.key("research_speed_2"), false);
                Research.changedExternally(server);
                double speed = Research.bonus(server, LabBlockEntity.LABORATORY_SPEED);
                helper.assertTrue(Math.abs(speed - 0.5) < 1e-9,
                        "research speed after both speed technologies: " + speed + ", not 0.5");
                helper.assertTrue(Math.abs(Bonuses.of(helper.getLevel(), LabBlockEntity.LABORATORY_SPEED) - 0.5) < 1e-9,
                        "the library answers differently from research - the hook is not wired");
                Technology engine = technology(helper, "electric_engine");
                helper.assertValueEqual(LabBlockEntity.cycleTicksFor(engine, helper.getLevel()), 400,
                        "ticks a 600-tick unit takes at half again the research speed");

                // Stack inserter, capacity 1, capacity 2: one from each for the stack inserter's
                // hand, and the ordinary inserter's single bonus from the second level only.
                finish(server, state, ModTechnologies.key("inserter_capacity_bonus_1"), false);
                Research.changedExternally(server);
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "bulk-inserter-capacity-bonus"), 2,
                        "stack inserter bonus after its technology and the first capacity level");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 0,
                        "an ordinary inserter's bonus before the second capacity level");

                finish(server, state, ModTechnologies.key("inserter_capacity_bonus_2"), false);
                Research.changedExternally(server);
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "bulk-inserter-capacity-bonus"), 3,
                        "stack inserter bonus after the second capacity level");
                helper.assertValueEqual(Bonuses.count(helper.getLevel(), "inserter-stack-size-bonus"), 1,
                        "an ordinary inserter's bonus after the second capacity level");
            } finally {
                for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                    if (!before.contains(key)) {
                        state.forget(key);
                    }
                }
                before.forEach(state::complete);
                state.setCurrent(current);
                Research.changedExternally(server);
            }
            helper.succeed();
        });

        tests.batch("oil");

        // Factorio's other trigger: oil processing finishes when crude oil has been mined once.
        //
        // The mechanism, without a machine: the tree has oil_processing as a mine trigger on
        // nauvis_fluids:crude_oil, one report finishes it, and the refinery unlocks. And the
        // two tallies are separate - crafting something with that id must not count, because
        // the id is also the creative item that places a well.
        //
        // Completes the whole chain up to oil gathering first, and puts the tree back exactly
        // afterwards; see ResearchCommandTest for why a snapshot and not a clear.
        tests.add("a_mine_trigger_finishes_research", 20, PADDING, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> oilProcessing = ModTechnologies.key("oil_processing");
            Technology technology = technology(helper, "oil_processing");
            helper.assertTrue(technology != null, "oil processing is not in the tree");
            helper.assertTrue(technology.isTriggered(), "oil processing has a cost; Factorio 2.0 triggers it");

            Technology.Trigger trigger = technology.trigger().orElseThrow();
            helper.assertValueEqual(trigger.kind(), Technology.Trigger.Kind.MINE, "oil processing's trigger kind");
            helper.assertValueEqual(trigger.target(), Identifier.fromNamespaceAndPath("nauvis_fluids", "crude_oil"),
                    "what oil processing waits for");
            helper.assertValueEqual(trigger.count(), 1, "how many times - Factorio's mine-entity: crude-oil, 1");

            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            finish(server, state, oilProcessing, true);
            state.recordMined(trigger.target(), -state.mined(trigger.target()));
            Research.changedExternally(server);

            ResourceKey<Recipe<?>> refinery = recipe("nauvis_fluids", "oil_refinery");
            helper.assertFalse(Research.isUnlocked(server, refinery), "the refinery is craftable before oil processing");

            // Crafting the thing with the well's id is not mining the well.
            Research.recordMade(helper.getLevel(), trigger.target(), 1);
            helper.assertFalse(state.isCompleted(oilProcessing),
                    "oil processing finished on a craft of nauvis_fluids:crude_oil - the two tallies have merged");

            Research.recordMined(helper.getLevel(), trigger.target(), 1);
            helper.assertTrue(state.isCompleted(oilProcessing),
                    "oil processing did not finish when crude oil was mined once");
            helper.assertTrue(Research.isUnlocked(server, refinery), "the refinery is still locked after oil processing");

            for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                if (!before.contains(key)) {
                    state.forget(key);
                }
            }
            before.forEach(state::complete);
            state.recordMade(trigger.target(), -state.made(trigger.target()));
            state.recordMined(trigger.target(), -state.mined(trigger.target()));
            Research.changedExternally(server);
            helper.succeed();
        });
        tests.add("pumping_oil_finishes_oil_processing", PumpingOilFinishesOilProcessingTest::new, 100, PADDING);

        tests.batch("blue");
        tests.add("a_lab_spends_blue_science", LabSpendsBlueScienceTest::new, 700, PADDING);
    }

    /** Where every test puts its lab: one block up, so it is not inside the floor. */
    private static final BlockPos LAB = new BlockPos(0, 1, 0);

    /**
     * Room around each test.
     *
     * <p>A grid test builds outside the structure it is given, and a lab is three blocks across.
     * Without padding the machines of one test land in the next one along, and the failure turns
     * up in whichever ran second.
     */
    private static final int PADDING = 16;

    /**
     * Puts a whole lab in, all ten blocks of it.
     *
     * <p>Not {@code helper.setBlock}: one block of a machine standing alone is destroyed by its
     * own teardown rule the moment anything beside it changes, so a test that placed one would
     * fail somewhere else entirely.
     */
    private static LabBlockEntity placeLab(GameTestHelper helper) {
        LabBlock block = ModBlocks.LAB.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(LAB),
                block.defaultBlockState());
        return helper.getBlockEntity(LAB, LabBlockEntity.class);
    }

    /** Fills the lab's buffer the way a pole would. */
    private static void charge(LabBlockEntity lab) {
        try (Transaction transaction = Transaction.openRoot()) {
            lab.gridView().insert(LabBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** Points the world at a technology, whatever it was doing before. */
    private static void research(GameTestHelper helper, String path) {
        MinecraftServer server = helper.getLevel().getServer();
        ResearchState state = Research.state(server);
        finish(server, state, ModTechnologies.key(path), true);
        Research.setCurrent(server, ModTechnologies.key(path));
    }

    /**
     * Completes everything a technology needs first, so a test can point a lab at it.
     *
     * <p>The tree has prerequisites now - nothing early is free - so a test that just set its
     * research would be refused by {@link Research#setCurrent} and would then fail somewhere else
     * entirely, reporting a lab that did no work.
     */
    private static void finish(MinecraftServer server, ResearchState state,
            ResourceKey<Technology> key, boolean skipSelf) {
        Technology technology = ModTechnologies.registry(server.registryAccess()).getValue(key);
        if (technology == null) {
            return;
        }
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            finish(server, state, prerequisite, false);
        }
        if (skipSelf) {
            state.forget(key);
        } else {
            state.complete(key);
        }
    }

    private static Technology technology(GameTestHelper helper, String path) {
        return ModTechnologies.registry(helper.getLevel().registryAccess())
                .getValue(ModTechnologies.key(path));
    }

    /** The key of the recipe file {@code gen_recipes.py} writes for an item. */
    private static ResourceKey<Recipe<?>> recipe(String namespace, String path) {
        return ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static boolean isScheduled(GameTestHelper helper) {
        return helper.getLevel().getBlockTicks()
                .hasScheduledTick(helper.absolutePos(LAB), ModBlocks.LAB.get());
    }

    /** Facrafting is actually asking us, and gets the right answer. */
    /**
     * {@code /research} grants and forgets whole chains, and the tree it leaves is consistent.
     */
    public static class ResearchCommandTest extends PackGameTest {

        ResearchCommandTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);

            ResourceKey<Technology> target = ModTechnologies.key("automation_2");
            ResourceKey<Technology> steel = ModTechnologies.key("steel_processing");
            ResourceKey<Technology> green = ModTechnologies.key("logistic_science_pack");

            // **Snapshotted, not just cleared.** One SavedData is shared by every test in the
            // run, and `grant` cascades - granting automation-2 completes automation, which is
            // the technology lab_researches points a lab at. Forgetting only the three named
            // below left that one researched and failed a test that mentions research nowhere.
            // Putting the tree back exactly is the only cleanup that survives a command whose
            // whole point is that it moves more than you named. See PITFALLS.md.
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());

            List<ResourceKey<Technology>> mine = List.of(target, steel, green);
            mine.forEach(state::forget);
            Research.changedExternally(server);

            run(server, "research grant " + target.identifier());

            helper.assertTrue(state.isCompleted(target), "automation-2 after granting it");
            helper.assertTrue(state.isCompleted(steel),
                    "steel processing - grant is supposed to bring the prerequisites");
            helper.assertTrue(state.isCompleted(green),
                    "green science - grant is supposed to bring the prerequisites");

            run(server, "research forget " + steel.identifier());

            helper.assertFalse(state.isCompleted(steel), "steel processing after forgetting it");
            helper.assertFalse(state.isCompleted(target),
                    "automation-2 still researched after its prerequisite was forgotten - forget "
                            + "is supposed to take the dependants with it");
            // Green science does not depend on steel, so it stays. A forget that took the whole
            // tree would pass every assertion above.
            helper.assertTrue(state.isCompleted(green),
                    "green science, which does not depend on steel processing, went with it");

            for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                if (!before.contains(key)) {
                    state.forget(key);
                }
            }
            before.forEach(state::complete);
            Research.changedExternally(server);
            helper.succeed();
        }

        /** Through the dispatcher, from the server's own source, which is a gamemaster. */
        private static void run(MinecraftServer server, String command) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

    }

    /** The tech tree's geometry, checked without anything drawing it. */
    public static class TechnologyLayoutTest extends PackGameTest {

        TechnologyLayoutTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            var access = helper.getLevel().registryAccess();
            List<Holder.Reference<Technology>> all = ModTechnologies.all(access);
            helper.assertTrue(!all.isEmpty(), "no technologies to lay out");

            java.util.Set<ResourceKey<Technology>> everything = new java.util.HashSet<>();
            for (Holder.Reference<Technology> holder : all) {
                everything.add(holder.key());
            }

            for (Holder.Reference<Technology> holder : all) {
                check(helper, all, holder.key());

                // The same view with every ancestor researched and hidden. What is left is the
                // work outstanding, which is the point of the toggle, so the selection has to
                // survive it and no ancestor may.
                TechnologyLayout.Layout thinned =
                        TechnologyLayout.around(all, holder.key(), everything, true);
                helper.assertTrue(thinned.at(holder.key()) != null,
                        "hiding the researched ancestors of " + holder.key().identifier()
                                + " took the selected technology with them");
                for (TechnologyLayout.Placed placed : thinned.nodes()) {
                    helper.assertTrue(placed.kind() != TechnologyLayout.Kind.ANCESTOR,
                            placed.key().identifier() + " is a researched ancestor that was asked "
                                    + "to be hidden and is drawn anyway");
                }
            }
            helper.succeed();
        }

        /** Everything one view has to be true about. */
        private static void check(GameTestHelper helper, List<Holder.Reference<Technology>> all,
                ResourceKey<Technology> selected) {

            TechnologyLayout.Layout layout =
                    TechnologyLayout.around(all, selected, Set.of(), false);
            java.util.Map<ResourceKey<Technology>, Technology> byKey = new java.util.HashMap<>();
            for (Holder.Reference<Technology> holder : all) {
                byKey.put(holder.key(), holder.value());
            }

            TechnologyLayout.Placed centre = layout.at(selected);
            helper.assertTrue(centre != null,
                    selected.identifier() + " is not in its own view");
            helper.assertTrue(centre.kind() == TechnologyLayout.Kind.SELECTED,
                    selected.identifier() + " is not the selection of its own view");

            java.util.Set<Long> cells = new java.util.HashSet<>();
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                helper.assertTrue(cells.add((long) placed.column() << 32 | placed.row()),
                        placed.key().identifier() + " shares a cell with something else in the "
                                + "view of " + selected.identifier() + ", so one of the two is "
                                + "drawn underneath and cannot be clicked");
                helper.assertTrue(placed.column() >= 0 && placed.row() >= 0,
                        placed.key().identifier() + " is placed off the grid");
            }

            // Everything the selection needs, however far back, is in front of the player.
            for (ResourceKey<Technology> ancestor : ancestors(byKey, selected)) {
                TechnologyLayout.Placed placed = layout.at(ancestor);
                helper.assertTrue(placed != null,
                        selected.identifier() + " needs " + ancestor.identifier()
                                + ", which its view does not draw at all");
                helper.assertTrue(placed.kind() == TechnologyLayout.Kind.ANCESTOR,
                        ancestor.identifier() + " leads to " + selected.identifier()
                                + " and is not drawn as one of its ancestors");
                helper.assertTrue(placed.column() < centre.column(),
                        ancestor.identifier() + " is not left of " + selected.identifier()
                                + " - the picture is lying about what comes first");
            }

            for (TechnologyLayout.Placed placed : layout.nodes()) {
                if (placed.kind() == TechnologyLayout.Kind.DESCENDANT) {
                    helper.assertTrue(placed.column() > centre.column(),
                            placed.key().identifier() + " comes after " + selected.identifier()
                                    + " and is not drawn to the right of it");
                    helper.assertTrue(
                            placed.column() - centre.column() <= TechnologyLayout.DESCENDANT_DEPTH,
                            placed.key().identifier() + " is further past " + selected.identifier()
                                    + " than the view is supposed to reach");
                }

                // What is not in the picture, said on the node that wants it.
                int missing = 0;
                for (ResourceKey<Technology> prerequisite
                        : placed.technology().prerequisites()) {
                    if (byKey.containsKey(prerequisite) && layout.at(prerequisite) == null) {
                        missing++;
                    }
                }
                helper.assertValueEqual(placed.outside(), missing,
                        "prerequisites of " + placed.key().identifier()
                                + " outside the view of " + selected.identifier());
            }

            for (TechnologyLayout.Edge edge : layout.edges()) {
                TechnologyLayout.Placed from = layout.at(edge.from());
                TechnologyLayout.Placed to = layout.at(edge.to());
                helper.assertTrue(from != null && to != null,
                        "an arrow points at a technology that was never placed");
                helper.assertTrue(from.column() < to.column(),
                        edge.to().identifier() + " is not right of " + edge.from().identifier()
                                + " - the arrow points backwards");
                helper.assertTrue(to.technology().prerequisites().contains(edge.from()),
                        edge.to().identifier() + " is drawn as needing " + edge.from().identifier()
                                + ", which is not a prerequisite of it");
            }

            // Nothing goes missing: a prerequisite joining two drawn technologies is drawn, or
            // another pair of arrows already says it.
            java.util.Set<String> drawn = new java.util.HashSet<>();
            for (TechnologyLayout.Edge edge : layout.edges()) {
                drawn.add(edge.from() + " -> " + edge.to());
            }
            for (TechnologyLayout.Placed placed : layout.nodes()) {
                for (ResourceKey<Technology> prerequisite : placed.technology().prerequisites()) {
                    if (layout.at(prerequisite) == null
                            || drawn.contains(prerequisite + " -> " + placed.key())) {
                        continue;
                    }
                    helper.assertTrue(implied(layout, placed.key(), prerequisite),
                            placed.key().identifier() + " needs " + prerequisite.identifier()
                                    + " and, in the view of " + selected.identifier()
                                    + ", that arrow is neither drawn nor implied by others");
                }
            }

            lanes(helper, layout, selected);

            helper.assertValueEqual(
                    TechnologyLayout.around(all, selected, Set.of(), false).nodes(),
                    layout.nodes(),
                    "the view of " + selected.identifier() + " is not deterministic - it would "
                            + "shuffle between openings");
        }

        /** Two arrows may share a channel only where their vertical runs cannot overlap. */
        private static void lanes(GameTestHelper helper, TechnologyLayout.Layout layout,
                ResourceKey<Technology> selected) {

            java.util.Map<ResourceKey<Technology>, int[]> spans = new java.util.HashMap<>();
            for (TechnologyLayout.Edge edge : layout.edges()) {
                helper.assertTrue(edge.lane() >= 0 && edge.lane() < edge.lanes(),
                        edge.from().identifier() + " turns in lane " + edge.lane() + " of "
                                + edge.lanes() + ", which is not a lane that exists");
                TechnologyLayout.Placed from = layout.at(edge.from());
                TechnologyLayout.Placed to = layout.at(edge.to());
                int[] span = spans.computeIfAbsent(edge.from(),
                        ignored -> new int[] {from.row(), from.row(), from.column(), edge.lane()});
                span[0] = Math.min(span[0], to.row());
                span[1] = Math.max(span[1], to.row());
                helper.assertValueEqual(span[3], edge.lane(),
                        "one lane for every arrow out of " + edge.from().identifier());
            }
            for (java.util.Map.Entry<ResourceKey<Technology>, int[]> one : spans.entrySet()) {
                for (java.util.Map.Entry<ResourceKey<Technology>, int[]> two : spans.entrySet()) {
                    int[] a = one.getValue();
                    int[] b = two.getValue();
                    if (one.getKey().equals(two.getKey()) || a[2] != b[2] || a[3] != b[3]) {
                        continue;
                    }
                    helper.assertTrue(a[1] < b[0] || b[1] < a[0],
                            one.getKey().identifier() + " and " + two.getKey().identifier()
                                    + " share lane " + a[3] + " in the view of "
                                    + selected.identifier() + " and overlap - the two would be "
                                    + "drawn as one line");
                }
            }
        }

        /** Everything a technology needs, transitively. */
        private static Set<ResourceKey<Technology>> ancestors(
                java.util.Map<ResourceKey<Technology>, Technology> byKey,
                ResourceKey<Technology> key) {

            java.util.Set<ResourceKey<Technology>> found = new java.util.HashSet<>();
            java.util.Deque<ResourceKey<Technology>> frontier =
                    new java.util.ArrayDeque<>(List.of(key));
            while (!frontier.isEmpty()) {
                Technology technology = byKey.get(frontier.removeFirst());
                if (technology == null) {
                    continue;
                }
                for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
                    if (byKey.containsKey(prerequisite) && !prerequisite.equals(key)
                            && found.add(prerequisite)) {
                        frontier.add(prerequisite);
                    }
                }
            }
            return found;
        }

        /** Whether {@code prerequisite} is reached from {@code key} through drawn arrows. */
        private static boolean implied(TechnologyLayout.Layout layout,
                ResourceKey<Technology> key, ResourceKey<Technology> prerequisite) {
            java.util.Set<ResourceKey<Technology>> seen = new java.util.HashSet<>(Set.of(key));
            java.util.Deque<ResourceKey<Technology>> frontier =
                    new java.util.ArrayDeque<>(List.of(key));
            while (!frontier.isEmpty()) {
                ResourceKey<Technology> at = frontier.removeFirst();
                for (TechnologyLayout.Edge edge : layout.edges()) {
                    if (edge.to().equals(at) && seen.add(edge.from())) {
                        if (edge.from().equals(prerequisite)) {
                            return true;
                        }
                        frontier.add(edge.from());
                    }
                }
            }
            return false;
        }

    }

    /**
     * The whole chain, end to end: a real pumpjack on a real well, powered, finishes oil
     * processing.
     */
    public static class PumpingOilFinishesOilProcessingTest extends PackGameTest {

        private static final BlockPos WELL = new BlockPos(2, 1, 2);

        PumpingOilFinishesOilProcessingTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            Block well = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "crude_oil"));
            Block pumpjack = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nauvis_fluids", "pumpjack"));
            if (well == Blocks.AIR || pumpjack == Blocks.AIR) {
                // Standalone: no oil here to pump. The mechanism is covered by the test above.
                helper.succeed();
                return;
            }

            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> oilProcessing = ModTechnologies.key("oil_processing");
            Identifier target = technology(helper, "oil_processing").trigger().orElseThrow().target();
            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            finish(server, state, oilProcessing, true);
            state.recordMined(target, -state.mined(target));
            Research.changedExternally(server);

            helper.setBlock(WELL, well);

            // Placed as a player would: click the top of the well with the item in hand.
            BlockPos clicked = helper.absolutePos(WELL);
            ItemStack stack = new ItemStack(pumpjack.asItem());
            ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
            BlockPos anchor = helper.absolutePos(WELL.above());
            helper.assertTrue(helper.getLevel().getBlockState(anchor).is(pumpjack),
                    "a pumpjack placed by clicking a well did not land on it");

            // Powered the way a pole powers it: through the energy capability on any of its blocks.
            EnergyHandler power = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, anchor, null);
            helper.assertTrue(power != null, "a pumpjack offers no energy capability");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(power.insert(1200, transaction) > 0, "a pumpjack took no electricity");
                transaction.commit();
            }

            helper.assertTrue(MiningListeners.installed() > 0,
                    "nothing is listening to Facrafting's MiningListeners, so no machine can finish a mine trigger");

            // One cycle is a second. Forty ticks is two, with room for the tick it wakes on.
            helper.runAfterDelay(40, () -> {
                var pumpjackEntity = helper.getLevel().getBlockEntity(anchor);
                helper.assertTrue(state.isCompleted(oilProcessing),
                        "a pumpjack ran on a well for two seconds and oil processing did not finish - "
                                + "the report is not reaching research. Mined tally: " + state.mined(target)
                                + "; prerequisites outstanding: " + technology(helper, "oil_processing").prerequisites()
                                        .stream().filter(key -> !state.isCompleted(key)).toList()
                                + "; the machine: " + (pumpjackEntity == null ? "no block entity"
                                        : pumpjackEntity.saveWithoutMetadata(helper.getLevel().registryAccess())));
                // At least the cycle that finished it. A second one ends on tick 41, and on a well as
                // rich as the gametest world's - millions of blocks from the origin - it would find
                // the tank still full of the first.
                helper.assertTrue(state.mined(target) >= 1, "the pumpjack reported no cycle at all");
                for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                    if (!before.contains(key)) {
                        state.forget(key);
                    }
                }
                before.forEach(state::complete);
                state.recordMined(target, -state.mined(target));
                Research.changedExternally(server);
                helper.succeed();
            });
        }

    }

    /** A lab spends chemical science on a technology that asks for it, one of each pack a unit. */
    public static class LabSpendsBlueScienceTest extends PackGameTest {

        LabSpendsBlueScienceTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            MinecraftServer server = helper.getLevel().getServer();
            ResearchState state = Research.state(server);
            ResourceKey<Technology> engine = ModTechnologies.key("electric_engine");
            Technology technology = technology(helper, "electric_engine");
            helper.assertTrue(technology != null, "electric engine is not in the tree");

            Identifier blue = Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "science_pack_3");
            helper.assertValueEqual(technology.packs().size(), 3, "kinds of pack electric engine wants");
            helper.assertTrue(technology.packs().contains(blue), "electric engine does not cost blue science");
            helper.assertTrue(technology.isResearchable(),
                    "electric engine cannot be researched - a pack it wants is not a registered item");
            helper.assertValueEqual(technology.ticksPerUnit(), 600, "ticks a unit of electric engine");

            Set<ResourceKey<Technology>> before = Set.copyOf(state.completed());
            ResourceKey<Technology> current = state.current();
            research(helper, "electric_engine");
            helper.assertValueEqual(state.current(), engine, "the world's current research");

            LabBlockEntity lab = placeLab(helper);
            helper.onEachTick(() -> charge(lab));
            insert(lab.automationView(), ModItems.SCIENCE_PACK_1.get(), 2);
            insert(lab.automationView(), ModItems.SCIENCE_PACK_2.get(), 2);

            // Two of the three: not a unit. The lab is fed and powered and must sit still.
            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(lab.progress(), 0,
                        "progress on a lab holding red and green but no blue science");
                helper.assertValueEqual(lab.cycles(), 0, "units done without blue science");

                insert(lab.automationView(), ModItems.SCIENCE_PACK_3.get(), 2);

                // A fed lab with nothing to research looks again once a second, and the pack
                // arriving cannot bring that look forward - a scheduled tick is kept, not moved -
                // so the unit starts up to a recheck late. The window allows for the whole of one.
                int unitTicks = technology.ticksPerUnit() + LabBlockEntity.IDLE_RECHECK_TICKS;

                helper.runAfterDelay(unitTicks + 3, () -> {
                    try {
                        helper.assertValueEqual(lab.cycles(), 1, "units after one unit's worth of ticks");
                        for (Item pack : List.of(ModItems.SCIENCE_PACK_1.get(),
                                ModItems.SCIENCE_PACK_2.get(), ModItems.SCIENCE_PACK_3.get())) {
                            helper.assertValueEqual(count(lab, pack), 1,
                                    pack + " left after one unit - a unit is one of each");
                        }
                        helper.assertValueEqual(state.units(engine), 1,
                                "units the world has towards electric engine");
                    } finally {
                        // Put the tree back for whatever batch runs next.
                        for (ResourceKey<Technology> key : List.copyOf(state.completed())) {
                            if (!before.contains(key)) {
                                state.forget(key);
                            }
                        }
                        before.forEach(state::complete);
                        state.forget(engine);
                        state.setCurrent(current);
                        Research.changedExternally(server);
                    }
                    helper.succeed();
                });
            });
        }

        private static int count(LabBlockEntity lab, Item item) {
            int total = 0;
            for (int slot = 0; slot < lab.inventory().size(); slot++) {
                if (lab.inventory().getAmountAsInt(slot) > 0 && lab.inventory().getResource(slot).is(item)) {
                    total += lab.inventory().getAmountAsInt(slot);
                }
            }
            return total;
        }

    }

}
