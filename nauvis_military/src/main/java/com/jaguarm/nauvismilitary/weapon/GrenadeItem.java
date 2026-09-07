package com.jaguarm.nauvismilitary.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The grenade in the hand: thrown like a snowball, and one fewer once it is. */
public class GrenadeItem extends Item {

    /** Factorio's grenade takes thirty ticks to throw again. */
    public static final int COOLDOWN_TICKS = 30;

    public GrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.6F);
        if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation(Grenade::new, server, stack, player, 0.0F, 1.2F, 1.0F);
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
