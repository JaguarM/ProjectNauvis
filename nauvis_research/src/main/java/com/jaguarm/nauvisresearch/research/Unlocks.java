package com.jaguarm.nauvisresearch.research;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/** Which recipes the tree is currently holding back. */
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
