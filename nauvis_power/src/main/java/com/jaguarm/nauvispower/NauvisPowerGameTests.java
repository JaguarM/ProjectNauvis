package com.jaguarm.nauvispower;

import java.util.List;

import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.block.Block;
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
 * Tests that run inside a real server, headless.
 *
 * <p>The one that matters most is {@code power_chain_sleeps}. Making energy is easy to check and
 * hard to get wrong; <em>stopping</em> is neither. Non-negotiable #5 across a chain means an engine
 * with a full buffer stops drawing steam, which lets the boiler's buffer fill, which stops it
 * burning coal - three blocks that each have to decide to do nothing, in order, from the far end.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class NauvisPowerGameTests {

    private NauvisPowerGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final BlockPos BOILER = new BlockPos(0, 1, 0);
    private static final BlockPos ENGINE = new BlockPos(1, 1, 0);

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisPower.MODID);

    static {
        TEST_TYPES.register("boiler_makes_steam", () -> BoilerMakesSteamTest.CODEC);
        TEST_TYPES.register("steam_engine_makes_power", () -> SteamEngineMakesPowerTest.CODEC);
        TEST_TYPES.register("power_chain_sleeps", () -> PowerChainSleepsTest.CODEC);
        TEST_TYPES.register("steam_engine_takes_no_power", () -> SteamEngineTakesNoPowerTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));

        register(event, environment, "boiler_makes_steam", BoilerMakesSteamTest::new, 100);
        register(event, environment, "steam_engine_makes_power", SteamEngineMakesPowerTest::new, 100);
        register(event, environment, "power_chain_sleeps", PowerChainSleepsTest::new, 400);
        register(event, environment, "steam_engine_takes_no_power", SteamEngineTakesNoPowerTest::new, 60);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> environment, String name, TestFactory factory, int maxTicks) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE)));
    }

    /** A boiler with coal in it, and an engine touching it. */
    private static void buildChain(GameTestHelper helper, boolean fuelled) {
        helper.setBlock(BOILER, ModBlocks.BOILER.get());
        helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get());
        if (fuelled) {
            insert(helper.getBlockEntity(BOILER, BoilerBlockEntity.class).fuelAccess(), Items.COAL, 1);
        }
    }

    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static boolean isScheduled(GameTestHelper helper, BlockPos pos, Block block) {
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), block);
    }

    /** Coal in, steam out. */
    public static class BoilerMakesSteamTest extends GameTestInstance {

        public static final MapCodec<BoilerMakesSteamTest> CODEC =
                RecordCodecBuilder.<BoilerMakesSteamTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(BoilerMakesSteamTest::info))
                                .apply(i, BoilerMakesSteamTest::new));

        public BoilerMakesSteamTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(BOILER, ModBlocks.BOILER.get());
            BoilerBlockEntity boiler = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
            helper.assertValueEqual(boiler.steam(), 0, "steam in a cold boiler");
            helper.assertValueEqual(insert(boiler.fuelAccess(), Items.COAL, 1), 1, "coal accepted");

            helper.runAfterDelay(20, () -> {
                BoilerBlockEntity fired = helper.getBlockEntity(BOILER, BoilerBlockEntity.class);
                helper.assertTrue(fired.steam() > 0, "a boiler with coal in it made no steam");
                helper.assertTrue(fired.burnTime() > 0, "it made steam without burning anything");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("boiler makes steam");
        }
    }

    /** Steam in, electricity out - the first energy this pack has ever made. */
    public static class SteamEngineMakesPowerTest extends GameTestInstance {

        public static final MapCodec<SteamEngineMakesPowerTest> CODEC =
                RecordCodecBuilder.<SteamEngineMakesPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineMakesPowerTest::info))
                                .apply(i, SteamEngineMakesPowerTest::new));

        public SteamEngineMakesPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            buildChain(helper, true);

            helper.runAfterDelay(20, () -> {
                SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);
                helper.assertTrue(engine.energyStored() > 0,
                        "a steam engine beside a burning boiler stored no energy");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine makes power");
        }
    }

    /**
     * <b>Non-negotiable #5, across three blocks.</b>
     *
     * <p>Nobody is drawing, so the engine fills and stops. Having stopped it draws no more steam,
     * so the boiler fills and stops too, and stops burning coal. Each of those is a separate
     * decision made at a different end of the chain, and a mistake in any one of them looks exactly
     * like a factory that works - right up until there are a thousand of them.
     */
    public static class PowerChainSleepsTest extends GameTestInstance {

        public static final MapCodec<PowerChainSleepsTest> CODEC =
                RecordCodecBuilder.<PowerChainSleepsTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(PowerChainSleepsTest::info))
                                .apply(i, PowerChainSleepsTest::new));

        public PowerChainSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
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
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("power chain sleeps");
        }
    }

    /** A generator is not a battery: the grid cannot push energy back into it. */
    public static class SteamEngineTakesNoPowerTest extends GameTestInstance {

        public static final MapCodec<SteamEngineTakesNoPowerTest> CODEC =
                RecordCodecBuilder.<SteamEngineTakesNoPowerTest>mapCodec(
                        i -> i.group(TestData.CODEC.forGetter(SteamEngineTakesNoPowerTest::info))
                                .apply(i, SteamEngineTakesNoPowerTest::new));

        public SteamEngineTakesNoPowerTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.setBlock(ENGINE, ModBlocks.STEAM_ENGINE.get());
            SteamEngineBlockEntity engine = helper.getBlockEntity(ENGINE, SteamEngineBlockEntity.class);

            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(engine.cableView().insert(1000, transaction), 0,
                        "energy pushed into a generator");
                transaction.commit();
            }
            helper.assertValueEqual(engine.energyStored(), 0, "charge in an engine nobody fuelled");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("steam engine takes no power");
        }
    }
}
