package com.jaguarm.nauvismilitary.pollution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The biters: Minecraft's own hostiles, sent at the factory by its pollution.
 *
 * <p>Factorio's spawners absorb the pollution that drifts over them and send a biter for every so
 * much absorbed. There are no spawners here, so the cloud itself does the sending: once a minute,
 * a chunk holding at least {@link #MOB_COST} has a chance proportional to what it holds of
 * spending it on a group, which walks in from outside the cloud's edge with the nearest player as
 * its target. Nothing comes if nobody is near - an attack on an empty base is a server spending
 * ticks on mobs nobody will meet - and nothing comes on peaceful.
 *
 * <p>What comes is decided by everything the level has ever emitted, which stands in for
 * Factorio's evolution: zombies to begin with, skeletons among them after twenty thousand,
 * creepers after sixty. They wear a cap so the sun does not do the turrets' job for them.
 *
 * <p>All of it is tunable and none of it is identity; {@code PLAN.md} has the model and
 * {@code GAPS.md} what it leaves out.
 */
public final class Attacks {

    private Attacks() {}

    /** What one hostile costs the cloud. */
    public static final double MOB_COST = 50.0;

    /** The most a chunk sends at once. */
    public static final int MAX_GROUP = 6;

    /** A chunk holding this much sends something every minute; less, proportionally less often. */
    public static final double CERTAIN_AT = 500.0;

    /** How near a player has to be to the polluted chunk for anything to come. */
    public static final double PLAYER_RANGE = 96.0;

    /** How far from the chunk's middle the group appears: outside the base, not in it. */
    public static final double SPAWN_NEAR = 24.0;
    public static final double SPAWN_FAR = 40.0;

    /** The level's lifetime pollution at which the mix changes. */
    public static final double SKELETONS_FROM = 20_000;
    public static final double CREEPERS_FROM = 60_000;

    /** One minute's worth of attacks, over every cloud thick enough. */
    static void sweep(ServerLevel level, PollutionState state) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        // A copy: launching spends from the map being walked.
        for (Map.Entry<Long, Double> entry : List.copyOf(state.clouds().entrySet())) {
            double amount = entry.getValue();
            if (amount < MOB_COST || level.getRandom().nextDouble() > amount / CERTAIN_AT) {
                continue;
            }
            ChunkPos chunk = ChunkPos.unpack(entry.getKey());
            if (!level.hasChunk(chunk.x(), chunk.z())) {
                continue;
            }
            BlockPos middle = chunk.getMiddleBlockPosition(level.getSeaLevel());
            Player target = level.getNearestPlayer(middle.getX(), middle.getY(), middle.getZ(), PLAYER_RANGE, false);
            if (target == null) {
                continue;
            }
            launch(level, state, chunk, target);
        }
    }

    /**
     * Sends a group from this chunk's cloud at this player, spending the cloud for each that
     * appears. Public for the test, which does not want to wait for the dice.
     *
     * @return the hostiles that appeared
     */
    public static List<Mob> launch(ServerLevel level, PollutionState state, ChunkPos chunk, Player target) {
        double amount = state.at(chunk);
        int group = (int) Math.min(MAX_GROUP, Math.floor(amount / MOB_COST));
        List<Mob> sent = new ArrayList<>();
        BlockPos middle = chunk.getMiddleBlockPosition(level.getSeaLevel());
        for (int i = 0; i < group; i++) {
            Mob mob = spawnOne(level, middle, state.total(), target);
            if (mob != null) {
                sent.add(mob);
            }
        }
        state.set(chunk, amount - sent.size() * MOB_COST);
        return sent;
    }

    private static Mob spawnOne(ServerLevel level, BlockPos middle, double lifetime, Player target) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = SPAWN_NEAR + random.nextDouble() * (SPAWN_FAR - SPAWN_NEAR);
        int x = middle.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = middle.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return null;
        }

        Mob mob = kind(random, lifetime).spawn(level, pos, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        // Here for the factory, not for the night: it does not wander off and it does not burn.
        mob.setPersistenceRequired();
        mob.setTarget(target);
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        return mob;
    }

    /** Who comes, by how dirty the level has been so far. */
    static EntityType<? extends Mob> kind(RandomSource random, double lifetime) {
        if (lifetime >= CREEPERS_FROM && random.nextInt(4) == 0) {
            return EntityTypes.CREEPER;
        }
        if (lifetime >= SKELETONS_FROM && random.nextInt(3) == 0) {
            return EntityTypes.SKELETON;
        }
        return EntityTypes.ZOMBIE;
    }
}
