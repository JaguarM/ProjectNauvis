package com.jaguarm.nauvisresearch.research;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.jaguarm.nauvisresearch.NauvisResearch;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Watches for the things a triggered technology is waiting on.
 *
 * <p>Some technologies have no cost at all - they finish when the world has made fifty iron plates,
 * or one lab. That is what lets a new world research its way to a boiler and a lab before it has
 * any science at all, and it is why the opening is not "hand-craft everything and then discover
 * research".
 *
 * <h2>Crafting is more than crafting</h2>
 *
 * <p>Factorio has one verb for it. Minecraft has four, and the trigger has to hear all of them or
 * it means something different from what it says: <b>iron plates are smelted here, not crafted</b>,
 * so a listener that only heard the crafting grid would leave "craft fifty iron plates" unreachable
 * for ever. So this hears a bench, a furnace, and Facrafting's own panel - which is the one that
 * matters most, since the panel is where this pack does nearly all its crafting.
 *
 * <p>What it deliberately does not hear is picking an item up. Mining fifty iron ore and smelting
 * it is the intended route; finding fifty iron ingots in a village chest is not the thing the
 * trigger is asking about.
 *
 * <h2>The filter, and why it is worth having</h2>
 *
 * <p>{@link #watched} is the handful of items some technology actually names - four, today. Every
 * craft in the game reaches this class, so the first thing it does is a hash lookup that says no,
 * and only a matching one touches the saved data at all. Without it a world's saved state would
 * grow an entry per item anybody ever made.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class ResearchTriggers {

    private ResearchTriggers() {}

    /** The items any technology is watching for, and the registry that answer was computed from. */
    private static @Nullable Set<Identifier> watched;
    private static @Nullable Registry<Technology> watchedFor;

    /** Dropped on a datapack reload, when the tree may name different items. */
    public static void invalidate() {
        watched = null;
        watchedFor = null;
    }

    private static Set<Identifier> watched(ServerLevel level) {
        Registry<Technology> technologies = ModTechnologies.registry(level.registryAccess());
        if (watched == null || watchedFor != technologies) {
            Set<Identifier> items = new HashSet<>();
            for (Holder.Reference<Technology> holder : technologies.listElements().toList()) {
                holder.value().trigger().ifPresent(trigger -> items.add(trigger.item()));
            }
            watched = items;
            watchedFor = technologies;
        }
        return watched;
    }

    /**
     * The one way in. Called for a bench craft, a smelt, and a Facrafting craft.
     *
     * <p>Takes the whole stack rather than one item: smelting hands over what the furnace made,
     * and a Facrafting recipe can produce two belts at a time. Counting stacks instead of items
     * would make "craft fifty iron plates" mean fifty *smelting operations*, which is not what it
     * says.
     */
    public static void made(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(player instanceof ServerPlayer server)) {
            return;
        }
        ServerLevel level = server.level();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!watched(level).contains(id)) {
            return;
        }
        Research.recordMade(level, id, stack.getCount());
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        made(event.getEntity(), event.getCrafting());
    }

    /**
     * A furnace, which is how iron and copper plates are made here.
     *
     * <p>The event fires when the player takes the result out, so a furnace quietly filling a chest
     * through a hopper is not counted - but hoppers are gone from this pack and an inserter feeding
     * a chest is not a player crafting anything either. Somebody has to have handled it.
     */
    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        made(event.getEntity(), event.getSmelting());
    }
}
