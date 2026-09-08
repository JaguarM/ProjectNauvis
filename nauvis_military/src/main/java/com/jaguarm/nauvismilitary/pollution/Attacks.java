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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/** The biters: Minecraft's own hostiles, sent at the factory by its pollution. */
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

    /** How far from the target the group appears: outside the base, not in it. */
    public static final double SPAWN_NEAR = 24.0;
    public static final double SPAWN_FAR = 40.0;

    /** How far uphill a group follows the cloud to find the machine that made it, in chunks. */
    public static final int GRADIENT_STEPS = 6;

    /** How near a player has to come to a hostile bound for the factory before it turns on them. */
    public static final double NOTICE_PLAYER = 6.0;

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
            Player near = level.getNearestPlayer(middle.getX(), middle.getY(), middle.getZ(), PLAYER_RANGE, false);
            if (near == null) {
                continue;
            }
            // The factory, if the cloud remembers one; the player who is near it otherwise.
            BlockPos polluter = state.polluterOf(chunk, GRADIENT_STEPS);
            launch(level, state, chunk, polluter != null ? polluter : near.blockPosition(), polluter == null ? near : null);
        }
    }

    /**
     * Sends a group from this chunk's cloud at this position, spending the cloud for each that
     * appears. Public for the test, which does not want to wait for the dice.
     *
     * @param player a player to walk at instead, for a cloud that remembers no machine; null
     *               sends the group at the position and lets it find its own fights
     * @return the hostiles that appeared
     */
    public static List<Mob> launch(ServerLevel level, PollutionState state, ChunkPos chunk, BlockPos target,
            @Nullable Player player) {
        double amount = state.at(chunk);
        int group = (int) Math.min(MAX_GROUP, Math.floor(amount / MOB_COST));
        List<Mob> sent = new ArrayList<>();
        for (int i = 0; i < group; i++) {
            Mob mob = spawnOne(level, target, state.total());
            if (mob != null) {
                hunt(mob, target, player);
                sent.add(mob);
            }
        }
        state.set(chunk, amount - sent.size() * MOB_COST);
        return sent;
    }

    /**
     * Points a hostile at the factory: it walks at this position and chews through what is in the
     * way, and turns on a player only within {@link #NOTICE_PLAYER} blocks or when hit. Public so a
     * test can send one hostile at one machine.
     */
    public static void hunt(Mob mob, BlockPos target, @Nullable Player player) {
        // Here for the factory, not for the night: it does not wander off and it does not burn.
        mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        // Vanilla's hostiles go for any player in follow range, which would leave the factory
        // untouched whenever its owner is at home. Only a player close enough to be in the way.
        mob.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true,
                (candidate, level) -> candidate.distanceToSqr(mob) <= NOTICE_PLAYER * NOTICE_PLAYER));
        mob.goalSelector.addGoal(4, new AttackFactoryGoal(mob, target));
        if (player != null) {
            mob.setTarget(player);
        }
    }

    private static @Nullable Mob spawnOne(ServerLevel level, BlockPos around, double lifetime) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = SPAWN_NEAR + random.nextDouble() * (SPAWN_FAR - SPAWN_NEAR);
        int x = around.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = around.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return null;
        }
        return kind(random, lifetime).spawn(level, pos, EntitySpawnReason.EVENT);
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
