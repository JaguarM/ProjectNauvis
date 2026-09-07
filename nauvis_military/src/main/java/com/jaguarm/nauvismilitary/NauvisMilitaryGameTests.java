package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.pollution.Attacks;
import com.jaguarm.nauvismilitary.pollution.PollutionState;
import com.jaguarm.nauvismilitary.registry.ModBlocks;
import com.jaguarm.nauvismilitary.registry.ModComponents;
import com.jaguarm.nauvismilitary.registry.ModItems;
import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import com.jaguarm.nauvismilitary.turret.GunTurretBlockEntity;
import com.jaguarm.nauvismilitary.weapon.GunItem;
import com.jaguarm.nauvismilitary.weapon.MagazineItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Tests that run inside a real server, headless: a gun hits what it points at, a turret shoots
 * what comes near it and sleeps when it has nothing to shoot with, and the pollution model does
 * its arithmetic and brings something.
 *
 * <p>The hostile in every test is a husk, which is a zombie that does not burn: gametest worlds
 * are daylit, and a zombie dying of sunshine would pass a test about turrets for the wrong reason.
 */
@EventBusSubscriber(modid = NauvisMilitary.MODID)
public final class NauvisMilitaryGameTests {

    private NauvisMilitaryGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos TURRET = new BlockPos(0, 1, 0);
    private static final int PADDING = 24;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisMilitary.MODID);

    static {
        TEST_TYPES.register("a_pistol_hits_what_it_points_at", () -> PistolTest.CODEC);
        TEST_TYPES.register("a_turret_shoots_what_comes_near", () -> TurretShootsTest.CODEC);
        TEST_TYPES.register("a_turret_without_ammunition_sleeps", () -> TurretSleepsTest.CODEC);
        TEST_TYPES.register("pollution_drifts_and_thins", () -> PollutionDriftsTest.CODEC);
        TEST_TYPES.register("pollution_brings_something", () -> PollutionAttacksTest.CODEC);
    }

    public static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, environment, "a_pistol_hits_what_it_points_at", PistolTest::new, 40);
        register(event, environment, "a_turret_shoots_what_comes_near", TurretShootsTest::new, 100);
        register(event, environment, "a_turret_without_ammunition_sleeps", TurretSleepsTest::new, 40);
        register(event, environment, "pollution_drifts_and_thins", PollutionDriftsTest::new, 40);
        register(event, environment, "pollution_brings_something", PollutionAttacksTest::new, 40);
    }

    private interface TestFactory {
        GameTestInstance create(TestData<Holder<TestEnvironmentDefinition<?>>> info);
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            String name, TestFactory factory, int maxTicks) {
        event.registerTest(Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, name),
                factory.create(new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE,
                        false, 1, 1, false, PADDING)));
    }

    /** A whole turret, anchored here and facing north. */
    private static GunTurretBlockEntity placeTurret(GameTestHelper helper, BlockPos anchor) {
        GunTurretBlock block = ModBlocks.GUN_TURRET.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(GunTurretBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, GunTurretBlockEntity.class);
    }

    /**
     * A pistol fired at a husk six blocks away takes a round off the magazine and health off the
     * husk - a bullet is a line, and it lands on the first living thing along it.
     */
    public static class PistolTest extends GameTestInstance {

        public static final MapCodec<PistolTest> CODEC = RecordCodecBuilder.<PistolTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(PistolTest::info)).apply(i, PistolTest::new));

        public PistolTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
            Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
            // Yaw 180 looks north, which is where the husk stands.
            player.snapTo(feet.x, feet.y, feet.z, 180.0F, 0.0F);
            ItemStack pistol = new ItemStack(ModItems.PISTOL.get());
            player.getInventory().add(new ItemStack(ModItems.FIREARM_MAGAZINE.get(), 3));

            Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(0, 1, -6));
            float before = husk.getHealth();

            helper.assertTrue(((GunItem) pistol.getItem()).fire(helper.getLevel(), player, pistol),
                    "the pistol did not fire with a magazine in the inventory");
            helper.assertTrue(husk.getHealth() < before, "the husk in front of the pistol was not hit");
            // The gun loaded one magazine off the stack and fired one round from it.
            ItemStack held = MagazineItem.find(player);
            helper.assertTrue(held != null, "every magazine left the inventory");
            helper.assertValueEqual(held.getCount(), 2, "magazines left in the stack after loading one");
            ModComponents.Loaded loaded = GunItem.loaded(pistol);
            helper.assertTrue(loaded != null, "the pistol holds no magazine after firing");
            helper.assertValueEqual(loaded.rounds(), 9, "rounds left in the loaded magazine");
            helper.assertTrue(((GunItem) pistol.getItem()).fire(helper.getLevel(), player, pistol),
                    "the pistol did not fire its second round");
            helper.assertValueEqual(GunItem.loaded(pistol).rounds(), 8, "rounds left after two shots");
            helper.assertValueEqual(MagazineItem.find(player).getCount(), 2,
                    "the second shot took another magazine instead of the loaded one's next round");

            player.getInventory().clearContent();
            pistol.remove(ModComponents.LOADED.get());
            helper.assertFalse(((GunItem) pistol.getItem()).fire(helper.getLevel(), player, pistol),
                    "the pistol fired with nothing to fire");
            husk.discard();
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a pistol hits what it points at");
        }
    }

    /**
     * A loaded turret shoots the husk that walks up to it until it is dead, and spends its
     * magazine doing it. Twenty health at five a round, less armour, is a handful of rounds at ten
     * a second; the window allows the turret's first look.
     */
    public static class TurretShootsTest extends GameTestInstance {

        public static final MapCodec<TurretShootsTest> CODEC = RecordCodecBuilder.<TurretShootsTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(TurretShootsTest::info)).apply(i, TurretShootsTest::new));

        public TurretShootsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            GunTurretBlockEntity turret = placeTurret(helper, TURRET);
            turret.inventory().set(GunTurretBlockEntity.AMMO_SLOT,
                    ItemResource.of(ModItems.FIREARM_MAGAZINE.get()), 1);
            Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(0, 1, -6));
            husk.setNoAi(true);

            helper.runAfterDelay(80, () -> {
                helper.assertFalse(husk.isAlive(), "the husk in front of a loaded turret is still alive");
                helper.assertTrue(turret.shots() >= 4, "rounds the turret fired: " + turret.shots());
                helper.assertTrue(turret.roundsLeft() < 10, "the turret shot without spending its magazine");
                helper.assertValueEqual(turret.inventory().getAmountAsInt(GunTurretBlockEntity.AMMO_SLOT), 0,
                        "magazines still in the slot after chambering the only one");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a turret shoots what comes near");
        }
    }

    /** A turret with nothing in its slot schedules nothing: non-negotiable #5. */
    public static class TurretSleepsTest extends GameTestInstance {

        public static final MapCodec<TurretSleepsTest> CODEC = RecordCodecBuilder.<TurretSleepsTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(TurretSleepsTest::info)).apply(i, TurretSleepsTest::new));

        public TurretSleepsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            GunTurretBlockEntity turret = placeTurret(helper, TURRET);
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(turret.status(), GunTurretBlockEntity.Status.NO_AMMO, "status of an empty turret");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                        helper.absolutePos(TURRET), ModBlocks.GUN_TURRET.get()), "an empty turret is still ticking");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a turret without ammunition sleeps");
        }
    }

    /**
     * The machines' pollution lands in the cloud over their chunk, and a minute of drift gives
     * two percent to each neighbour and loses five to the ground - on a fresh state, so the
     * arithmetic is checked without the other tests' furnaces breathing into it.
     */
    public static class PollutionDriftsTest extends GameTestInstance {

        public static final MapCodec<PollutionDriftsTest> CODEC = RecordCodecBuilder.<PollutionDriftsTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(PollutionDriftsTest::info)).apply(i, PollutionDriftsTest::new));

        public PollutionDriftsTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.assertTrue(Pollution.installed(), "nothing is keeping count of pollution");
            ChunkPos here = ChunkPos.containing(helper.absolutePos(TURRET));
            PollutionState world = PollutionState.get(helper.getLevel());
            double before = world.at(here);
            Pollution.emit(helper.getLevel(), helper.absolutePos(TURRET), 3.0);
            helper.assertTrue(world.at(here) >= before + 3.0 - 1e-9, "an emission did not reach the cloud over its chunk");

            PollutionState state = new PollutionState();
            ChunkPos chunk = new ChunkPos(10, 10);
            state.set(chunk, 100.0);
            state.drift();
            helper.assertTrue(Math.abs(state.at(chunk) - 87.0) < 1e-9,
                    "a cloud of 100 after a minute: " + state.at(chunk) + ", not 87");
            for (ChunkPos neighbour : List.of(new ChunkPos(11, 10), new ChunkPos(9, 10),
                    new ChunkPos(10, 11), new ChunkPos(10, 9))) {
                helper.assertTrue(Math.abs(state.at(neighbour)) < 1e-9,
                        "two percent of 100 is under what the ground takes, so a neighbour holds nothing: "
                                + state.at(neighbour));
            }
            state.set(chunk, 1000.0);
            state.drift();
            helper.assertTrue(Math.abs(state.at(new ChunkPos(11, 10)) - 15.0) < 1e-9,
                    "a neighbour of a cloud of 1000 after a minute: " + state.at(new ChunkPos(11, 10)) + ", not 15");
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pollution drifts and thins");
        }
    }

    /** A thick enough cloud, with a player near it, sends hostiles and spends itself on them. */
    public static class PollutionAttacksTest extends GameTestInstance {

        public static final MapCodec<PollutionAttacksTest> CODEC = RecordCodecBuilder.<PollutionAttacksTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(PollutionAttacksTest::info)).apply(i, PollutionAttacksTest::new));

        public PollutionAttacksTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            if (helper.getLevel().getDifficulty() == Difficulty.PEACEFUL) {
                helper.succeed();
                return;
            }
            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
            Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
            player.snapTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);

            PollutionState state = new PollutionState();
            ChunkPos here = ChunkPos.containing(helper.absolutePos(TURRET));
            state.set(here, 1000.0);
            List<Mob> sent = Attacks.launch(helper.getLevel(), state, here, player);
            try {
                helper.assertTrue(!sent.isEmpty(), "a cloud of 1000 with a player beside it sent nothing");
                helper.assertTrue(state.at(here) <= 1000.0 - sent.size() * Attacks.MOB_COST + 1e-9,
                        "the cloud was not spent on what it sent");
                for (Mob mob : sent) {
                    helper.assertTrue(mob.isPersistenceRequired(), "a hostile the cloud sent could despawn");
                }
            } finally {
                sent.forEach(Mob::discard);
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("pollution brings something");
        }
    }
}
