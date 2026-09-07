package com.jaguarm.nauvismilitary.pollution;

import com.jaguarm.nauvislib.health.Health;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.jspecify.annotations.Nullable;

/**
 * What a hostile the pollution sent does: walks at the machine that made it, and chews through
 * whatever stands in the way.
 *
 * <p>Factorio's biters path to the polluter and attack anything that blocks the path - which is
 * how a wall gets chewed through and a turret gets swarmed. This goal is that in two rules. While
 * it can walk, it walks at its target. When it cannot - the path is done and it is not there, or
 * there is no path - it hits the block in front of it in the direction of its target, once a
 * second for its own attack damage, through {@code nauvis_lib}'s {@link Health}, which is what a
 * wall at two hundred and a turret at four hundred are measured in. Cracks show on the block as
 * they would under a pickaxe. When the target is gone it picks the nearest remembered polluter
 * within a couple of chunks, and when there is none left it stops and is an ordinary hostile.
 *
 * <p>It runs only while the mob has nobody to fight: a player who comes within reach is a target
 * the vanilla goals take, and this one waits. Which is Factorio's rule too - biters fight what
 * fights them.
 *
 * <p>The target is not saved. A hostile that lives through a reload forgets the factory and is a
 * zombie again, which is written down in {@code GAPS.md}.
 */
public class AttackFactoryGoal extends Goal {

    /** How close, from the mob's box to the middle of the block, counts as reaching it. */
    private static final double REACH = 2.0;

    /** One hit a second, which is roughly a biter's rate of attack. */
    private static final int HIT_TICKS = 20;

    /** How often to ask for a path again while walking. */
    private static final int REPATH_TICKS = 20;

    /** How far from where the last target stood to look for the next one, in chunks. */
    private static final int NEXT_TARGET_CHUNKS = 2;

    private final Mob mob;
    private @Nullable BlockPos target;

    /** The block being hit right now: the target when it is in reach, or what is in the way. */
    private @Nullable BlockPos chewing;
    private int lastProgress = -1;
    private int cooldown;
    private int repath;

    public AttackFactoryGoal(Mob mob, BlockPos target) {
        this.mob = mob;
        this.target = target.immutable();
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** What the mob is walking at, for a readout or a test. */
    public @Nullable BlockPos target() {
        return target;
    }

    @Override
    public boolean canUse() {
        return target != null && mob.getTarget() == null && mob.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repath = 0;
        cooldown = 0;
    }

    @Override
    public void stop() {
        clearCracks();
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || target == null) {
            return;
        }
        if (level.getBlockState(target).isAir()) {
            // Destroyed, by this mob or another. The next polluter, or nothing.
            clearCracks();
            target = PollutionState.get(level).nearestSource(target, NEXT_TARGET_CHUNKS);
            if (target == null) {
                return;
            }
        }

        cooldown--;
        BlockPos aim = chewing != null ? chewing : target;
        Vec3 middle = Vec3.atCenterOf(aim);
        mob.getLookControl().setLookAt(middle);

        if (chewing != null && (level.getBlockState(chewing).isAir() || distanceTo(chewing) > REACH + 1)) {
            // Chewed through, or walked away from it.
            clearCracks();
        }

        if (distanceTo(aim) <= REACH) {
            if (cooldown <= 0) {
                hit(level, aim);
            }
            return;
        }

        if (repath-- <= 0) {
            // The block above the target, as vanilla's own goals path to a block: the target
            // itself is solid. A target nothing can reach still gives a path to the nearest point.
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 1.0);
            repath = REPATH_TICKS;
        }
        if (mob.getNavigation().isDone()) {
            // As near as the paths go, and not there. Press on towards the target anyway - a
            // wall is what stops a straight walk - and hit whatever the mob is pressed against.
            mob.getMoveControl().setWantedPosition(target.getX() + 0.5, mob.getY(), target.getZ() + 0.5, 1.0);
            BlockPos blocking = inTheWay(level);
            if (blocking != null && !blocking.equals(chewing)) {
                clearCracks();
                chewing = blocking;
            }
        }
    }

    /** The block a step towards the target at the mob's feet, or at its head, that can be hurt. */
    private @Nullable BlockPos inTheWay(ServerLevel level) {
        Vec3 towards = Vec3.atCenterOf(target).subtract(mob.position());
        Direction step = Direction.getApproximateNearest(towards.x, 0, towards.z);
        BlockPos feet = mob.blockPosition().relative(step);
        for (BlockPos candidate : new BlockPos[] {feet, feet.above()}) {
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && Health.canHurt(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void hit(ServerLevel level, BlockPos pos) {
        cooldown = HIT_TICKS;
        if (!CommonHooks.canEntityDestroy(level, pos, mob)) {
            // Mob griefing is off. The mob stands there swinging, as a zombie does at a door.
            mob.swing(mob.getUsedItemHand());
            return;
        }
        mob.swing(mob.getUsedItemHand());
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8F, 1.0F);
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float max = Health.maxHealth(level, pos);
        if (Health.hurt(level, pos, damage)) {
            clearCracks();
            return;
        }
        if (max > 0) {
            int progress = (int) ((1 - Health.health(level, pos) / max) * 10);
            if (progress != lastProgress) {
                level.destroyBlockProgress(mob.getId(), pos, progress);
                lastProgress = progress;
            }
            chewing = pos;
        }
    }

    private double distanceTo(BlockPos pos) {
        return Math.sqrt(mob.getBoundingBox().distanceToSqr(Vec3.atCenterOf(pos)));
    }

    private void clearCracks() {
        if (chewing != null) {
            mob.level().destroyBlockProgress(mob.getId(), chewing, -1);
        }
        chewing = null;
        lastProgress = -1;
    }
}
