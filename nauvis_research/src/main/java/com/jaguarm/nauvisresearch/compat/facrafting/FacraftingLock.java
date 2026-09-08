package com.jaguarm.nauvisresearch.compat.facrafting;

import com.jaguarm.facrafting.progress.MiningListeners;
import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.RecipeLock;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.ResearchTriggers;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;

/** Teaches Facrafting's crafting panel about the technology tree. */
public final class FacraftingLock implements RecipeLock {

    private FacraftingLock() {}

    public static void install() {
        RecipeLocks.install(new FacraftingLock());

        // The other half of the seam, and it points the same way: Facrafting says a craft
        // finished, and this mod decides whether that finished a technology. A trigger reads
        // "craft fifty iron plates", and the panel is where this pack does nearly all crafting -
        // without this the two triggered technologies that ask for a crafted item, rather than a
        // smelted one, could never fire.
        CraftListeners.add(ResearchTriggers::made);
        // And a machine's report of the same thing. Iron plates are never crafted by hand, so
        // "craft fifty iron plates" is heard from the furnace that smelted them or from nothing.
        CraftListeners.addMachine(ResearchTriggers::made);

        // And the other verb. A machine that mines something says so through Facrafting, which
        // is the one mod this one and the machine's may both compile against; oil processing is
        // finished by a pumpjack's first cycle and could be finished by nothing else.
        MiningListeners.add(ResearchTriggers::mined);
    }

    @Override
    public boolean isUnlocked(Player player, ResourceKey<Recipe<?>> recipe) {
        if (player.level() instanceof ServerLevel level) {
            return Research.isUnlocked(level.getServer(), recipe);
        }
        return ClientResearch.isUnlocked(player.level().registryAccess(), recipe);
    }

    /**
     * A machine's question - a furnace deciding whether it may smelt steel yet. Research is a
     * fact about the world, so the answer is the world's, with no player in it.
     */
    @Override
    public boolean isUnlocked(ServerLevel level, ResourceKey<Recipe<?>> recipe) {
        return Research.isUnlocked(level.getServer(), recipe);
    }

    /**
     * The client's revision, because the client is the only side that caches an answer.
     *
     * <p>This is what makes a recipe appear in an open panel the moment its technology finishes.
     * The panel reads it every frame and rebuilds its tab strip when it moves; without it the
     * newly unlocked recipe would turn up only when the screen was closed and opened again, which
     * looks exactly like the research having done nothing.
     */
    @Override
    public int revision() {
        return ClientResearch.revision();
    }
}
