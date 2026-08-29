package com.jaguarm.nauvisresearch.research;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Which recipes the tree is currently holding back.
 *
 * <p>The rule is one line and worth stating exactly, because the obvious reading of it is wrong:
 * <b>a recipe is locked when some technology unlocks it and none of those technologies is
 * finished.</b> A recipe no technology mentions is not locked - that is how belts, inserters,
 * furnaces and the lab itself are craftable from the first minute, which is Factorio's own
 * arrangement and not a concession.
 *
 * <p>The answer is computed as a set of what is <em>locked</em> rather than of what is unlocked,
 * and the difference matters at this pack's scale: the tree names 154 recipes today and the pack
 * will hold two hundred, but "everything else" is every recipe in every mod installed, which is
 * a set nothing here can enumerate and should not try to.
 *
 * <p>Nothing is cached here. Both callers - {@link Research} on the server and
 * {@code ClientResearch} on the client - hold their own answer and drop it when their own state
 * changes, which is once per completed technology rather than once per query. A cache in here
 * would have to be keyed on which side asked, because in single player the two sides have
 * different registry objects for the same tree.
 */
public final class Unlocks {

    private Unlocks() {}

    /**
     * @param completed what the world has already researched.
     * @return every recipe key some technology gates and no completed technology has released.
     */
    public static Set<ResourceKey<Recipe<?>>> locked(
            Registry<Technology> technologies, Set<ResourceKey<Technology>> completed) {

        Set<ResourceKey<Recipe<?>>> locked = new HashSet<>();
        Set<ResourceKey<Recipe<?>>> released = new HashSet<>();

        for (Holder.Reference<Technology> holder : technologies.listElements().toList()) {
            boolean done = completed.contains(holder.key());
            for (ResourceKey<Recipe<?>> recipe : holder.value().unlocks()) {
                // Two technologies can unlock the same recipe - Factorio does it for the oil
                // recipes - and finishing either one is enough, so a release always wins over a
                // gate however the two are ordered.
                if (done) {
                    released.add(recipe);
                } else {
                    locked.add(recipe);
                }
            }
        }

        locked.removeAll(released);
        return locked;
    }
}
