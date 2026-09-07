package com.jaguarm.nauvismilitary.weapon;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvismilitary.registry.ModComponents;
import com.jaguarm.nauvismilitary.registry.ModDamageTypes;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A gun: the pistol and the submachine gun.
 *
 * <p>Factorio's two: the pistol fires four rounds a second and the submachine gun ten, at twice
 * Factorio's reach - thirty blocks and thirty-six; see {@code ModItems.RANGE_SCALE}. Both take the
 * same magazines and do the
 * magazine's damage, raised by whatever physical projectile damage research the world has done -
 * Factorio's {@code ammo-damage} modifier for bullets, read through the library's hook.
 *
 * <p>A gun loads one magazine at a time and carries it as a component, the way Factorio's gun has
 * an ammunition slot: the rounds left are shown as the item's bar, and when they run out the next
 * magazine comes out of the inventory. The pistol fires once a click; the submachine gun fires for
 * as long as the button is held, which is the whole difference between them and the reason to
 * build one.
 */
public class GunItem extends Item {

    /** Factorio's modifier type for ammunition damage, and its category for these magazines. */
    public static final String AMMO_DAMAGE = "ammo-damage";
    public static final String BULLET = "bullet";

    /** Long enough that holding the trigger never runs out. */
    private static final int HOLD_TICKS = 72_000;

    /** The bar's colour: brass, for rounds. */
    private static final int BAR_COLOUR = 0xD6B04C;

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

    /** What is loaded, or null for an empty gun. */
    public static ModComponents.@Nullable Loaded loaded(ItemStack gun) {
        ModComponents.Loaded loaded = gun.get(ModComponents.LOADED.get());
        return loaded == null || loaded.rounds() <= 0 ? null : loaded;
    }

    /**
     * Fires one round: from the magazine in the gun, or from the next one out of the player's
     * inventory when the gun is empty, along their look.
     *
     * @return whether a round was fired; false with nothing to fire
     */
    public boolean fire(ServerLevel level, Player player, ItemStack gun) {
        ModComponents.Loaded loaded = loaded(gun);
        if (loaded == null) {
            loaded = reload(player, gun);
            if (loaded == null) {
                Bullets.click(level, player.getEyePosition());
                return false;
            }
        }
        if (!(loaded.magazine().value() instanceof MagazineItem rounds)) {
            gun.remove(ModComponents.LOADED.get());
            return false;
        }

        float damage = (float) (rounds.damage() * (1 + Bonuses.of(level, AMMO_DAMAGE, BULLET)));
        Bullets.fire(level, player, player.getEyePosition(), player.getLookAngle(), range, damage,
                ModDamageTypes.bullet(level, player));
        Bullets.crack(level, player.getEyePosition(), automatic ? 1.3F : 1.0F);

        ModComponents.Loaded left = loaded.fired();
        if (left.rounds() <= 0) {
            gun.remove(ModComponents.LOADED.get());
        } else {
            gun.set(ModComponents.LOADED.get(), left);
        }
        return true;
    }

    /** Takes the next magazine out of the inventory and loads it. Null when there is none. */
    private static ModComponents.@Nullable Loaded reload(Player player, ItemStack gun) {
        ItemStack magazines = MagazineItem.find(player);
        if (magazines == null || !(magazines.getItem() instanceof MagazineItem magazine)) {
            return null;
        }
        ModComponents.Loaded loaded = new ModComponents.Loaded(
                BuiltInRegistries.ITEM.wrapAsHolder(magazine), magazine.rounds());
        magazines.consume(1, player);
        gun.set(ModComponents.LOADED.get(), loaded);
        return loaded;
    }

    /** The rounds left in the loaded magazine, as the item's bar. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return loaded(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        ModComponents.Loaded loaded = loaded(stack);
        if (loaded == null || !(loaded.magazine().value() instanceof MagazineItem magazine)) {
            return 0;
        }
        return Mth.clamp(Math.round(13.0F * loaded.rounds() / magazine.rounds()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, adder, flag);
        ModComponents.Loaded loaded = loaded(stack);
        if (loaded == null) {
            adder.accept(Component.translatable("tooltip.nauvis_military.gun.empty"));
        } else {
            adder.accept(Component.translatable("tooltip.nauvis_military.gun.loaded",
                    new ItemStack(loaded.magazine()).getHoverName(), loaded.rounds()));
        }
    }
}
