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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Watches for the things a triggered technology is waiting on.
 *
 * <p>Some technologies have no cost at all - they finish when the world has made fifty iron plates,
 * or one lab, or pumped crude oil once. That is what lets a new world research its way to a boiler
 * and a lab before it has any science at all, and it is why the opening is not "hand-craft
 * everything and then discover research".
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
 * <h2>Mining is a machine's report</h2>
 *
 * <p>Factorio's {@code mine-entity} trigger - oil processing finishes when crude oil has been
 * pumped once - has no vanilla event behind it: no player, no block break. The machine that took
 * the resource says so, through Facrafting's {@code MiningListeners}, which is the one seam two
 * subsystem mods may share without depending on each other. {@link #mined} is what that seam
 * calls, installed beside the craft listener in {@code FacraftingLock}.
 *
 * <h2>The filter, and why it is worth having</h2>
 *
 * <p>{@link #watchedCrafts} and {@link #watchedMines} are the handful of ids some technology
 * actually names - five, today. Every craft in the game reaches this class, so the first thing it
 * does is a hash lookup that says no, and only a matching one touches the saved data at all.
 * Without it a world's saved state would grow an entry per item anybody ever made.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class ResearchTriggers {

    private ResearchTriggers() {}

    /** What any technology is watching for, and the registry those answers were computed from. */
    private static @Nullable Set<Identifier> watchedCrafts;
    private static @Nullable Set<Identifier> watchedMines;
    private static @Nullable Registry<Technology> watchedFor;

    /** Dropped on a datapack reload, when the tree may name different things. */
    public static void invalidate() {
        watchedCrafts = null;
        watchedMines = null;
        watchedFor = null;
    }

    private static void refresh(ServerLevel level) {
        Registry<Technology> technologies = ModTechnologies.registry(level.registryAccess());
        if (watchedCrafts != null && watchedMines != null && watchedFor == technologies) {
            return;
        }
        Set<Identifier> crafts = new HashSet<>();
        Set<Identifier> mines = new HashSet<>();
        for (Holder.Reference<Technology> holder : technologies.listElements().toList()) {
            holder.value().trigger().ifPresent(trigger -> {
                switch (trigger.kind()) {
                    case CRAFT -> crafts.add(trigger.target());
                    case MINE -> mines.add(trigger.target());
                }
            });
        }
        watchedCrafts = crafts;
        watchedMines = mines;
        watchedFor = technologies;
    }

    private static Set<Identifier> watchedCrafts(ServerLevel level) {
        refresh(level);
        return watchedCrafts;
    }

    private static Set<Identifier> watchedMines(ServerLevel level) {
        refresh(level);
        return watchedMines;
    }

    /**
     * The one way in for crafts. Called for a bench craft, a smelt, and a Facrafting craft.
     *
     * <p>Takes the whole stack rather than one item: smelting hands over what the furnace made,
     * and a Facrafting recipe can produce two belts at a time. Counting stacks instead of items
     * would make "craft fifty iron plates" mean fifty *smelting operations*, which is not what it
     * says.
     */
    public static void made(Player player, ItemStack stack) {
        // The level rather than the player's type: what this needs is the server's saved data,
        // and anything crafting on the server can reach it. A crafter that is not a networked
        // player has still made the thing.
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!watchedCrafts(level).contains(id)) {
            return;
        }
        Research.recordMade(level, id, stack.getCount());
    }

    /**
     * The one way in for mining. Called by Facrafting's {@code MiningListeners} when a machine
     * reports what it took, with the machine's own id for the resource - a pumpjack says
     * {@code nauvis_fluids:crude_oil}, the block it stands on.
     *
     * <p>The count is how many times the thing was mined, not how much came out: Factorio's
     * {@code mine-entity: crude-oil, 1} is one pumpjack cycle, whatever the yield.
     */
    public static void mined(ServerLevel level, Identifier resource, int count) {
        if (count <= 0 || !watchedMines(level).contains(resource)) {
            return;
        }
        Research.recordMined(level, resource, count);
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
