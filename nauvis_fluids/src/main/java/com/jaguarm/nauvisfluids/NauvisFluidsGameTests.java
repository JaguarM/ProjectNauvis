package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.ModuleEffect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvislib.multiblock.MachineShape;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantBlock;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantBlockEntity;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlockEntity;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpStatus;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.CrudeOilField;
import com.jaguarm.nauvisfluids.oil.CrudeOilFieldFeature;
import com.jaguarm.nauvisfluids.oil.OilProgress;
import com.jaguarm.nauvisfluids.pipe.FluidNetwork;
import com.jaguarm.nauvisfluids.pipe.FluidNetworkManager;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;
import com.jaguarm.nauvisfluids.processing.ProcessingBlock;
import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvisfluids.processing.ProcessingStatus;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackStatus;
import com.jaguarm.nauvisfluids.refinery.OilRefineryBlock;
import com.jaguarm.nauvisfluids.refinery.OilRefineryBlockEntity;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import com.jaguarm.nauvisfluids.registry.ModItems;
import com.jaguarm.nauvisfluids.tank.StorageTankBlock;
import com.jaguarm.nauvisfluids.tank.StorageTankBlockEntity;
import com.jaguarm.nauvisfluids.water.NaturalWaterFeature;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Tests that run inside a real server, headless. */
public final class NauvisFluidsGameTests {

