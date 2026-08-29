package com.jaguarm.nauvisresearch.compat.facrafting;

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

/**
 * Teaches Facrafting's crafting panel about the technology tree.
 *
 * <h2>Why this is a whole package of its own</h2>
 *
 * <p>{@code neoforge.mods.toml} declares Facrafting <b>optional</b>, and it stays optional: the
 * lab and its science pack have standalone bench recipes precisely so this mod can be played
 * without it. But this class names Facrafting types, so loading it without Facrafting present
 * would be a {@code NoClassDefFoundError} at mod construction.
 *
 * <p>So it lives behind a branch. {@code NauvisResearch}'s constructor calls {@link #install()}
 * only when {@code ModList} says Facrafting is loaded, and the JVM resolves the reference the
 * first time that call actually executes - so with Facrafting absent this class is never touched.
 * That is exactly the trick the Jade plugins in this pack use, for exactly the same reason.
 *
 * <h2>Two sides, one class</h2>
 *
 * <p>Facrafting asks the same question on both sides and means different things by it. On the
 * server it is the gate and the answer has to come from the {@code SavedData}; on the client it
 * is a display filter and the answer comes from what was last synced. Splitting on the level is
 * the whole of it, and the two are allowed to disagree for a tick - the server checks again on
 * every click, so a client that thinks something is unlocked and is wrong gets nothing.
 */
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
    }

    @Override
    public boolean isUnlocked(Player player, ResourceKey<Recipe<?>> recipe) {
        if (player.level() instanceof ServerLevel level) {
            return Research.isUnlocked(level.getServer(), recipe);
        }
        return ClientResearch.isUnlocked(player.level().registryAccess(), recipe);
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
