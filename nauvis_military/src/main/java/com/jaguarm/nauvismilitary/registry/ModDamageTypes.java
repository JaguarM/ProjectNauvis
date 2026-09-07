package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The three ways this mod hurts something: a bullet from a gun, a bullet from a turret, and a
 * grenade. Damage types are data - {@code data/nauvis_military/damage_type/} - and these are the
 * keys the code asks for them by; the death messages are lang entries under the same names.
 */
public final class ModDamageTypes {

    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> BULLET = key("bullet");
    public static final ResourceKey<DamageType> TURRET = key("turret");
    public static final ResourceKey<DamageType> GRENADE = key("grenade");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, name));
    }

    /** A bullet fired by somebody. */
    public static DamageSource bullet(ServerLevel level, @Nullable Entity shooter) {
        return new DamageSource(holder(level, BULLET), shooter, shooter);
    }

    /** A bullet fired by a turret, which is a place rather than a somebody. */
    public static DamageSource turret(ServerLevel level, Vec3 from) {
        return new DamageSource(holder(level, TURRET), from);
    }

    /** A grenade going off: the grenade did it, the thrower is to blame. */
    public static DamageSource grenade(ServerLevel level, Entity grenade, @Nullable Entity thrower) {
        return new DamageSource(holder(level, GRENADE), grenade, thrower);
    }

    private static Holder<DamageType> holder(ServerLevel level, ResourceKey<DamageType> key) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }
}
