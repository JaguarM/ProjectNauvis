package com.jaguarm.nauvis;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tests that run inside a real server, headless, reporting pass or fail on exit.
 *
 * <p>This is the half of testing that needs nobody watching. Behaviour — a machine consuming
 * its ingredients, an inserter moving a stack, a recipe resolving — belongs here. How any of
 * it looks does not, and never will.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}: it starts a server, runs every
 * test, and exits non-zero if any failed.
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
                                "neoprogressiveautomation:burner_drill")));
    }

    /**
     * Asserts that a list of item ids is registered once the server is up.
     *
     * <p>Gradle resolving four sibling jars onto the classpath is a different claim from their
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
}
