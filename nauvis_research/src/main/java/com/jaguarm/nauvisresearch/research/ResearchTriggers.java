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

/** Watches for the things a triggered technology is waiting on. */
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
        if (player.level() instanceof ServerLevel level) {
            made(level, stack);
        }
    }

    /**
     * The same, from a machine. A furnace has no player, and the plates it smelts are the ones
     * {@code steam-power} is counting - Factorio's {@code craft-item} trigger counts what a
     * crafting machine makes as well as what a hand does, which is the only reading under which
     * a technology that asks for fifty iron plates can ever finish.
     */
    public static void made(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
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
