package com.jaguarm.nauvismilitary.weapon;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvismilitary.registry.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A gun: the pistol and the submachine gun.
 *
 * <p>Factorio's two: the pistol fires four rounds a second and the submachine gun ten, the pistol
 * at fifteen blocks and the submachine gun at eighteen. Both take the same magazines and do the
 * magazine's damage, raised by whatever physical projectile damage research the world has done -
 * Factorio's {@code ammo-damage} modifier for bullets, read through the library's hook.
 *
 * <p>The pistol fires once a click. The submachine gun fires for as long as the button is held,
 * which is the whole difference between them and the reason to build one.
 */
public class GunItem extends Item {

    /** Factorio's modifier type for ammunition damage, and its category for these magazines. */
    public static final String AMMO_DAMAGE = "ammo-damage";
    public static final String BULLET = "bullet";

    /** Long enough that holding the trigger never runs out. */
    private static final int HOLD_TICKS = 72_000;

    private final int cooldownTicks;
    private final double range;
    private final boolean automatic;

    public GunItem(Properties properties, int cooldownTicks, double range, boolean automatic) {
        super(properties.stacksTo(1));
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.automatic = automatic;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public double range() {
        return range;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (automatic) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server) {
            fire(server, player, gun);
        }
        player.getCooldowns().addCooldown(gun, cooldownTicks);
        return InteractionResult.SUCCESS;
    }

    /** The submachine gun, held: a round every cooldown for as long as the button is down. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack gun, int ticksRemaining) {
        if (!automatic || !(level instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        int held = getUseDuration(gun, entity) - ticksRemaining;
        if (held % cooldownTicks == 0) {
            fire(server, player, gun);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return automatic ? HOLD_TICKS : 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return automatic ? ItemUseAnimation.BOW : ItemUseAnimation.NONE;
    }

    /**
     * Fires one round from this gun, out of whatever magazine the player has, along their look.
     *
     * @return whether a round was fired; false with nothing to fire it from
     */
    public boolean fire(ServerLevel level, Player player, ItemStack gun) {
        ItemStack magazine = MagazineItem.find(player);
        if (magazine == null || !(magazine.getItem() instanceof MagazineItem rounds)) {
            Bullets.click(level, player.getEyePosition());
            return false;
        }
        float damage = (float) (rounds.damage() * (1 + Bonuses.of(level, AMMO_DAMAGE, BULLET)));
        Bullets.fire(level, player, player.getEyePosition(), player.getLookAngle(), range, damage,
                ModDamageTypes.bullet(level, player));
        Bullets.crack(level, player.getEyePosition(), automatic ? 1.3F : 1.0F);
        MagazineItem.spend(magazine, player);
        return true;
    }
}
