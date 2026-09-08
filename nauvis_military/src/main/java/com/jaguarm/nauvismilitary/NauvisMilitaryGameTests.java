package com.jaguarm.nauvismilitary;

import com.jaguarm.nauvislib.health.Health;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvismilitary.pollution.Absorption;
import com.jaguarm.nauvismilitary.pollution.Attacks;
import com.jaguarm.nauvismilitary.pollution.PollutionState;
import com.jaguarm.nauvismilitary.registry.ModBlocks;
import com.jaguarm.nauvismilitary.registry.ModComponents;
import com.jaguarm.nauvismilitary.registry.ModItems;
import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import com.jaguarm.nauvismilitary.turret.GunTurretBlockEntity;
import com.jaguarm.nauvismilitary.weapon.GunItem;
import com.jaguarm.nauvismilitary.weapon.MagazineItem;
import com.jaguarm.nauvislib.test.GameTests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Tests that run inside a real server, headless: a gun hits what it points at, a turret shoots
 * what comes near it and sleeps when it has nothing to shoot with, and the pollution model does
 * its arithmetic and brings something.
 *
 * <p>The hostile in every test is a husk, which is a zombie that does not burn: gametest worlds
 * are daylit, and a zombie dying of sunshine would pass a test about turrets for the wrong reason.
 */
public final class NauvisMilitaryGameTests {

    private NauvisMilitaryGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisMilitary.MODID, modEventBus);

        // A pistol fired at a husk six blocks away takes a round off the magazine and health off the
        // husk - a bullet is a line, and it lands on the first living thing along it.
        tests.add("a_pistol_hits_what_it_points_at", 40, PADDING, helper -> {
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
        });

        // A loaded turret shoots the husk that walks up to it until it is dead, and spends its
        // magazine doing it. Twenty health at five a round, less armour, is a handful of rounds at ten
        // a second; the window allows the turret's first look.
        tests.add("a_turret_shoots_what_comes_near", 100, PADDING, helper -> {
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
        });

        // A turret with nothing in its slot schedules nothing: non-negotiable #5.
        tests.add("a_turret_without_ammunition_sleeps", 40, PADDING, helper -> {
            GunTurretBlockEntity turret = placeTurret(helper, TURRET);
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(turret.status(), GunTurretBlockEntity.Status.NO_AMMO, "status of an empty turret");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(
                        helper.absolutePos(TURRET), ModBlocks.GUN_TURRET.get()), "an empty turret is still ticking");
                helper.succeed();
            });
        });

        // The machines' pollution lands in the cloud over their chunk, and a minute of drift gives
        // two percent to each neighbour and loses five to the ground - on a fresh state, so the
        // arithmetic is checked without the other tests' furnaces breathing into it.
        tests.add("pollution_drifts_and_thins", 40, PADDING, helper -> {
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
        });

        // A thick enough cloud, with a player near it, sends hostiles and spends itself on them.
        tests.add("pollution_brings_something", 40, PADDING, helper -> {
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
        });

        // A turret is hurt as one thing whichever of its four blocks is hit, says so, and comes down
        // as one thing with nothing handed back when its four hundred are gone.
        tests.add("a_turret_is_hurt_and_falls", 40, PADDING, helper -> {
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
        });

        // A block with no health of its own is worth a hundred times its hardness, keeps its wounds in
        // the level until it is mended or falls, and starts whole again when it is rebuilt.
        tests.add("a_wall_is_worth_its_hardness", 40, PADDING, helper -> {
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
        });

        // A forest takes three times what a plain does, a beach a fifth, and the drift reads it.
        tests.add("the_ground_absorbs_by_its_biome", 20, PADDING, helper -> {
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
        });
    }

    private static final BlockPos TURRET = new BlockPos(0, 1, 0);
    private static final int PADDING = 24;

    /** A whole turret, anchored here and facing north. */
    private static GunTurretBlockEntity placeTurret(GameTestHelper helper, BlockPos anchor) {
        GunTurretBlock block = ModBlocks.GUN_TURRET.get();
        Multiblock.place(block, helper.getLevel(), helper.absolutePos(anchor),
                block.defaultBlockState().setValue(GunTurretBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(anchor, GunTurretBlockEntity.class);
    }

}
