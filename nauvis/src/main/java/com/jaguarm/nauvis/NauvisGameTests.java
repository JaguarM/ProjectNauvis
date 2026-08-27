package com.jaguarm.nauvis;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>This is the half of testing that needs nobody watching. What the pack mod asserts is that
 * the pack itself holds together; how a machine behaves is asserted in the mod that owns the
 * machine, beside it. How any of it looks is asserted nowhere, and never will be.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}: it starts a server with every mod in
 * the pack on one classpath, runs every test in every one of them, and exits non-zero if any
 * failed.
 *
 * <p>The 26.2 shape is registry-driven and unlike every tutorial. See {@code docs/API-26.2.md}
 * — in particular, {@code FunctionGameTestInstance} is unavailable to mods, because the
 * registry its bodies live in is bootstrapped during {@code BuiltInRegistries} static
 * initialisation, before any mod exists. Subclassing {@link GameTestInstance} is the way in.
 */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class NauvisGameTests {

    private NauvisGameTests() {}

    /**
     * A test whose structure is missing silently does not run — {@code placeStructure} returns
     * false and reports nothing. Minecraft ships {@code minecraft:empty}, which is all a test
     * needing no terrain requires.
     */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /**
     * Test types are a registry like any other, and the codec is what a datapack would use to
     * deserialise one. Ours are registered in code and never serialised, but the registry
     * entry still has to exist for the type to be legal.
     */
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Nauvis.MODID);

    static {
        TEST_TYPES.register("registry_presence", () -> RegistryPresenceTest.CODEC);
        TEST_TYPES.register("power_reaches_a_machine", () -> PowerReachesAMachineTest.CODEC);
    }

    /** Called from the mod constructor so the test type registers with everything else. */
    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        // Our own environment rather than a lookup of minecraft:default, because the event
        // exposes no getter for one that already exists. An empty AllOf imposes no conditions,
        // which is exactly what minecraft:default is.
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "pack_loads"),
                new RegistryPresenceTest(
                        new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true, Rotation.NONE),
                        List.of(
                                "neoprogressivematerials:iron_gear_wheel",
                                "neoprogressivematerials:electronic_circuit",
                                "neoprogressiveautomation:burner_drill",
                                "nauvis_machines:assembling_machine_1",
                                "nauvis_logistics:burner_inserter",
                                "nauvis_logistics:inserter",
                                "nauvis_logistics:iron_chest",
                                "nauvis_fluids:pipe",
                                "nauvis_power:steam_engine",
                                "nauvis_power:small_electric_pole")));

        // Padded: this one builds a factory eleven blocks long, well outside the point-sized
        // structure it is given, and a pole from the test next door would join its network.
        event.registerTest(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "power_reaches_a_machine"),
                new PowerReachesAMachineTest(new TestData<>(environment, EMPTY_STRUCTURE, 200, 0,
                        true, Rotation.NONE, false, 1, 1, false, 24)));
    }

    /**
     * Asserts that a list of item ids is registered once the server is up.
     *
     * <p>Gradle resolving the sibling jars onto the classpath is a different claim from their
     * items existing in the registry. This checks the second, which is the one that matters:
     * it fails if a mod silently declines to load.
     */
    public static class RegistryPresenceTest extends GameTestInstance {

        public static final MapCodec<RegistryPresenceTest> CODEC = RecordCodecBuilder.<RegistryPresenceTest>mapCodec(
                i -> i.group(
                        TestData.CODEC.forGetter(RegistryPresenceTest::info),
                        Codec.STRING.listOf().fieldOf("items")
                                .forGetter((RegistryPresenceTest test) -> test.items))
                        .apply(i, RegistryPresenceTest::new));

        private final List<String> items;

        public RegistryPresenceTest(TestData<Holder<TestEnvironmentDefinition<?>>> info, List<String> items) {
            super(info);
            this.items = items;
        }

        @Override
        public void run(GameTestHelper helper) {
            for (String id : items) {
                // An unregistered id resolves to air rather than throwing, so comparing
                // against AIR is the check. Anything looser passes while proving nothing.
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                helper.assertTrue(item != Items.AIR, "expected " + id + " to be registered, got air");
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("registry presence");
        }
    }

    /**
     * <b>Milestone 1, end to end: coal in one place, a machine running in another.</b>
     *
     * <p>A boiler, a steam engine beside it, two poles, and an assembler eight blocks from the
     * generator. Every join in that chain is between two mods that do not compile against each
     * other - {@code nauvis_logistics} could feed the boiler, {@code nauvis_power} makes and
     * carries the electricity, {@code nauvis_machines} spends it - and all of it is held together
     * by NeoForge's capabilities and nothing else. That is exactly the claim non-negotiable #3
     * makes and the one place it can actually be checked, which is why this test is in the pack
     * mod rather than in any of them.
     *
     * <p>Everything is named by id and reached through a capability, so this file still has no
     * compile-time dependency on anything.
     *
     * <p>The second assembler is the control. It is out of reach of both poles, and it stays at
     * zero - without it, a bug that handed energy to every machine in the level would pass.
     */
    public static class PowerReachesAMachineTest extends GameTestInstance {

        public static final MapCodec<PowerReachesAMachineTest> CODEC =
                RecordCodecBuilder.<PowerReachesAMachineTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerReachesAMachineTest::info))
                                .apply(i, PowerReachesAMachineTest::new));

        private static final BlockPos BOILER = new BlockPos(0, 1, 0);
        private static final BlockPos ENGINE = new BlockPos(1, 1, 0);
        private static final BlockPos NEAR_POLE = new BlockPos(2, 1, 0);
        /** Six from the first pole, inside the 7.5 wire reach; eight from the engine. */
        private static final BlockPos FAR_POLE = new BlockPos(8, 1, 0);
        private static final BlockPos ASSEMBLER = new BlockPos(10, 1, 0);
        private static final BlockPos UNPOWERED_ASSEMBLER = new BlockPos(10, 6, 0);

        public PowerReachesAMachineTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            place(helper, BOILER, block(helper, "nauvis_power:boiler"));
            // East, so the engine lies along the line to the boiler. A steam engine takes steam
            // through the two faces on its own axis, and the pack mod can say so without knowing
            // the property: setBlock applies a direction to whatever has a horizontal facing.
            place(helper, ENGINE, block(helper, "nauvis_power:steam_engine"), Direction.EAST);
            place(helper, NEAR_POLE, block(helper, "nauvis_power:small_electric_pole"));
            place(helper, FAR_POLE, block(helper, "nauvis_power:small_electric_pole"));
            place(helper, ASSEMBLER, block(helper, "nauvis_machines:assembling_machine_1"));
            place(helper, UNPOWERED_ASSEMBLER, block(helper, "nauvis_machines:assembling_machine_1"));

            ResourceHandler<ItemResource> fuel = helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BOILER), null);
            helper.assertTrue(fuel != null, "the boiler published no item capability to fuel it through");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(fuel.insert(ItemResource.of(Items.COAL), 1, transaction), 1,
                        "coal accepted by the boiler");
                transaction.commit();
            }

            // Coal to steam to electricity to two poles to a machine. Forty ticks is generous for
            // a chain that moves a tick's worth per tick once it is running.
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(charge(helper, ASSEMBLER) > 0,
                        "an assembler two poles from a running steam engine has no charge, so the "
                                + "grid is not carrying anything");
                helper.assertValueEqual(charge(helper, UNPOWERED_ASSEMBLER), 0,
                        "charge in an assembler no pole can reach");
                helper.succeed();
            });
        }

        private static int charge(GameTestHelper helper, BlockPos pos) {
            EnergyHandler handler = helper.getLevel()
                    .getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), null);
            helper.assertTrue(handler != null, "no energy capability at " + pos);
            return handler.getAmountAsInt();
        }

        /**
         * Places a block the way a player does, {@code setPlacedBy} included.
         *
         * <p>A power pole is three blocks tall and puts its upper two in from there, so a test
         * that only wrote one block state would be building a pole that cannot exist. Calling it
         * for everything costs nothing and needs no knowledge of which blocks care.
         */
        private static void place(GameTestHelper helper, BlockPos pos, Block block) {
            place(helper, pos, block, null);
        }

        private static void place(GameTestHelper helper, BlockPos pos, Block block,
                @Nullable Direction facing) {
            if (facing == null) {
                helper.setBlock(pos, block);
            } else {
                helper.setBlock(pos, block, facing);
            }
            BlockPos absolute = helper.absolutePos(pos);
            block.setPlacedBy(helper.getLevel(), absolute,
                    helper.getLevel().getBlockState(absolute), null, ItemStack.EMPTY);
        }

        /** A block by id, so the pack mod can name another mod's block without depending on it. */
        private static Block block(GameTestHelper helper, String id) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            helper.assertTrue(block != Blocks.AIR, "expected " + id + " to be registered, got air");
            return block;
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power reaches a machine");
        }
    }
}
