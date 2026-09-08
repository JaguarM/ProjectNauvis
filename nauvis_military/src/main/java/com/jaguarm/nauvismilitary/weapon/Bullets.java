package com.jaguarm.nauvismilitary.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** A bullet: a line from a muzzle, and whatever it meets first. */
public final class Bullets {

    private Bullets() {}

    /** How far along the ray a tracer particle is put, in blocks. */
    private static final double TRACER_STEP = 1.5;

    /**
     * Fires one bullet.
     *
     * @param shooter who is firing, for the ray to ignore; null for a turret
     * @param from    the muzzle
     * @param towards which way, any length
     * @param range   how far the bullet carries, in blocks
     * @return what was hit, or null for nothing
     */
    public static @Nullable LivingEntity fire(ServerLevel level, @Nullable Entity shooter, Vec3 from, Vec3 towards,
            double range, float damage, DamageSource source) {
        Vec3 direction = towards.normalize();
        Vec3 to = from.add(direction.scale(range));

        BlockHitResult blocked = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter == null ? CollisionContext.empty() : CollisionContext.of(shooter)));
        Vec3 end = blocked.getType() == HitResult.Type.MISS ? to : blocked.getLocation();

        LivingEntity hit = nearest(level, shooter, from, end);
        Vec3 stop = hit == null ? end : hit.getBoundingBox().clip(from, end).orElse(hit.position());
        tracer(level, from, stop);

        if (hit != null) {
            hit.invulnerableTime = 0;
            hit.hurtServer(level, source, damage);
        }
        return hit;
    }

    /** The living thing nearest the muzzle along the ray, or null. */
    private static @Nullable LivingEntity nearest(ServerLevel level, @Nullable Entity shooter, Vec3 from, Vec3 to) {
        AABB along = new AABB(from, to).inflate(1.0);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, along,
                entity -> entity != shooter && entity.isAlive() && !entity.isSpectator())) {
            Vec3 point = candidate.getBoundingBox().inflate(candidate.getPickRadius()).clip(from, to).orElse(null);
            if (point == null) {
                continue;
            }
            double distance = from.distanceToSqr(point);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void tracer(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < 1e-6) {
            return;
        }
        Vec3 step = line.scale(TRACER_STEP / length);
        Vec3 at = from.add(step);
        for (double travelled = TRACER_STEP; travelled < length; travelled += TRACER_STEP) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            at = at.add(step);
        }
    }

    /** The crack of a shot, at the muzzle. Vanilla has no gun; a firework's blast an octave down is the nearest. */
    public static void crack(ServerLevel level, Vec3 at, float pitch) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6F, pitch);
    }

    /** The click of an empty gun. */
    public static void click(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.4F);
    }
}
