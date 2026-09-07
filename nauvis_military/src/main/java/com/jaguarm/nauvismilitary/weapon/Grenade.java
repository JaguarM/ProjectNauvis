package com.jaguarm.nauvismilitary.weapon;

import com.jaguarm.nauvismilitary.registry.ModDamageTypes;
import com.jaguarm.nauvismilitary.registry.ModEntities;
import com.jaguarm.nauvismilitary.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown grenade: a snowball that goes off.
 *
 * <p>Factorio's grenade does thirty-five damage over a radius of six and a half tiles and leaves
 * the buildings standing. Minecraft's explosion does the first two on its own - power three is
 * about that much at the centre, falling off to the edge - and {@code ExplosionInteraction.NONE}
 * is the third: it hurts what is in the blast and breaks nothing.
 */
public class Grenade extends ThrowableItemProjectile {

    /** Vanilla's explosion power: a creeper is three, TNT four. */
    public static final float POWER = 3.0F;

    public Grenade(EntityType<? extends Grenade> type, Level level) {
        super(type, level);
    }

    public Grenade(Level level, LivingEntity thrower, ItemStack stack) {
        super(ModEntities.GRENADE.get(), thrower, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GRENADE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        level.explode(this, ModDamageTypes.grenade(level, this, getOwner()), null, position(), POWER, false,
                Level.ExplosionInteraction.NONE);
        discard();
    }
}