    private NauvisFluidsGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisFluids.MODID, modEventBus);
        tests.add("pipe_run_is_one_object", PipeRunIsOneObjectTest::new, 100);
        tests.add("pipe_run_splits_and_merges", PipeRunSplitsAndMergesTest::new, 200);
        tests.add("pipe_connects_to_what_offers_fluid", PipeConnectsTest::new, 100);

        // Steam and crude oil exist under the ids the mapping has always given them.
        //
        // nauvis_fluids:steam is identity: data/mapping.json names it, and
        // nauvis_power finds it by that id rather than by importing it, so a rename here would
        // quietly stop every boiler in the pack from making anything. nauvis_fluids:crude_oil
        // is the same kind of fact for everything downstream of a pumpjack.
        tests.add("fluids_are_registered", 20, helper -> {
            for (String name : new String[] {"steam", "crude_oil", "water", "flowing_water",
                    "heavy_oil", "light_oil", "petroleum_gas", "lubricant", "sulfuric_acid"}) {
                Identifier id = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name);
                helper.assertTrue(BuiltInRegistries.FLUID.getValue(id) != Fluids.EMPTY,
                        "nauvis_fluids:" + name + " is not registered");
            }
            for (String name : new String[] {"pipe", "crude_oil", "pumpjack", "water", "offshore_pump"}) {
                Block block = BuiltInRegistries.BLOCK.getValue(
                        Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name));
                helper.assertTrue(block != Blocks.AIR, "nauvis_fluids:" + name + " is not registered");
            }
            helper.succeed();
        });
        tests.add("pipe_reports_its_run", PipeReportsItsRunTest::new, 100);

        // An oil well cannot be mined, pushed or dropped, and it is worth something the moment it
        // exists.
        //
        // Factorio's crude-oil is a resource entity: nothing a player does moves it. Here
        // that is three block properties, each of which could be lost in a refactor of
        // ModBlocks without anything else noticing. The amount is the other half - a well
        // placed in the world works out how rich it is from where it is, so a well placed with nothing
        // written into it must still answer with Factorio's floor or better.
        tests.add("crude_oil_is_unmovable", 40, PADDING, helper -> {
            helper.setBlock(WELL, ModBlocks.CRUDE_OIL.get());
            BlockPos at = helper.absolutePos(WELL);
            BlockState state = helper.getBlockState(WELL);

            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), at) < 0,
                    "an oil well can be mined, so a player can pick up Factorio's one unmovable resource");
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK,
                    "an oil well can be pushed by a piston");
            helper.assertTrue(ModBlocks.CRUDE_OIL.get().getLootTable().isEmpty(),
                    "an oil well has a loot table, so something that breaks it gets a well back");

            CrudeOilBlockEntity well = helper.getBlockEntity(WELL, CrudeOilBlockEntity.class);
            helper.assertTrue(well.amount() >= CrudeOilField.ADDITIONAL_RICHNESS + CrudeOilField.SPREAD_MIN,
                    "a fresh well is poorer than Factorio's additional richness allows: " + well.amount());
            helper.assertTrue(well.amount() % CrudeOilBlockEntity.DEPLETION == 0,
                    "a well's amount is not a multiple of a cycle's depletion: " + well.amount());
            helper.assertValueEqual(well.initial(), well.amount(), "a fresh well's initial amount");
            helper.assertTrue(well.yieldPercent() >= 90,
                    "a well near the start reads under 90%: " + well.yieldPercent());
            helper.succeed();
        });

        // A pumpjack goes down centred on a well, from a click anywhere over it, and nowhere else.
        //
        // Factorio's rule and Factorio's snapping. Three clicks: the block over the well, which
        // must place with the centre pinned to it; the block over the well's diagonal neighbour, which
        // must place with a corner pinned to it so that the centre still lands over the well;
        // and a block two away, which is not over the machine's footprint and must refuse.
        tests.add("pumpjack_stands_only_on_a_well", 40, PADDING, helper -> {
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < 6; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                }
            }
            well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            MachineShape shape = PumpjackShape.SHAPE;

            BlockState centred = placement(helper, WELL);
            helper.assertTrue(centred != null, "a pumpjack refuses the block directly over a well");
            helper.assertValueEqual(centred.getValue(shape.part()), shape.placement(),
                    "the cell pinned to a click over the well");

            BlockPos diagonal = WELL.offset(1, 0, 1);
            BlockState snapped = placement(helper, diagonal);
            helper.assertTrue(snapped != null,
                    "a pumpjack refuses a click one block off the well, so placement does not snap");
            BlockPos anchor = shape.anchorPos(helper.absolutePos(diagonal.above()),
                    snapped.getValue(shape.part()), snapped.getValue(PumpjackBlock.FACING));
            helper.assertValueEqual(anchor, helper.absolutePos(PUMPJACK),
                    "where a pumpjack clicked one block off the well ends up centred");

            helper.assertTrue(placement(helper, WELL.offset(2, 0, 0)) == null,
                    "a pumpjack accepts a block two away from the well, which is not over one");
            helper.assertTrue(placement(helper, new BlockPos(5, 1, 5)) == null,
                    "a pumpjack accepts plain ground");

            // The ghost asks the same questions, so it snaps where the click will and says no
            // where the click would.
            Multiblock.Ghost ghost = Multiblock.ghost(ModBlocks.PUMPJACK.get(), pumpjackClick(helper, diagonal));
            helper.assertTrue(ghost.allowed(), "the ghost of a pumpjack clicked one block off the well says it will not go");
            helper.assertValueEqual(ghost.anchor(), helper.absolutePos(PUMPJACK),
                    "where the ghost of a pumpjack clicked one block off the well stands");
            helper.assertFalse(Multiblock.ghost(ModBlocks.PUMPJACK.get(), pumpjackClick(helper, new BlockPos(5, 1, 5))).allowed(),
                    "the ghost of a pumpjack on plain ground says it will go");
            helper.succeed();
        });

        // Ten crude oil a second from a 100% well, and ten off the well each second.
        //
        // The two numbers that are identity. One cycle is twenty ticks, so after thirty the first
        // cycle has banked and the second has not, and after fifty two have. The second cycle is
        // pumped from a well that is no longer quite 100%, and the machine carries the fraction rather
        // than rounding it, so two cycles bank nineteen - #bankedAfter is that arithmetic.
        tests.add("pumpjack_pumps_at_factorio_rate", 100, PADDING, helper -> {
            CrudeOilBlockEntity well = well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.startSequence()
                    .thenExecuteAfter(30, () -> {
                        helper.assertValueEqual(pumpjack.stored(), PumpjackBlockEntity.UNITS_PER_CYCLE_AT_NORMAL,
                                "crude oil banked after one cycle from a 100% well");
                        helper.assertValueEqual(well.amount(),
                                CrudeOilBlockEntity.NORMAL - CrudeOilBlockEntity.DEPLETION,
                                "what one cycle takes off a well");
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.PUMPING, "status while pumping");
                    })
                    .thenExecuteAfter(20, () -> {
                        // Nineteen, not twenty: the second cycle ran at 99.997% and the fraction is
                        // carried, not rounded up. See bankedAfter.
                        helper.assertValueEqual(pumpjack.stored(), bankedAfter(CrudeOilBlockEntity.NORMAL, 2),
                                "crude oil banked after two cycles");
                        helper.assertValueEqual(well.amount(),
                                CrudeOilBlockEntity.NORMAL - 2 * CrudeOilBlockEntity.DEPLETION,
                                "what two cycles take off a well");
                        // 90 kW is twelve a tick for every tick spent pumping, the third cycle's
                        // ticks included - not a price per cycle. Two full cycles at least, and
                        // never more than the fifty ticks that have passed.
                        int spent = PumpjackBlockEntity.ENERGY_CAPACITY - pumpjack.energyStored();
                        helper.assertTrue(spent >= 2 * PumpjackBlockEntity.CYCLE_TICKS * PumpjackBlockEntity.ENERGY_PER_TICK
                                        && spent <= 50 * PumpjackBlockEntity.ENERGY_PER_TICK
                                        && spent % PumpjackBlockEntity.ENERGY_PER_TICK == 0,
                                "electricity spent over two cycles and a bit: " + spent);
                    })
                    .thenSucceed();
        });
        tests.add("well_stops_at_its_floor", WellStopsAtItsFloorTest::new, 100, PADDING);
        tests.add("pumpjack_sleeps", PumpjackSleepsTest::new, 200, PADDING);

        // A pumpjack takes Factorio's modules and reads them the way every other machine does: a
        // speed module shortens the cycle by its speed and raises the draw by its cost. The module is
        // nauvis_machines' and this mod does not name it, so it is looked up by id and the test
        // passes trivially without it.
        tests.add("pumpjack_takes_modules", 40, PADDING, helper -> {
            var speed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_machines", "speed_module"));
            if (ModuleSlots.moduleOf(new ItemStack(speed)) == null) {
                helper.succeed();
                return;
            }
            well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            pumpjack.modules().set(0, ItemResource.of(speed), 1);
            charge(pumpjack);
            ModuleEffect effect = ModuleSlots.moduleOf(new ItemStack(speed)).effect();

            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(pumpjack.status(), PumpjackStatus.PUMPING, "status with a well, power and a module");
                helper.assertValueEqual(pumpjack.cycleTicks(),
                        (int) Math.round(PumpjackBlockEntity.CYCLE_TICKS / effect.speedFactor()),
                        "ticks a cycle takes with a speed module");
                helper.assertTrue(pumpjack.cycleTicks() < PumpjackBlockEntity.CYCLE_TICKS,
                        "a speed module did not shorten the cycle");
                helper.assertValueEqual(pumpjack.currentEnergyPerTick(),
                        effect.scaleEnergy(PumpjackBlockEntity.ENERGY_PER_TICK), "draw with a speed module");
                helper.succeed();
            });
        });

        // Oil leaves by the outlet and by nothing else, and a pipe there carries it away.
        //
        // The outlet is the north-east corner's north face on a north-facing machine, which is
        // Factorio's corner. A pipe against the east flank must not connect - that a pipe in the wrong
        // place gets nothing is what makes the outlet a thing the player can be right about.
        tests.add("pumpjack_feeds_a_pipe", 100, PADDING, helper -> {
            MachineShape shape = PumpjackShape.SHAPE;
            BlockPos outletCell = shape.cellPos(PUMPJACK, PumpjackShape.OUTLET_CELL, Direction.NORTH);
            BlockPos outletPipe = outletCell.north();
            BlockPos flankPipe = PUMPJACK.east(2);

            // Pipes first. A block put down by anything but a player never runs
            // getStateForPlacement - see PITFALLS.md - so a pipe placed beside a machine that is
            // already there would show no connection; placed first, the machine arriving is the
            // neighbour change that makes each pipe re-read the face towards it.
            pipe(helper, outletPipe);
            pipe(helper, flankPipe);
            well(helper, WELL, CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(helper.getBlockState(outletPipe).getValue(PipeBlock.SOUTH),
                                "a pipe at the outlet does not reach into the pumpjack");
                        helper.assertFalse(helper.getBlockState(flankPipe).getValue(PipeBlock.WEST),
                                "a pipe on the flank connects to a pumpjack, so the outlet means nothing");
                    })
                    .thenExecuteAfter(45, () -> {
                        FluidNetwork run = networkAt(helper, outletPipe, "the outlet pipe has no run");
                        helper.assertValueEqual(run.fluid().getFluid(), ModFluids.CRUDE_OIL.get(),
                                "what the outlet pipe is carrying");
                        helper.assertTrue(run.amount() + pumpjack.stored() == bankedAfter(CrudeOilBlockEntity.NORMAL, 2),
                                "oil went missing between the pumpjack and the pipe: " + run.amount()
                                        + " in the run, " + pumpjack.stored() + " in the tank");
                        helper.assertTrue(run.amount() > 0, "the pipe run drew nothing from the pumpjack");
                        helper.assertValueEqual(networkAt(helper, flankPipe, "the flank pipe has no run").amount(), 0,
                                "what a pipe on the flank carries");
                    })
                    .thenSucceed();
        });
        tests.add("oil_field_is_pumpable", OilFieldIsPumpableTest::new, 60, WIDE_PADDING);

        // A pumpjack says what it mined, once per cycle, naming the well.
        //
        // This is the report that finishes Factorio's oil processing - {@code mine-entity:
        // crude-oil, 1} - through Facrafting's MiningListeners and into research, neither of
        // which this mod names. What can be asserted here is this mod's half: one report per cycle,
        // for this well, of one, whatever the yield. Other pumpjacks in the run report too, so the
        // listener keeps only what came from this test's well.
        tests.add("pumpjack_reports_what_it_mines", 100, PADDING, helper -> {
            // The seam to research goes through Facrafting, and with Facrafting present the
            // adapter that forwards reports must be installed - see FacraftingProgress.
            if (ModList.get().isLoaded("facrafting")) {
                helper.assertTrue(OilProgress.installed() > 0,
                        "Facrafting is loaded and nothing forwards what a pumpjack mines to it");
            }

            BlockPos wellPos = helper.absolutePos(WELL);
            List<String> reports = new ArrayList<>();
            OilProgress.add((level, well, resource, cycles) -> {
                if (well.equals(wellPos)) {
                    reports.add(resource + " x" + cycles);
                }
            });

            well(helper, WELL, 4 * CrudeOilBlockEntity.NORMAL);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.runAfterDelay(50, () -> {
                // Two cycles at 400%: forty units each, and still one report of one per cycle.
                helper.assertValueEqual(reports, List.of("nauvis_fluids:crude_oil x1", "nauvis_fluids:crude_oil x1"),
                        "what the pumpjack reported over two cycles");
                helper.succeed();
            });
        });
        tests.add("pumpjack_caps_a_cycle_at_its_tank", PumpjackCapsACycleTest::new, 60, PADDING);
        tests.add("oil_command_places_a_field", OilCommandPlacesAFieldTest::new, 40, WIDE_PADDING);

        // A bucket lifts natural water as water, and pours it back as vanilla's.
        //
        // The rule that makes a lake a place rather than a supply: what you carry away is
        // ordinary water, what you pour out is ordinary water, and neither is what an offshore pump
        // draws from. Water on the move is not lifted at all, exactly as vanilla's is not.
        tests.add("natural_water_is_bucketed_as_water", 20, PADDING, helper -> {
            platform(helper, 5);
            BlockPos at = new BlockPos(2, 2, 2);
            BlockPos absolute = helper.absolutePos(at);
            LiquidBlock water = ModBlocks.WATER.get();

            helper.setBlock(at, naturalWater());
            ItemStack lifted = water.pickupBlock(null, helper.getLevel(), absolute, helper.getBlockState(at));
            helper.assertTrue(lifted.is(Items.WATER_BUCKET),
                    "a bucket of natural water is " + lifted + ", not a water bucket");
            helper.assertTrue(helper.getBlockState(at).isAir(), "the water was lifted and is still there");

            ((BucketItem) Items.WATER_BUCKET).emptyContents(null, helper.getLevel(), absolute, null);
            helper.assertTrue(helper.getBlockState(at).is(Blocks.WATER),
                    "a poured bucket put down " + helper.getBlockState(at) + ", not vanilla's water");

            helper.setBlock(at, naturalWater().setValue(LiquidBlock.LEVEL, 2));
            helper.assertTrue(water.pickupBlock(null, helper.getLevel(), absolute, helper.getBlockState(at)).isEmpty(),
                    "flowing natural water was lifted by a bucket");
            helper.succeed();
        });
        tests.add("natural_water_makes_no_new_source", NaturalWaterMakesNoNewSourceTest::new, 100, PADDING);
        tests.add("worldgen_water_becomes_natural", WorldgenWaterBecomesNaturalTest::new, 20, PADDING);

        // An offshore pump stands where its intake finds still natural water, and nowhere else.
        //
        // Not on dry land, not at a bucket's water, and not at the flowing edge of a lake - the
        // three ways a player would otherwise get infinite water back. Under the intake counts, and
        // so does beside it: a pump on a beach reaches down, a pump in the shallows reaches sideways.
        tests.add("offshore_pump_stands_only_at_water", 40, PADDING, helper -> {
            platform(helper, 6);
            BlockPos shore = PUMP.below();
            BlockPos beside = INTAKE_WATER.above().west();

            helper.assertTrue(pumpPlacement(helper, shore) == null, "an offshore pump stands on dry land");

            helper.setBlock(INTAKE_WATER, naturalWater());
            BlockState placed = pumpPlacement(helper, shore);
            helper.assertTrue(placed != null, "an offshore pump refuses a shore with natural water ahead of it");
            helper.assertValueEqual(placed.getValue(OffshorePumpBlock.FACING), Direction.NORTH,
                    "the way a pump placed with no player faces");

            helper.setBlock(INTAKE_WATER, Blocks.WATER.defaultBlockState());
            helper.assertTrue(pumpPlacement(helper, shore) == null,
                    "an offshore pump accepts a bucket's water, so water is infinite again");
            helper.assertValueEqual(OffshorePumpBlock.bestIntake(helper.getLevel(), helper.absolutePos(PUMP)),
                    OffshorePumpBlock.Intake.OTHER, "what the refusal says of a bucket's water");

            helper.setBlock(INTAKE_WATER, naturalWater().setValue(LiquidBlock.LEVEL, 3));
            helper.assertTrue(pumpPlacement(helper, shore) == null, "an offshore pump accepts water on the move");

            helper.setBlock(INTAKE_WATER, Blocks.STONE);
            helper.assertValueEqual(OffshorePumpBlock.bestIntake(helper.getLevel(), helper.absolutePos(PUMP)),
                    OffshorePumpBlock.Intake.NONE, "what the refusal says of dry land");

            helper.setBlock(beside, naturalWater());
            helper.assertTrue(pumpPlacement(helper, shore) != null,
                    "an offshore pump refuses natural water beside its intake");
            helper.setBlock(beside, Blocks.AIR);

            // A bank a block above the water: the intake reaches two down.
            helper.setBlock(INTAKE_WATER.below(), naturalWater());
            helper.assertTrue(pumpPlacement(helper, shore) != null,
                    "an offshore pump on a bank one block above the water refuses to stand there");
            helper.succeed();
        });

        // A pump turns to the water. The click faces north and the lake is to the east, and the pump
        // placed faces east, intake over the lake.
        //
        // Factorio's ghost snaps to the shoreline; this is the nearest a block can come. The
        // player's own facing is tried first, so a pump that could face the way they look does, and
        // only one that could not turns.
        tests.add("offshore_pump_turns_to_the_water", 40, PADDING, helper -> {
            platform(helper, 6);
            BlockPos shore = PUMP.below();

            // Dry: the ghost stands where the click would have put it, the player's way, refused.
            Multiblock.Ghost dry = Multiblock.ghost(ModBlocks.OFFSHORE_PUMP.get(), pumpClick(helper, shore));
            helper.assertFalse(dry.allowed(), "the ghost of a pump on dry land says it will go");
            helper.assertValueEqual(dry.facing(), Direction.NORTH, "the way a refused pump's ghost faces");
            helper.assertValueEqual(dry.anchor(), helper.absolutePos(PUMP), "where a refused pump's ghost stands");

            // Under where an east-facing intake would hang, and nowhere a north-facing one reaches.
            helper.setBlock(PUMP.east().below(), naturalWater());

            Multiblock.Ghost turned = Multiblock.ghost(ModBlocks.OFFSHORE_PUMP.get(), pumpClick(helper, shore));
            helper.assertTrue(turned.allowed(), "the ghost of a pump beside a lake says it will not go");
            helper.assertValueEqual(turned.facing(), Direction.EAST, "the way a pump's ghost turns to find water");
            helper.assertValueEqual(
                    ModBlocks.OFFSHORE_PUMP.get().placementMarks(helper.getLevel(), turned.anchor(), turned.facing()),
                    List.of(helper.absolutePos(PUMP.east().below())), "what a pump's ghost marks");

            BlockState placed = pumpPlacement(helper, shore);
            helper.assertTrue(placed != null, "an offshore pump facing away from a lake beside it will not turn to it");
            helper.assertValueEqual(placed.getValue(OffshorePumpBlock.FACING), Direction.EAST,
                    "the way a pump clicked facing north turns when the water is to the east");

            // Water the way the player faces wins over water beside, so a pump faces as placed
            // whenever it can.
            helper.setBlock(INTAKE_WATER, naturalWater());
            BlockState straight = pumpPlacement(helper, shore);
            helper.assertTrue(straight != null, "a pump with water ahead of it will not stand");
            helper.assertValueEqual(straight.getValue(OffshorePumpBlock.FACING), Direction.NORTH,
                    "the way a pump faces when the water is where the player looks");
            helper.succeed();
        });
        tests.add("offshore_pump_floats_on_a_lake", OffshorePumpFloatsOnALakeTest::new, 40, PADDING);
        tests.add("oil_recipes_load", OilRecipesLoadTest::new, 20);
        tests.add("offshore_pump_pumps_at_factorio_rate", OffshorePumpPumpsAtFactorioRateTest::new, 60, PADDING);

        // What comes out of the back is water - vanilla's, the water of the pipes - and only the back
        // offers it.
        //
        // A pipe at the outlet reaches into the machine and its run fills with
        // minecraft:water until run and tank are both full and the pump reports so. A pipe on
        // the flank connects to nothing and carries nothing.
        tests.add("offshore_pump_fills_a_pipe", 100, PADDING, helper -> {
            platform(helper, 5);
            BlockPos outletPipe = PUMP.south();
            BlockPos flankPipe = PUMP.east();

            // Pipes first, for the reason the pumpjack test gives: a block put down by anything
            // but a player never runs getStateForPlacement, so a pipe placed beside a machine
            // already there would show no connection.
            pipe(helper, outletPipe);
            pipe(helper, flankPipe);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(helper.getBlockState(outletPipe).getValue(PipeBlock.NORTH),
                                "a pipe at the outlet does not reach into the offshore pump");
                        helper.assertFalse(helper.getBlockState(flankPipe).getValue(PipeBlock.WEST),
                                "a pipe on the flank connects to an offshore pump, so the outlet means nothing");
                    })
                    .thenExecuteAfter(40, () -> {
                        FluidNetwork run = networkAt(helper, outletPipe, "the outlet pipe has no run");
                        helper.assertValueEqual(run.fluid().getFluid(), Fluids.WATER,
                                "what the outlet pipe is carrying");
                        helper.assertValueEqual(run.amount(), run.capacity(), "a run fed by an offshore pump fills up");
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY,
                                "the tank behind a full run");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.OUTPUT_FULL,
                                "status with a full tank and a full run");
                        helper.assertValueEqual(networkAt(helper, flankPipe, "the flank pipe has no run").amount(), 0,
                                "what a pipe on the flank carries");
                    })
                    .thenSucceed();
        });

        // A full offshore pump asks for no ticks; a draw wakes it; the water going, or turning out to
        // be a bucket's, stops it again; the lake coming back restarts it.
        //
        // Non-negotiable #5, asserted through hasScheduledTick for each of the three
        // reasons the machine can stop and the two ways it can be woken. Delete the wake in
        // FluidOutputAccess or the one in neighborChanged and one of these lines goes red.
        tests.add("offshore_pump_sleeps", 100, PADDING, helper -> {
            platform(helper, 5);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);
            Block block = ModBlocks.OFFSHORE_PUMP.get();

            helper.startSequence()
                    .thenExecuteAfter(15, () -> {
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY, "a tank left alone");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.OUTPUT_FULL, "status when full");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "a full offshore pump is still asking for ticks");
                        drawWater(pump, 50);
                        helper.assertTrue(isScheduled(helper, PUMP, block), "drawing from a full offshore pump did not wake it");
                    })
                    .thenExecuteAfter(10, () -> {
                        helper.assertValueEqual(pump.stored(), OffshorePumpBlockEntity.TANK_CAPACITY, "the tank after a draw");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "a refilled offshore pump is still asking for ticks");
                        helper.setBlock(INTAKE_WATER, Blocks.STONE);
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.NO_WATER, "status with the lake gone");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "an offshore pump with no water is still asking for ticks");
                        helper.setBlock(INTAKE_WATER, Blocks.WATER.defaultBlockState());
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.WRONG_WATER, "status at a bucket's water");
                        helper.assertFalse(isScheduled(helper, PUMP, block), "an offshore pump at the wrong water is still asking for ticks");
                        drawWater(pump, OffshorePumpBlockEntity.TANK_CAPACITY);
                        helper.setBlock(INTAKE_WATER, naturalWater());
                    })
                    .thenExecuteAfter(3, () -> {
                        helper.assertTrue(pump.stored() > 0, "the lake coming back did not restart the pump");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.PUMPING, "status with the lake back");
                    })
                    .thenSucceed();
        });
        tests.add("storage_tank_levels_with_its_run", StorageTankLevelsWithItsRunTest::new, 100, PADDING);
        tests.add("refinery_runs_basic_oil_processing", RefineryRunsBasicOilProcessingTest::new, 300, PADDING);

        // Advanced oil processing with nowhere to put the heavy oil makes no light oil and no gas
        // either, and spends nothing - the whole puzzle of Factorio's oil in one assertion. Draw some
        // heavy oil off and the other two flow again.
        tests.add("refinery_outputs_block_each_other", 300, PADDING, helper -> {
            platform(helper, 7);
            OilRefineryBlockEntity refinery = refinery(helper, MACHINE);
            charge(refinery);
            refinery.setRecipe(recipe(NauvisFluids.MODID, "advanced_oil_processing"));
            Fluid heavy = ModFluids.HEAVY_OIL.get();
            int full = ProcessingBlockEntity.TANK_CAPACITY;
            helper.assertValueEqual(fill(refinery.inputAccess(OilRefineryBlockEntity.WATER_PORT), Fluids.WATER, full), full,
                    "water into the water port");
            helper.assertValueEqual(fill(refinery.inputAccess(OilRefineryBlockEntity.CRUDE_PORT), ModFluids.CRUDE_OIL.get(), full), full,
                    "crude into the crude port");
            // The heavy oil tank is jammed full before the first craft can start.
            helper.assertValueEqual(fill(refinery.outputTank(OilRefineryBlockEntity.HEAVY_PORT), heavy, full), full,
                    "jamming the heavy oil tank");

            helper.startSequence()
                    .thenExecuteAfter(120, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.OUTPUT_FULL, "status with heavy oil jammed");
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.LIGHT_PORT).getAmountAsInt(0), 0,
                                "light oil made while jammed");
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT).getAmountAsInt(0), 0,
                                "petroleum gas made while jammed");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.WATER_PORT).getAmountAsInt(0), full,
                                "water spent while jammed");
                        helper.assertFalse(isScheduled(helper, MACHINE, ModBlocks.OIL_REFINERY.get()),
                                "a jammed refinery is still asking for ticks");
                        helper.assertValueEqual(drain(refinery.outputAccess(OilRefineryBlockEntity.HEAVY_PORT), heavy, 100), 100,
                                "drawing heavy oil off");
                    })
                    .thenExecuteAfter(110, () -> {
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.HEAVY_PORT).getAmountAsInt(0), 925,
                                "heavy oil after one craft");
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.LIGHT_PORT).getAmountAsInt(0), 45,
                                "light oil after one craft");
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT).getAmountAsInt(0), 55,
                                "petroleum gas after one craft");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.WATER_PORT).getAmountAsInt(0), 950,
                                "water after one craft");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.CRUDE_PORT).getAmountAsInt(0), 900,
                                "crude after one craft");
                    })
                    .thenSucceed();
        });
        tests.add("chemical_plant_makes_plastic", ChemicalPlantMakesPlasticTest::new, 200, PADDING);

        // The refinery and the chemical plant have three module slots each, and modules in them change
        // the machine's speed and draw by Factorio's arithmetic.
        //
        // The modules are the machines mod's items and this mod does not name it, so the test finds
        // them by id and passes on the slots alone when they are not there - the standalone run. With
        // them, three speed modules are plus three fifths on the speed and half again three times on
        // the draw: a 210 kW plant at 28 FE a tick draws 70.
        tests.add("oil_machines_take_modules", 20, PADDING, helper -> {
            ChemicalPlantBlockEntity plant = chemicalPlant(helper, MACHINE);
            helper.assertValueEqual(plant.layout().moduleSlots(), ChemicalPlantBlockEntity.MODULE_SLOTS,
                    "module slots in the chemical plant's layout");
            helper.assertValueEqual(plant.modules().size(), 3, "module slots on a chemical plant");
            helper.assertValueEqual(OilRefineryBlockEntity.LAYOUT.moduleSlots(), 3, "module slots on a refinery");

            var speed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("nauvis_machines", "speed_module"));
            if (speed == Items.AIR) {
                helper.succeed();
                return;
            }
            for (int slot = 0; slot < 3; slot++) {
                try (Transaction transaction = Transaction.openRoot()) {
                    helper.assertValueEqual(plant.modules().insert(slot, ItemResource.of(speed), 1, transaction), 1,
                            "a speed module taken by slot " + slot);
                    transaction.commit();
                }
            }
            helper.assertTrue(Math.abs(plant.modules().effect().speedFactor() - 1.6) < 1e-9,
                    "three speed modules' speed factor: " + plant.modules().effect().speedFactor());
            helper.assertValueEqual(plant.currentEnergyPerTick(),
                    (int) Math.round(ChemicalPlantBlockEntity.ENERGY_PER_TICK * 2.5),
                    "the draw under three speed modules");
            helper.succeed();
        });

        // A refinery asks for a tick only while it has work, and is woken by each of the things that
        // can give it some: a recipe, an ingredient, electricity, and room for a product.
        //
        // The one to watch is the last: a machine that finished a craft into a full tank holds it
        // unpaid, and drawing from the tank is what lets it bank the craft and carry on.
        tests.add("oil_machines_sleep", 200, PADDING, helper -> {
            platform(helper, 7);
            OilRefineryBlockEntity refinery = refinery(helper, MACHINE);
            Block block = ModBlocks.OIL_REFINERY.get();
            Fluid crude = ModFluids.CRUDE_OIL.get();
            Fluid petroleum = ModFluids.PETROLEUM_GAS.get();
            int full = ProcessingBlockEntity.TANK_CAPACITY;

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_RECIPE, "status with no recipe");
                        helper.assertFalse(isScheduled(helper, MACHINE, block), "a refinery with no recipe is asking for ticks");
                        refinery.setRecipe(recipe(NauvisFluids.MODID, "basic_oil_processing"));
                        helper.assertTrue(isScheduled(helper, MACHINE, block), "choosing a recipe did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_INGREDIENTS, "status with no crude");
                        helper.assertFalse(isScheduled(helper, MACHINE, block), "a refinery with no crude is asking for ticks");
                        fill(refinery.inputAccess(OilRefineryBlockEntity.CRUDE_PORT), crude, 100);
                        helper.assertTrue(isScheduled(helper, MACHINE, block), "crude arriving did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_POWER, "status with no power");
                        helper.assertFalse(isScheduled(helper, MACHINE, block), "a refinery with no power is asking for ticks");
                        charge(refinery);
                        helper.assertTrue(isScheduled(helper, MACHINE, block), "power arriving did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.WORKING, "status while crafting");
                        helper.assertTrue(isScheduled(helper, MACHINE, block), "a working refinery is not asking for ticks");
                        // Jam the output while the craft is under way, so it finishes into a full tank.
                        helper.assertValueEqual(fill(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT), petroleum, full), full,
                                "jamming the petroleum tank");
                    })
                    .thenExecuteAfter(110, () -> {
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.OUTPUT_FULL, "status with the output jammed");
                        helper.assertFalse(isScheduled(helper, MACHINE, block), "a jammed refinery is asking for ticks");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.CRUDE_PORT).getAmountAsInt(0), 100,
                                "crude spent on a craft that could not be banked");
                        drain(refinery.outputAccess(OilRefineryBlockEntity.PETROLEUM_PORT), petroleum, 100);
                        helper.assertTrue(isScheduled(helper, MACHINE, block), "drawing from the jammed tank did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT).getAmountAsInt(0), 945,
                                "petroleum gas once the held craft was banked");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.CRUDE_PORT).getAmountAsInt(0), 0,
                                "crude once the held craft was banked");
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_INGREDIENTS, "status after the crude was spent");
                        helper.assertFalse(isScheduled(helper, MACHINE, block), "a refinery out of crude is asking for ticks");
                    })
                    .thenSucceed();
        });

        // A refinery runs oil processing and a chemical plant runs chemistry, and each refuses the
        // other's recipes at the block entity, behind whatever the panel showed.
        tests.add("oil_machines_run_only_their_category", 40, PADDING, helper -> {
            platform(helper, 7);
            ChemicalPlantBlockEntity plant = chemicalPlant(helper, MACHINE);
            ResourceKey<Recipe<?>> basic = recipe(NauvisFluids.MODID, "basic_oil_processing");
            ResourceKey<Recipe<?>> cracking = recipe(NauvisFluids.MODID, "heavy_oil_cracking");
            plant.setRecipe(basic);
            helper.assertTrue(plant.recipeKey() == null, "a chemical plant took an oil processing recipe");
            plant.setRecipe(cracking);
            helper.assertValueEqual(plant.recipeKey(), cracking, "a chemical plant refused cracking");
            helper.assertValueEqual(plant.inputTank(ChemicalPlantBlockEntity.WATER_PORT).assigned(), Fluids.WATER,
                    "the water port of a plant on heavy oil cracking");
            helper.assertValueEqual(plant.inputTank(1).assigned(), ModFluids.HEAVY_OIL.get(),
                    "the other port of a plant on heavy oil cracking");
            helper.assertValueEqual(plant.outputTank(0).assigned(), ModFluids.LIGHT_OIL.get(),
                    "the first output of a plant on heavy oil cracking");
            helper.assertTrue(plant.outputTank(1).assigned() == null, "the second output of a plant on heavy oil cracking");
            helper.succeed();
        });
    }

    /**
     * How much empty world to leave around a test that builds a machine. A pumpjack is three
     * tiles across and a field of wells is twenty; without room, one test's blocks land in the
     * next test along.
     */
    private static final int PADDING = 24;

    /** Room for the field test, which builds a platform twenty-four blocks square. */
    private static final int WIDE_PADDING = 40;

    /** Where a well sits in the machine tests, and where the pumpjack over it is anchored. */
    private static final BlockPos WELL = new BlockPos(2, 1, 2);
    private static final BlockPos PUMPJACK = WELL.above();

    // --- helpers ------------------------------------------------------------------------------

    private static FluidNetworkManager grid(GameTestHelper helper) {
        return FluidNetworkManager.of(helper.getLevel());
    }

    private static FluidNetwork networkAt(GameTestHelper helper, BlockPos pos, String what) {
        FluidNetwork network = grid(helper).networkAt(helper.absolutePos(pos));
        helper.assertTrue(network != null, what);
        return network;
    }

    private static void pipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.PIPE.get());
    }

    /** A well with a known amount under it, so a test's numbers do not depend on the world seed. */
    private static CrudeOilBlockEntity well(GameTestHelper helper, BlockPos pos, long amount) {
        helper.setBlock(pos, ModBlocks.CRUDE_OIL.get());
        CrudeOilBlockEntity well = helper.getBlockEntity(pos, CrudeOilBlockEntity.class);
        well.reset(amount);
        return well;
    }

    /** A pumpjack anchored at {@code anchor}, facing north: its outlet is the north-east corner's north face. */
    private static PumpjackBlockEntity pumpjack(GameTestHelper helper, BlockPos anchor) {
        PumpjackBlock block = ModBlocks.PUMPJACK.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(PumpjackBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, PumpjackBlockEntity.class);
    }

    /** Fills the machine's buffer the way a pole would: through the insert-only grid view. */
    private static void charge(PumpjackBlockEntity pumpjack) {
        try (Transaction transaction = Transaction.openRoot()) {
            pumpjack.gridView().insert(PumpjackBlockEntity.ENERGY_CAPACITY, transaction);
            transaction.commit();
        }
    }

    /** Takes oil off the machine the way a pipe would: through the extract-only outlet view. */
    private static int draw(PumpjackBlockEntity pumpjack, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = pumpjack.output().extract(FluidResource.of(ModFluids.CRUDE_OIL.get()), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /**
     * How much a pumpjack has banked after this many cycles from a well that started at
     * {@code initial}: ten times the yield each cycle, with the fraction carried rather than
     * rounded. A 100% well gives ten, then 9.9997 - so nineteen after two cycles, not twenty, and
     * the missing three ten-thousandths turn up in a later cycle. That is Factorio's total to
     * within a unit, and the carry is what makes it exact over time.
     */
    private static int bankedAfter(long initial, int cycles) {
        long due = 0;
        long amount = initial;
        for (int cycle = 0; cycle < cycles; cycle++) {
            due += amount;
            amount -= CrudeOilBlockEntity.DEPLETION;
        }
        return (int) (due / PumpjackBlockEntity.UNIT_DIVISOR);
    }

    /** Where the offshore pump tests put the shore, the machine, and the water its intake reaches. */
    private static final BlockPos PUMP = new BlockPos(2, 2, 3);
    private static final BlockPos INTAKE_WATER = new BlockPos(2, 1, 2);

    /** Natural water: the still water the world makes, and the only kind an offshore pump draws. */
    private static BlockState naturalWater() {
        return ModBlocks.WATER.get().defaultBlockState();
    }

    /**
     * A stone platform at y 1, so a machine has something to stand on and water something to lie
     * in - and stone under it at y 0, so water set into the platform has a bed and stays where it
     * was put rather than pouring into the space below and turning up, flowing, two blocks under
     * an intake.
     */
    private static void platform(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    /** An offshore pump anchored at {@code anchor}, facing north: its intake is one block north, its outlet the body's south face. */
    private static OffshorePumpBlockEntity offshorePump(GameTestHelper helper, BlockPos anchor) {
        OffshorePumpBlock block = ModBlocks.OFFSHORE_PUMP.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(OffshorePumpBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, OffshorePumpBlockEntity.class);
    }

    /** Takes water off the pump the way a pipe would: through the extract-only outlet view. */
    private static int drawWater(OffshorePumpBlockEntity pump, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = pump.output().extract(FluidResource.of(Fluids.WATER), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /**
     * What the offshore pump would place as if a player clicked the top of {@code ground}, asked
     * exactly the way a right-click asks it. No player, so the facing is north and the intake
     * lands one block north of the body.
     */
    private static @Nullable BlockState pumpPlacement(GameTestHelper helper, BlockPos ground) {
        return ModBlocks.OFFSHORE_PUMP.get().getStateForPlacement(pumpClick(helper, ground));
    }

    /** The click itself: the top of {@code ground}, with an offshore pump in hand and no player. */
    private static BlockPlaceContext pumpClick(GameTestHelper helper, BlockPos ground) {
        BlockPos below = helper.absolutePos(ground);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        return new BlockPlaceContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.OFFSHORE_PUMP.get()), hit);
    }

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    /**
     * What the pumpjack would place as if a player clicked the top of {@code ground}, asked
     * exactly the way a right-click asks it. No player, so the facing is north.
     */
    private static @Nullable BlockState placement(GameTestHelper helper, BlockPos ground) {
        return ModBlocks.PUMPJACK.get().getStateForPlacement(pumpjackClick(helper, ground));
    }

    /** The click itself: the top of {@code ground}, with a pumpjack in hand and no player. */
    private static BlockPlaceContext pumpjackClick(GameTestHelper helper, BlockPos ground) {
        BlockPos below = helper.absolutePos(ground);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        return new BlockPlaceContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.PUMPJACK.get()), hit);
    }

    // --- the tank and the oil machines ----------------------------------------------------------

    /**
     * Where the oil machine tests put the anchor: the middle of a seven-block platform at y 1, so
     * a refinery's twenty-five cells and a tank's nine both fit with a block to spare.
     */
    private static final BlockPos MACHINE = new BlockPos(3, 2, 3);

    private static StorageTankBlockEntity storageTank(GameTestHelper helper, BlockPos anchor) {
        StorageTankBlock block = ModBlocks.STORAGE_TANK.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(StorageTankBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, StorageTankBlockEntity.class);
    }

    /** A refinery facing north: outputs on the north row, inputs on the south. */
    private static OilRefineryBlockEntity refinery(GameTestHelper helper, BlockPos anchor) {
        OilRefineryBlock block = ModBlocks.OIL_REFINERY.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(ProcessingBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, OilRefineryBlockEntity.class);
    }

    private static ChemicalPlantBlockEntity chemicalPlant(GameTestHelper helper, BlockPos anchor) {
        ChemicalPlantBlock block = ModBlocks.CHEMICAL_PLANT.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(ProcessingBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, ChemicalPlantBlockEntity.class);
    }

    /** Fills the machine's buffer the way a pole would: through the insert-only grid view. */
    private static void charge(ProcessingBlockEntity machine) {
        try (Transaction transaction = Transaction.openRoot()) {
            machine.gridView().insert(machine.energyCapacity(), transaction);
            transaction.commit();
        }
    }

    /** Puts fluid into a tank the way a pipe run would, and says how much it took. */
    private static int fill(ResourceHandler<FluidResource> tank, Fluid fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = tank.insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /** Takes fluid out of a tank the way a pipe run would, and says how much it got. */
    private static int drain(ResourceHandler<FluidResource> tank, Fluid fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = tank.extract(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    private static ResourceKey<Recipe<?>> recipe(String namespace, String name) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(namespace, name));
    }

    // --- pipes --------------------------------------------------------------------------------

    /** A line of pipes is one object, whatever its length: one thing to tick, one capacity. */
    public static class PipeRunIsOneObjectTest extends PackGameTest {

        private static final int LENGTH = 5;

        PipeRunIsOneObjectTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < LENGTH; x++) {
                pipe(helper, new BlockPos(x, 1, 0));
            }

            helper.runAfterDelay(5, () -> {
                FluidNetwork first = networkAt(helper, new BlockPos(0, 1, 0), "the first pipe has no run");
                FluidNetwork last = networkAt(helper, new BlockPos(LENGTH - 1, 1, 0),
                        "the last pipe has no run");

                helper.assertTrue(first == last,
                        "a straight line of pipes is more than one object, so a long run would cost "
                                + "a tick per pipe and take a tick per pipe to cross");
                helper.assertValueEqual(first.pipeCount(), LENGTH, "pipes in the run");
                helper.assertValueEqual(first.capacity(),
                        LENGTH * FluidNetwork.CAPACITY_PER_PIPE,
                        "how much the run can hold - a pipeline is one tank as long as itself");
                helper.succeed();
            });
        }

    }

    /**
     * Breaking a line splits the run; joining it merges them again.
     *
     * <p>The expensive-to-get-wrong half, exactly as for poles. A split that never happens leaves
     * two disconnected halves sharing one tank, which is a pipeline that carries fluid through a
     * gap.
     */
    public static class PipeRunSplitsAndMergesTest extends PackGameTest {

        private static final BlockPos LEFT = new BlockPos(0, 1, 0);
        private static final BlockPos MIDDLE = new BlockPos(1, 1, 0);
        private static final BlockPos RIGHT = new BlockPos(2, 1, 0);

        PipeRunSplitsAndMergesTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            pipe(helper, LEFT);
            pipe(helper, MIDDLE);
            pipe(helper, RIGHT);

            helper.startSequence()
                    .thenExecuteAfter(5, () -> helper.assertTrue(
                            networkAt(helper, LEFT, "no run") == networkAt(helper, RIGHT, "no run"),
                            "three pipes in a row are not one run"))
                    .thenExecute(() -> helper.setBlock(MIDDLE, Blocks.AIR))
                    .thenExecuteAfter(5, () -> {
                        FluidNetwork left = networkAt(helper, LEFT, "the left pipe lost its run");
                        FluidNetwork right = networkAt(helper, RIGHT, "the right pipe lost its run");
                        helper.assertFalse(left == right,
                                "breaking the pipe between two halves left them on one run, so "
                                        + "fluid would cross a gap that is not there");
                        helper.assertValueEqual(left.pipeCount(), 1, "pipes in the left half");
                    })
                    .thenExecute(() -> pipe(helper, MIDDLE))
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(
                                networkAt(helper, LEFT, "no run") == networkAt(helper, RIGHT, "no run"),
                                "putting the pipe back did not join the two halves");
                        helper.assertValueEqual(
                                networkAt(helper, LEFT, "no run").pipeCount(), 3,
                                "pipes in the rejoined run");
                    })
                    .thenSucceed();
        }

    }

    /**
     * A pipe reaches towards what offers fluid, and not towards what does not.
     *
     * <p>The connection is in the block state, so it is what the player sees. Reaching towards a
     * plain stone block would be a lie drawn in the world, and worse than no connection at all.
     */
    public static class PipeConnectsTest extends PackGameTest {

        private static final BlockPos PIPE = new BlockPos(1, 1, 0);
        private static final BlockPos NEIGHBOUR_PIPE = new BlockPos(2, 1, 0);
        private static final BlockPos STONE = new BlockPos(0, 1, 0);

        PipeConnectsTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(STONE, Blocks.STONE);
            pipe(helper, PIPE);
            pipe(helper, NEIGHBOUR_PIPE);

            helper.runAfterDelay(5, () -> {
                var state = helper.getLevel().getBlockState(helper.absolutePos(PIPE));
                helper.assertTrue(state.getValue(PipeBlock.EAST),
                        "a pipe does not reach towards the pipe beside it");
                helper.assertFalse(state.getValue(PipeBlock.WEST),
                        "a pipe reaches towards a stone block, which offers it nothing");
                helper.assertFalse(state.getValue(PipeBlock.UP),
                        "a pipe reaches towards thin air");
                helper.succeed();
            });
        }

    }

    /**
     * The numbers behind the readout, which cannot be asserted any other way.
     *
     * <p>Jade's tooltip is drawn on the client from data the server sends, and neither half can be
     * reached headlessly. What <em>can</em> be reached is what the server would send - the run's
     * extent and its capacity - and that is where the bug would be: a readout reporting the block
     * rather than the run would say a pipe holds nothing, for ever, and look perfectly reasonable
     * doing it.
     */
    public static class PipeReportsItsRunTest extends PackGameTest {

        private static final int LENGTH = 6;

        PipeReportsItsRunTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < LENGTH; x++) {
                pipe(helper, new BlockPos(x, 1, 0));
            }

            helper.runAfterDelay(5, () -> {
                // Asked from the far end, because every pipe in a run must answer for the whole
                // run - that is what makes the readout worth having.
                FluidNetwork run = networkAt(helper, new BlockPos(LENGTH - 1, 1, 0),
                        "the last pipe has no run");

                helper.assertValueEqual(run.pipeCount(), LENGTH,
                        "the extent the readout would print");
                helper.assertValueEqual(run.capacity(), LENGTH * FluidNetwork.CAPACITY_PER_PIPE,
                        "the capacity the readout would print");
                helper.assertValueEqual(run.amount(), 0, "what an unconnected run is holding");
                helper.assertTrue(run.fluid().isEmpty(),
                        "an empty run names a fluid, so the readout would claim to hold something");
                helper.succeed();
            });
        }

    }

    // --- oil ----------------------------------------------------------------------------------

    /**
     * A well is pumped down to its floor and never past it, and at the floor it still pumps.
     *
     * <p>Factorio's minimum is 60000 - a fifth of normal, so two a second - or a fifth of what the
     * well started with, whichever is more. The well here starts one cycle above the minimum: the
     * first cycle reaches the floor, the second finds it there and takes nothing more, and both
     * cycles bank the same two units. The second well checks the other arm of the rule.
     */
    public static class WellStopsAtItsFloorTest extends PackGameTest {

        private static final BlockPos RICH_WELL = new BlockPos(8, 1, 2);

        WellStopsAtItsFloorTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            CrudeOilBlockEntity well = well(helper, WELL,
                    CrudeOilBlockEntity.MINIMUM + CrudeOilBlockEntity.DEPLETION);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            CrudeOilBlockEntity rich = well(helper, RICH_WELL, 4 * CrudeOilBlockEntity.NORMAL);
            helper.assertValueEqual(rich.floor(), 4 * CrudeOilBlockEntity.NORMAL / 5,
                    "the floor of a 400% well - a fifth of what it started with");
            helper.assertValueEqual(well.floor(), CrudeOilBlockEntity.MINIMUM,
                    "the floor of a well that started near the minimum");

            helper.startSequence()
                    .thenExecuteAfter(30, () -> {
                        helper.assertValueEqual(well.amount(), CrudeOilBlockEntity.MINIMUM,
                                "a well pumped down to Factorio's minimum");
                        helper.assertTrue(well.isAtFloor(), "a well at the minimum does not say so");
                        helper.assertValueEqual(pumpjack.stored(), 2, "two a cycle at 20%");
                    })
                    .thenExecuteAfter(20, () -> {
                        helper.assertValueEqual(well.amount(), CrudeOilBlockEntity.MINIMUM,
                                "a well pumped past its floor - Factorio's oil never runs dry");
                        helper.assertValueEqual(pumpjack.stored(), 4, "a well at its floor still pumps");
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.PUMPING,
                                "a pumpjack on a floored well stopped");
                    })
                    .thenSucceed();
        }

    }

    /**
     * A pumpjack costs nothing when it cannot work, and wakes for each of the three things that
     * give it work back.
     */
    public static class PumpjackSleepsTest extends PackGameTest {

        /** Five hundred units a cycle: 50 times normal. */
        private static final long GUSHER = 50 * CrudeOilBlockEntity.NORMAL;

        PumpjackSleepsTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(WELL, Blocks.STONE);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            Block block = ModBlocks.PUMPJACK.get();
            CrudeOilBlockEntity[] well = new CrudeOilBlockEntity[1];

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.NO_WELL,
                                "a pumpjack on stone does not know it has no well");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with no well is still ticking");
                    })
                    .thenExecute(() -> {
                        well[0] = well(helper, WELL, GUSHER);
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "a well appearing under a pumpjack did not wake it");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.NO_POWER,
                                "an unpowered pumpjack over a well does not say it lacks power");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with no power is still ticking");
                    })
                    .thenExecute(() -> {
                        charge(pumpjack);
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "power arriving did not wake the pumpjack in the same tick");
                    })
                    .thenExecuteAfter(70, () -> {
                        // Two cycles all but filled the tank - a hair under, since the second ran
                        // at a hair under fifty times normal - and the third finished and could
                        // not bank. A held cycle takes nothing off the well: two cycles' worth.
                        helper.assertTrue(pumpjack.stored() > PumpjackBlockEntity.TANK_CAPACITY - 500,
                                "the tank has room for a cycle and the pumpjack stopped anyway: " + pumpjack.stored());
                        helper.assertValueEqual(pumpjack.status(), PumpjackStatus.OUTPUT_FULL,
                                "a pumpjack with a full tank does not say so");
                        helper.assertFalse(isScheduled(helper, PUMPJACK, block),
                                "a pumpjack with a full tank is still ticking");
                        helper.assertValueEqual(well[0].amount(), GUSHER - 2 * CrudeOilBlockEntity.DEPLETION,
                                "a cycle that could not bank took oil off the well anyway");
                    })
                    .thenExecute(() -> {
                        helper.assertValueEqual(draw(pumpjack, 500), 500, "oil drawn off a full pumpjack");
                        helper.assertTrue(isScheduled(helper, PUMPJACK, block),
                                "drawing from the outlet did not wake the pumpjack");
                    })
                    .thenExecuteAfter(5, () -> {
                        helper.assertValueEqual(well[0].amount(), GUSHER - 3 * CrudeOilBlockEntity.DEPLETION,
                                "the held cycle was not banked once there was room");
                        helper.assertTrue(pumpjack.stored() > PumpjackBlockEntity.TANK_CAPACITY - 500,
                                "the held cycle's oil is not in the tank: " + pumpjack.stored());
                    })
                    .thenSucceed();
        }

    }

    /**
     * A generated field is a field: several wells, each far enough from the next for a pumpjack,
     * each on a levelled pad a pumpjack fits over.
     *
     * <p>The feature is asked to place a field on a flat platform, which is the case where the
     * levelling does nothing; the point is the spacing and the fit, which are what make a field
     * something you lay pumpjacks out on. Every well must accept a pumpjack centred on it.
     */
    public static class OilFieldIsPumpableTest extends PackGameTest {

        private static final int SIZE = 24;

        OilFieldIsPumpableTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.SHORT_GRASS);
                }
            }
            BlockPos origin = helper.absolutePos(new BlockPos(SIZE / 2, 2, SIZE / 2));

            List<BlockPos> wells = CrudeOilFieldFeature.placeField(helper.getLevel(),
                    RandomSource.create(20260905L), origin);

            helper.assertTrue(wells.size() >= CrudeOilFieldFeature.MIN_WELLS,
                    "a field of " + wells.size() + " wells, fewer than a field has");
            for (BlockPos well : wells) {
                helper.assertTrue(helper.getLevel().getBlockState(well).is(ModBlocks.CRUDE_OIL.get()),
                        "a well the feature reported is not there: " + well);
                for (BlockPos other : wells) {
                    if (other != well) {
                        int apart = Math.max(Math.abs(other.getX() - well.getX()), Math.abs(other.getZ() - well.getZ()));
                        helper.assertTrue(apart >= CrudeOilFieldFeature.WELL_SPACING,
                                "two wells " + apart + " apart, too close for two pumpjacks");
                    }
                }
                // The grass over the pad was cleared, so a pumpjack fits over every well.
                BlockPos relative = well.subtract(helper.absolutePos(BlockPos.ZERO));
                BlockState placed = placement(helper, relative);
                helper.assertTrue(placed != null, "a pumpjack does not fit over a generated well at " + well);
                CrudeOilBlockEntity entity = helper.getBlockEntity(relative, CrudeOilBlockEntity.class);
                helper.assertTrue(entity.amount() >= CrudeOilBlockEntity.MINIMUM,
                        "a generated well is below Factorio's minimum");
            }
            helper.succeed();
        }

    }

    /**
     * A cycle produces at most a tankful, however rich the well.
     *
     * <p>Factorio caps a pumpjack's cycle at its fluid box volume, and so does this one - which is
     * also what keeps a pumpjack working at all on a well far from the origin, where the
     * distance factor makes a cycle worth more than the tank holds. Without the cap such a machine
     * finished its first cycle, found the oil would not fit, and said <em>Full</em> over an empty
     * tank for ever. The gametest world is millions of blocks out, which is how this was found.
     */
    public static class PumpjackCapsACycleTest extends PackGameTest {

        /** Two thousand a cycle, uncapped: two hundred times normal. */
        private static final long MONSTER = 200 * CrudeOilBlockEntity.NORMAL;

        PumpjackCapsACycleTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            CrudeOilBlockEntity well = well(helper, WELL, MONSTER);
            PumpjackBlockEntity pumpjack = pumpjack(helper, PUMPJACK);
            charge(pumpjack);

            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(pumpjack.stored(), PumpjackBlockEntity.TANK_CAPACITY,
                        "what one cycle on a monster well banked - a tankful, no more and not nothing");
                helper.assertValueEqual(well.amount(), MONSTER - CrudeOilBlockEntity.DEPLETION,
                        "a capped cycle still takes one cycle off the well");
                helper.succeed();
            });
        }

    }

    /**
     * {@code /oil field} puts a field where the command is run, the way worldgen would have.
     *
     * <p>The tool for a superflat world, which runs no features and so has no oil, and for a
     * playtest. It goes through the dispatcher from a source standing on the platform, so what is
     * asserted is the command as typed: that it exists, that it needs no arguments, and that a
     * field of wells is there afterwards on the ground the source stood on.
     */
    public static class OilCommandPlacesAFieldTest extends PackGameTest {

        private static final int SIZE = 24;

        OilCommandPlacesAFieldTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                }
            }
            MinecraftServer server = helper.getLevel().getServer();
            BlockPos standing = helper.absolutePos(new BlockPos(SIZE / 2, 2, SIZE / 2));
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withLevel(helper.getLevel())
                            .withPosition(Vec3.atBottomCenterOf(standing)).withSuppressedOutput(),
                    "oil field");

            int found = 0;
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    if (helper.getBlockState(new BlockPos(x, 1, z)).is(ModBlocks.CRUDE_OIL.get())) {
                        found++;
                    }
                }
            }
            helper.assertTrue(found >= CrudeOilFieldFeature.MIN_WELLS,
                    "/oil field left " + found + " wells in the ground, fewer than a field has");
            helper.succeed();
        }

    }

    // --- natural water --------------------------------------------------------------------

    /**
     * Two natural sources a block apart never make a third; two of vanilla's do.
     *
     * <p>Minecraft's infinite water is this one rule, and it is the rule Factorio does not have:
     * water is where the map put it. Both troughs are built the same and only the water differs,
     * so the vanilla one is the control - if the game rule ever stopped vanilla water converting,
     * the natural trough would pass for the wrong reason and the control would say so.
     */
    public static class NaturalWaterMakesNoNewSourceTest extends PackGameTest {

        private static final int NATURAL_ROW = 1;
        private static final int VANILLA_ROW = 5;

        NaturalWaterMakesNoNewSourceTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            trough(helper, NATURAL_ROW, naturalWater());
            trough(helper, VANILLA_ROW, Blocks.WATER.defaultBlockState());

            helper.runAfterDelay(40, () -> {
                FluidState natural = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 2, NATURAL_ROW)));
                helper.assertTrue(natural.getType() == ModFluids.FLOWING_WATER.get(),
                        "the gap between two natural sources holds " + natural.getType() + ", not flowing natural water");
                helper.assertFalse(natural.isSource(),
                        "natural water made a new source, which is the one thing it must never do");

                FluidState vanilla = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 2, VANILLA_ROW)));
                helper.assertTrue(vanilla.isSource() && vanilla.getType() == Fluids.WATER,
                        "vanilla water no longer makes a source between two, so the comparison proves nothing");
                helper.succeed();
            });
        }

        /** A stone channel three long at y 2, a source at each end and air between. */
        private static void trough(GameTestHelper helper, int row, BlockState water) {
            for (int x = 0; x < 5; x++) {
                for (int z = row - 1; z <= row + 1; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                }
            }
            helper.setBlock(new BlockPos(2, 2, row), Blocks.AIR);
            helper.setBlock(new BlockPos(1, 2, row), water);
            helper.setBlock(new BlockPos(3, 2, row), water);
        }

    }

    /**
     * Worldgen's water becomes natural water, level for level, and nothing else is touched.
     *
     * <p>The feature is handed a live chunk rather than a generating one - the sections are the
     * same objects - holding what a sea floor holds: still water, a flow, a fall, a waterlogged
     * block and ice. The three waters change block and keep their level; the slab keeps the
     * vanilla water inside it, because that water is the slab's and not the world's; the ice
     * stays ice.
     */
    public static class WorldgenWaterBecomesNaturalTest extends PackGameTest {

        private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
        private static final BlockPos FLOW = new BlockPos(2, 2, 1);
        private static final BlockPos FALL = new BlockPos(3, 2, 1);
        private static final BlockPos SLAB = new BlockPos(1, 2, 3);
        private static final BlockPos ICE = new BlockPos(2, 2, 3);

        WorldgenWaterBecomesNaturalTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // A basin: a floor, a ring of stone at water level, and the contents.
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    if (x == 0 || x == 4 || z == 0 || z == 4) {
                        helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    }
                }
            }
            helper.setBlock(SOURCE, Blocks.WATER.defaultBlockState());
            helper.setBlock(FLOW, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
            helper.setBlock(FALL, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
            helper.setBlock(SLAB, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            helper.setBlock(ICE, Blocks.ICE.defaultBlockState());

            // The basin may straddle a chunk border; every chunk it touches gets the treatment,
            // as every chunk does in a generating world.
            Set<ChunkAccess> chunks = new HashSet<>();
            for (BlockPos pos : List.of(SOURCE, FLOW, FALL, SLAB, ICE)) {
                chunks.add(helper.getLevel().getChunk(helper.absolutePos(pos)));
            }
            int replaced = 0;
            for (ChunkAccess chunk : chunks) {
                replaced += NaturalWaterFeature.replace(chunk);
            }
            helper.assertValueEqual(replaced, 3, "water blocks the feature reported replacing");

            LiquidBlock natural = ModBlocks.WATER.get();
            helper.assertTrue(helper.getBlockState(SOURCE).is(natural), "a source stayed " + helper.getBlockState(SOURCE));
            helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(LiquidBlock.LEVEL), 0, "a source's level");
            helper.assertTrue(helper.getBlockState(FLOW).is(natural), "a flow stayed " + helper.getBlockState(FLOW));
            helper.assertValueEqual(helper.getBlockState(FLOW).getValue(LiquidBlock.LEVEL), 3, "a flow's level");
            helper.assertTrue(helper.getBlockState(FALL).is(natural), "a fall stayed " + helper.getBlockState(FALL));
            helper.assertValueEqual(helper.getBlockState(FALL).getValue(LiquidBlock.LEVEL), 8, "a fall's level");
            helper.assertTrue(helper.getBlockState(SLAB).is(Blocks.OAK_SLAB)
                            && helper.getBlockState(SLAB).getValue(BlockStateProperties.WATERLOGGED),
                    "a waterlogged slab became " + helper.getBlockState(SLAB));
            helper.assertTrue(helper.getBlockState(ICE).is(Blocks.ICE), "ice became " + helper.getBlockState(ICE));
            helper.succeed();
        }

    }

    // --- the offshore pump ----------------------------------------------------------------

    /**
     * Forty water a tick, for nothing.
     *
     * <p>Factorio's pump gives twenty boilers' worth and needs no power, and both are asserted:
     * the tank rises by exactly two ticks' worth between two readings taken two ticks apart,
     * from a machine that has been given no electricity and no fuel because it has nowhere to
     * put either.
     */
    public static class OffshorePumpPumpsAtFactorioRateTest extends PackGameTest {

        private int firstReading;

        OffshorePumpPumpsAtFactorioRateTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 5);
            helper.setBlock(INTAKE_WATER, naturalWater());
            OffshorePumpBlockEntity pump = offshorePump(helper, PUMP);

            helper.startSequence()
                    .thenExecuteAfter(2, () -> firstReading = pump.stored())
                    .thenExecuteAfter(2, () -> {
                        helper.assertValueEqual(pump.stored() - firstReading, 2 * OffshorePumpBlockEntity.WATER_PER_TICK,
                                "water banked over two ticks");
                        helper.assertValueEqual(pump.status(), OffshorePumpStatus.PUMPING, "status while pumping");
                    })
                    .thenSucceed();
        }

    }

    /**
     * A click on a lake puts the pump on the lake, not under it.
     *
     * <p>A block in hand looks through water, so the click lands on the bed and vanilla would
     * place just above it, on the bottom. The pump's item lifts the placement to the air over the
     * surface, and the block then finds the water under its intake and stands.
     */
    public static class OffshorePumpFloatsOnALakeTest extends PackGameTest {

        private static final BlockPos BED = new BlockPos(2, 0, 2);
        private static final int DEPTH = 3;

        OffshorePumpFloatsOnALakeTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            // A pool three deep and three across, on a stone bed.
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y <= DEPTH; y++) {
                        helper.setBlock(new BlockPos(x, y, z), naturalWater());
                    }
                }
            }

            BlockPos bed = helper.absolutePos(BED);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false);
            BlockPlaceContext clicked = new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.OFFSHORE_PUMP.get()), hit);
            helper.assertValueEqual(clicked.getClickedPos(), bed.above(),
                    "where vanilla would place a block clicked onto a lake bed");

            BlockPlaceContext lifted = ModItems.OFFSHORE_PUMP.get().updatePlacementContext(clicked);
            helper.assertTrue(lifted != null, "the pump's item refused the click altogether");
            helper.assertValueEqual(lifted.getClickedPos(), bed.above(DEPTH + 1),
                    "where the pump's item lifts a click on a lake bed to");

            BlockState placed = ModBlocks.OFFSHORE_PUMP.get().getStateForPlacement(lifted);
            helper.assertTrue(placed != null, "a pump lifted to the surface of a lake will not stand there");
            helper.assertValueEqual(placed.getValue(OffshorePumpShape.SHAPE.part()), OffshorePumpShape.BODY_CELL,
                    "the cell that lands on the lifted click");
            helper.succeed();
        }

    }

    // --- the oil chain's recipes ------------------------------------------------------------

    /** Every recipe of the oil chain is in the running recipe manager, as a timed recipe. */
    public static class OilRecipesLoadTest extends PackGameTest {

        private static final Identifier FACRAFT = Identifier.fromNamespaceAndPath("facrafting", "facraft");

        OilRecipesLoadTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
            for (String name : new String[] {"basic_oil_processing", "advanced_oil_processing",
                    "heavy_oil_cracking", "light_oil_cracking", "solid_fuel_from_heavy_oil",
                    "solid_fuel_from_light_oil", "solid_fuel_from_petroleum_gas", "lubricant",
                    "sulfuric_acid", "explosives", "oil_refinery", "chemical_plant", "storage_tank"}) {
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(NauvisFluids.MODID, name));
                RecipeHolder<?> holder = recipes.byKey(key).orElse(null);
                helper.assertTrue(holder != null, "nauvis_fluids:" + name + " did not load");
                Identifier serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
                helper.assertValueEqual(serializer, FACRAFT, "the recipe type of nauvis_fluids:" + name);
            }
            helper.succeed();
        }

    }

    // --- the storage tank and the oil machines -------------------------------------------------

    /**
     * A tank on a run settles at the run's fraction full, in one step, and then sleeps.
     *
     * <p>The claim the tank rests on: it is a length of the pipeline, not a sink the run pours
     * into or a source it pours out of. Two pipes and a tank are twenty-five thousand two hundred
     * of capacity, so a full tank leaves a hundred and ninety-nine in the pipes; empty the tank
     * and those flow back until the pipes hold two. Either way the run goes dormant afterwards,
     * because nothing moves on the second visit.
     */
    public static class StorageTankLevelsWithItsRunTest extends PackGameTest {

        /** The north-west corner's north face is a connection; the pipes run north from it. */
        private static final BlockPos NEAR_PIPE = new BlockPos(2, 2, 1);
        private static final BlockPos FAR_PIPE = new BlockPos(2, 2, 0);
        /** The corner's west face is not, and a pipe there stays unconnected. */
        private static final BlockPos FLANK_PIPE = new BlockPos(1, 2, 2);

        StorageTankLevelsWithItsRunTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 7);
            pipe(helper, NEAR_PIPE);
            pipe(helper, FAR_PIPE);
            pipe(helper, FLANK_PIPE);
            StorageTankBlockEntity tank = storageTank(helper, MACHINE);
            int total = StorageTankBlockEntity.CAPACITY;
            int combined = total + 2 * FluidNetwork.CAPACITY_PER_PIPE;

            helper.startSequence()
                    .thenExecuteAfter(5, () -> {
                        helper.assertTrue(helper.getBlockState(NEAR_PIPE).getValue(PipeBlock.SOUTH),
                                "a pipe at the tank's connection does not reach into it");
                        helper.assertFalse(helper.getBlockState(FLANK_PIPE).getValue(PipeBlock.EAST),
                                "a pipe on a face that is not a connection reaches into the tank");
                        helper.assertValueEqual(fill(tank.tank(), Fluids.WATER, total), total, "filling the tank");
                    })
                    .thenExecuteAfter(30, () -> {
                        FluidNetwork run = networkAt(helper, NEAR_PIPE, "the connected pipe has no run");
                        helper.assertValueEqual(run.fluid().getFluid(), Fluids.WATER, "what the run took from the tank");
                        int tankShare = (int) ((long) total * StorageTankBlockEntity.CAPACITY / combined);
                        helper.assertValueEqual(tank.stored(), tankShare, "the tank after levelling");
                        helper.assertValueEqual(run.amount(), total - tankShare, "the run after levelling");
                        helper.assertFalse(grid(helper).isActive(run), "a levelled run is still ticking");
                        helper.assertValueEqual(drain(tank.tank(), Fluids.WATER, tank.stored()), tankShare,
                                "emptying the tank");
                    })
                    .thenExecuteAfter(30, () -> {
                        FluidNetwork run = networkAt(helper, NEAR_PIPE, "the connected pipe has no run");
                        int left = total - (int) ((long) total * StorageTankBlockEntity.CAPACITY / combined);
                        int tankShare = (int) ((long) left * StorageTankBlockEntity.CAPACITY / combined);
                        helper.assertValueEqual(tank.stored(), tankShare, "the tank after the run levelled back into it");
                        helper.assertValueEqual(run.amount(), left - tankShare, "the run after levelling back");
                        helper.assertFalse(grid(helper).isActive(run), "a re-levelled run is still ticking");
                    })
                    .thenSucceed();
        }

    }

    /**
     * A refinery on basic oil processing turns a hundred crude into forty-five petroleum gas every
     * five seconds, takes the crude at its right-hand input and gives the gas at its right-hand
     * output - Factorio's ports, which is what lets a player add advanced processing's pipes
     * without moving these - and stops when the crude runs out.
     */
    public static class RefineryRunsBasicOilProcessingTest extends PackGameTest {

        /** The crude input: the fourth cell of the south row, on its south face. */
        private static final BlockPos CRUDE_CELL = new BlockPos(4, 2, 5);
        /** The petroleum output: the north-east corner, on its north face. */
        private static final BlockPos PETROLEUM_CELL = new BlockPos(5, 2, 1);

        RefineryRunsBasicOilProcessingTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            platform(helper, 7);
            OilRefineryBlockEntity refinery = refinery(helper, MACHINE);
            charge(refinery);
            refinery.setRecipe(recipe(NauvisFluids.MODID, "basic_oil_processing"));
            Fluid crude = ModFluids.CRUDE_OIL.get();
            Fluid petroleum = ModFluids.PETROLEUM_GAS.get();

            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                    helper.absolutePos(CRUDE_CELL), Direction.SOUTH) != null, "no port on the crude cell's south face");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                    helper.absolutePos(CRUDE_CELL), Direction.NORTH) == null, "a port on the crude cell's inside face");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                    helper.absolutePos(PETROLEUM_CELL), Direction.NORTH) != null, "no port on the petroleum cell's north face");
            helper.assertValueEqual(fill(refinery.inputAccess(OilRefineryBlockEntity.CRUDE_PORT), crude, 200), 200,
                    "crude into the crude port");
            helper.assertValueEqual(fill(refinery.inputAccess(OilRefineryBlockEntity.WATER_PORT), Fluids.WATER, 50), 0,
                    "water into a port basic oil processing does not use");
            helper.assertValueEqual(fill(refinery.inputAccess(OilRefineryBlockEntity.WATER_PORT), crude, 50), 0,
                    "crude into the water port");

            helper.startSequence()
                    .thenExecuteAfter(110, () -> {
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT).getAmountAsInt(0), 45,
                                "petroleum gas after one craft");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.CRUDE_PORT).getAmountAsInt(0), 100,
                                "crude after one craft");
                        // The buffer is five seconds of draw and the recipe is five seconds long, so
                        // one charge is exactly one craft: the gas was banked, and the next craft waits.
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_POWER, "status after one charge's worth");
                        charge(refinery);
                    })
                    .thenExecuteAfter(105, () -> {
                        helper.assertValueEqual(refinery.outputTank(OilRefineryBlockEntity.PETROLEUM_PORT).getAmountAsInt(0), 90,
                                "petroleum gas after two crafts");
                        helper.assertValueEqual(refinery.inputTank(OilRefineryBlockEntity.CRUDE_PORT).getAmountAsInt(0), 0,
                                "crude after two crafts");
                        helper.assertValueEqual(refinery.status(), ProcessingStatus.NO_INGREDIENTS, "status with the crude gone");
                        helper.assertFalse(isScheduled(helper, MACHINE, ModBlocks.OIL_REFINERY.get()),
                                "a refinery out of crude is still asking for ticks");
                        helper.assertValueEqual(drain(refinery.outputAccess(OilRefineryBlockEntity.PETROLEUM_PORT), petroleum, 90), 90,
                                "drawing the gas off at the petroleum port");
                    })
                    .thenSucceed();
        }

    }

    /**
     * A chemical plant on plastic turns twenty petroleum gas and a coal into two plastic bars a
     * second, and stops when the coal runs out. The recipe is Nauvis Materials', and
     * the test says so rather than failing when that mod is not in the run.
     */
    public static class ChemicalPlantMakesPlasticTest extends PackGameTest {

        private static final int OUTPUT_SLOT = ChemicalPlantBlockEntity.ITEM_INPUTS;

        ChemicalPlantMakesPlasticTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            if (!ModList.get().isLoaded("nauvis_materials")) {
                helper.succeed();  // no plastic to make without the mod that owns it
                return;
            }
            platform(helper, 7);
            ChemicalPlantBlockEntity plant = chemicalPlant(helper, MACHINE);
            charge(plant);
            plant.setRecipe(recipe("nauvis_materials", "plastic_bar"));
            helper.assertTrue(plant.recipeKey() != null, "the chemical plant refused plastic");
            Fluid petroleum = ModFluids.PETROLEUM_GAS.get();
            helper.assertValueEqual(fill(plant.inputAccess(0), petroleum, 100), 100, "petroleum gas into the first port");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(plant.items().insert(0, ItemResource.of(Items.COAL), 4, transaction), 4, "coal into the first slot");
                transaction.commit();
            }

            helper.startSequence()
                    .thenExecuteAfter(25, () -> {
                        helper.assertValueEqual(plant.items().getAmountAsInt(OUTPUT_SLOT), 2, "plastic after one craft");
                        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(plant.items().getResource(OUTPUT_SLOT).toStack(1).getItem()),
                                Identifier.fromNamespaceAndPath("nauvis_materials", "plastic_bar"), "what the plant made");
                        helper.assertValueEqual(plant.inputTank(0).getAmountAsInt(0), 80, "petroleum gas after one craft");
                    })
                    .thenExecuteAfter(65, () -> {
                        helper.assertValueEqual(plant.items().getAmountAsInt(OUTPUT_SLOT), 8, "plastic after four crafts");
                        helper.assertValueEqual(plant.items().getAmountAsInt(0), 0, "coal after four crafts");
                        helper.assertValueEqual(plant.inputTank(0).getAmountAsInt(0), 20, "petroleum gas after four crafts");
                        helper.assertValueEqual(plant.status(), ProcessingStatus.NO_INGREDIENTS, "status with the coal gone");
                        helper.assertFalse(isScheduled(helper, MACHINE, ModBlocks.CHEMICAL_PLANT.get()),
                                "a chemical plant out of coal is still asking for ticks");
                    })
                    .thenSucceed();
        }

    }

}
