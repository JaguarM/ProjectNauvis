package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvislib.health.Health;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.pollution.Absorption;
import com.jaguarm.nauvismilitary.pollution.AttackFactoryGoal;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
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
        TEST_TYPES.register("a_turret_is_hurt_and_falls", () -> TurretHealthTest.CODEC);
        TEST_TYPES.register("a_wall_is_worth_its_hardness", () -> WallHealthTest.CODEC);
        TEST_TYPES.register("hostiles_chew_through_to_the_polluter", () -> HostilesChewTest.CODEC);
        TEST_TYPES.register("the_ground_absorbs_by_its_biome", () -> AbsorptionTest.CODEC);
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
        register(event, environment, "a_turret_is_hurt_and_falls", TurretHealthTest::new, 40);
        register(event, environment, "a_wall_is_worth_its_hardness", WallHealthTest::new, 40);
        register(event, environment, "hostiles_chew_through_to_the_polluter", HostilesChewTest::new, 400);
        register(event, environment, "the_ground_absorbs_by_its_biome", AbsorptionTest::new, 20);
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
            BlockPos furnace = helper.absolutePos(TURRET.offset(3, 0, 0));
            state.add(here, 1000.0, furnace);
            helper.assertValueEqual(state.polluterOf(here, 6), furnace, "the polluter a cloud with a source remembers");
            // A cloud that only drifted here follows the thickest neighbour uphill to the machine.
            ChunkPos downwind = new ChunkPos(here.x() + 2, here.z());
            state.set(new ChunkPos(here.x() + 1, here.z()), 300.0);
            state.set(downwind, 60.0);
            helper.assertValueEqual(state.polluterOf(downwind, 6), furnace, "the polluter a drifted cloud finds uphill");
            helper.assertTrue(state.polluterOf(downwind, 1) == null, "a drifted cloud found the machine past its reach");

            List<Mob> sent = Attacks.launch(helper.getLevel(), state, here, furnace, null);
            try {
                helper.assertTrue(!sent.isEmpty(), "a cloud of 1000 with a player beside it sent nothing");
                helper.assertTrue(state.at(here) <= 1000.0 - sent.size() * Attacks.MOB_COST + 1e-9,
                        "the cloud was not spent on what it sent");
                for (Mob mob : sent) {
                    helper.assertTrue(mob.isPersistenceRequired(), "a hostile the cloud sent could despawn");
                    helper.assertTrue(mob.getTarget() == null, "a hostile sent at a machine set out after the player");
                    helper.assertTrue(mob.distanceToSqr(Vec3.atCenterOf(furnace)) >= Attacks.SPAWN_NEAR * Attacks.SPAWN_NEAR * 0.5,
                            "a hostile appeared inside the base");
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

    /**
     * A turret is hurt as one thing whichever of its four blocks is hit, says so, and comes down
     * as one thing with nothing handed back when its four hundred are gone.
     */
    public static class TurretHealthTest extends GameTestInstance {

        public static final MapCodec<TurretHealthTest> CODEC = RecordCodecBuilder.<TurretHealthTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(TurretHealthTest::info)).apply(i, TurretHealthTest::new));

        public TurretHealthTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            GunTurretBlockEntity turret = placeTurret(helper, TURRET);
            BlockPos corner = helper.absolutePos(TURRET.offset(1, 0, 1));
            helper.assertTrue(helper.getLevel().getBlockState(corner).is(ModBlocks.GUN_TURRET.get()),
                    "the far corner of the turret is not the turret");
            helper.assertValueEqual(Health.maxHealth(helper.getLevel(), corner), GunTurretBlockEntity.MAX_HEALTH,
                    "a turret's health, asked through a corner block");

            helper.assertFalse(Health.hurt(helper.getLevel(), corner, 150), "a turret fell at a hundred and fifty");
            helper.assertValueEqual(turret.health(), GunTurretBlockEntity.MAX_HEALTH - 150, "the turret's health after a hit");
            helper.assertValueEqual(Health.repair(helper.getLevel(), corner, 50), 50.0F, "mended");
            helper.assertValueEqual(turret.health(), GunTurretBlockEntity.MAX_HEALTH - 100, "the turret's health after mending");

            helper.assertTrue(Health.hurt(helper.getLevel(), corner, 1000), "a turret survived a thousand");
            helper.runAfterDelay(2, () -> {
                for (BlockPos pos : GunTurretBlockEntity.class.cast(turret).getBlockState().getBlock() instanceof GunTurretBlock block
                        ? block.shape().positions(helper.absolutePos(TURRET), Direction.NORTH) : List.<BlockPos>of()) {
                    helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "a block of the fallen turret still stands at " + pos);
                }
                helper.assertItemEntityCountIs(ModItems.GUN_TURRET.get(), TURRET, 6.0, 0);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a turret is hurt and falls");
        }
    }

    /**
     * A block with no health of its own is worth a hundred times its hardness, keeps its wounds in
     * the level until it is mended or falls, and starts whole again when it is rebuilt.
     */
    public static class WallHealthTest extends GameTestInstance {

        public static final MapCodec<WallHealthTest> CODEC = RecordCodecBuilder.<WallHealthTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(WallHealthTest::info)).apply(i, WallHealthTest::new));

        public WallHealthTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            BlockPos wall = new BlockPos(0, 1, 0);
            helper.setBlock(wall, Blocks.COBBLESTONE_WALL);
            BlockPos at = helper.absolutePos(wall);
            float max = Health.maxHealth(helper.getLevel(), at);
            helper.assertValueEqual(max, 200.0F, "a cobblestone wall's health, from its hardness of two");

            helper.assertFalse(Health.hurt(helper.getLevel(), at, 150), "a wall fell at a hundred and fifty");
            helper.assertValueEqual(Health.health(helper.getLevel(), at), 50.0F, "left after a hit");
            helper.assertValueEqual(Health.repair(helper.getLevel(), at, 1000), 150.0F, "mended: only what was missing");
            helper.assertValueEqual(Health.health(helper.getLevel(), at), 200.0F, "whole again");

            helper.assertFalse(Health.hurt(helper.getLevel(), at, 199), "a wall fell one short");
            helper.assertTrue(Health.hurt(helper.getLevel(), at, 1), "a wall stood at nothing");
            helper.assertBlockPresent(Blocks.AIR, wall);

            // Rebuilt, it is whole: the wound was the old wall's.
            helper.setBlock(wall, Blocks.COBBLESTONE_WALL);
            helper.assertValueEqual(Health.health(helper.getLevel(), at), 200.0F, "a rebuilt wall's health");

            helper.setBlock(wall, Blocks.BEDROCK);
            helper.assertFalse(Health.hurt(helper.getLevel(), at, 100000), "bedrock was hurt");
            helper.assertBlockPresent(Blocks.BEDROCK, wall);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a wall is worth its hardness");
        }
    }

    /**
     * A hostile sent at a turret walks up to it and hits it, and a wall built across its path is
     * what it hits first. The husk starts six blocks from a turret with a wall between; in twenty
     * seconds it has either chewed the wall or the turret, and either way the factory has been hurt
     * by something that was told to hurt it rather than the player.
     */
    public static class HostilesChewTest extends GameTestInstance {

        public static final MapCodec<HostilesChewTest> CODEC = RecordCodecBuilder.<HostilesChewTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(HostilesChewTest::info)).apply(i, HostilesChewTest::new));

        public HostilesChewTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            if (helper.getLevel().getDifficulty() == Difficulty.PEACEFUL) {
                helper.succeed();
                return;
            }
            // The test platform is only as big as the structure, which is a point: everything else
            // is air over the world's floor far below. Lay a floor for the husk to walk on.
            for (int x = -6; x <= 7; x++) {
                for (int z = -9; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            GunTurretBlockEntity turret = placeTurret(helper, TURRET);
            // A wall across the whole approach from the north, two high, so the husk cannot walk round or over.
            for (int x = -4; x <= 5; x++) {
                helper.setBlock(new BlockPos(x, 1, -2), Blocks.COBBLESTONE_WALL);
                helper.setBlock(new BlockPos(x, 2, -2), Blocks.COBBLESTONE_WALL);
            }
            BlockPos wall = helper.absolutePos(new BlockPos(0, 1, -2));
            // A gametest's structure chunk is entity-ticking and the padded ground around it is
            // only loaded, so a mob standing there never ticks. Force the chunks this test walks
            // across; the runner unforces every forced chunk when the batch ends.
            ChunkPos from = ChunkPos.containing(helper.absolutePos(new BlockPos(-5, 1, -7)));
            ChunkPos to = ChunkPos.containing(helper.absolutePos(new BlockPos(6, 1, 2)));
            for (int cx = from.x(); cx <= to.x(); cx++) {
                for (int cz = from.z(); cz <= to.z(); cz++) {
                    helper.getLevel().setChunkForced(cx, cz, true);
                }
            }
            Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(0, 1, -6));
            Attacks.hunt(husk, helper.absolutePos(TURRET), null);
            helper.assertTrue(husk.getTarget() == null, "a hostile sent at the factory has a target");

            Vec3 start = husk.position();
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(husk.isNoAi(), "the husk has no AI");
                String goals = husk.goalSelector.getAvailableGoals().stream()
                        .map(g -> g.getPriority() + ":" + g.getGoal().getClass().getSimpleName() + (g.isRunning() ? "*" : ""))
                        .toList().toString();
                boolean running = husk.goalSelector.getAvailableGoals().stream()
                        .anyMatch(g -> g.getGoal() instanceof AttackFactoryGoal && g.isRunning());
                helper.assertTrue(running, "the factory goal is not running; goals " + goals + ", target " + husk.getTarget()
                        + ", ticks " + husk.tickCount + ", effective AI " + husk.isEffectiveAi() + ", alive " + husk.isAlive()
                        + ", entity ticking chunk " + helper.getLevel().getChunkSource().chunkMap.getDistanceManager()
                                .inEntityTickingRange(husk.chunkPosition().pack()));
            });
            helper.runAfterDelay(100, () -> helper.assertTrue(husk.position().distanceTo(start) > 1.5,
                    "a hostile sent at the factory did not set off: still at " + husk.position()
                            + ", path done " + husk.getNavigation().isDone()));
            helper.runAfterDelay(380, () -> {
                float wallHealth = Health.health(helper.getLevel(), wall);
                boolean wallHurt = helper.getLevel().getBlockState(wall).isAir() || wallHealth < 200.0F;
                boolean turretHurt = turret.isRemoved() || turret.health() < GunTurretBlockEntity.MAX_HEALTH;
                String where = "husk at " + husk.position() + ", path done " + husk.getNavigation().isDone();
                husk.discard();
                helper.assertTrue(wallHurt || turretHurt,
                        "a hostile sent at the turret hurt neither the wall in its way (" + wallHealth
                                + ") nor the turret (" + turret.health() + "); " + where);
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("hostiles chew through to the polluter");
        }
    }

    /** A forest takes three times what a plain does, a beach a fifth, and the drift reads it. */
    public static class AbsorptionTest extends GameTestInstance {

        public static final MapCodec<AbsorptionTest> CODEC = RecordCodecBuilder.<AbsorptionTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(AbsorptionTest::info)).apply(i, AbsorptionTest::new));

        public AbsorptionTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            helper.assertValueEqual(Absorption.of(biomes.getOrThrow(Biomes.FOREST)), Absorption.FOREST, "a forest's absorption");
            helper.assertValueEqual(Absorption.of(biomes.getOrThrow(Biomes.PLAINS)), PollutionState.ABSORB_PER_MINUTE, "a plain's absorption");
            helper.assertValueEqual(Absorption.of(biomes.getOrThrow(Biomes.BEACH)), Absorption.BARE, "a beach's absorption");
            helper.assertValueEqual(Absorption.of(biomes.getOrThrow(Biomes.OCEAN)), Absorption.WATER, "an ocean's absorption");
            helper.assertTrue(biomes.getOrThrow(Biomes.FOREST).is(BiomeTags.IS_FOREST), "the forest tag is not on the forest");

            PollutionState state = new PollutionState();
            ChunkPos chunk = new ChunkPos(20, 20);
            state.set(chunk, 100.0);
            state.drift(c -> Absorption.FOREST);
            helper.assertTrue(Math.abs(state.at(chunk) - (100.0 - 8.0 - Absorption.FOREST)) < 1e-9,
                    "a cloud of 100 over a forest after a minute: " + state.at(chunk));
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("the ground absorbs by its biome");
        }
    }
}
