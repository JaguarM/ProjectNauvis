package com.jaguarm.nauvisrocket.client;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;
import com.jaguarm.nauvisrocket.silo.RocketSiloInventory;

import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The silo's slot rules as the client can work them out, so a shift-click lands where the server
 * will put it.
 *
 * <p>A menu's client half is built with a stand-in inventory, and the stand-in's {@code isValid}
 * and capacity are what the client predicts a click by. Facrafting sends every timed recipe to the
 * client, so the rocket part's is here to read: the recipe that makes a rocket part, found the way
 * the server finds it. A client that was never sent it - there is none, short of a broken
 * connection - refuses, and the server's answer would stand anyway.
 */
public final class ClientSiloRules {

    private ClientSiloRules() {}

    /** How many of a resource a rocket part takes in a slot, by the client's copy of the recipe. */
    public static RocketSiloInventory.Wants partWants() {
        return (slot, resource) -> {
            FacraftRecipe part = partRecipe();
            return part == null ? 0 : RocketSiloBlockEntity.wanted(part, slot, resource);
        };
    }

    private static FacraftRecipe partRecipe() {
        for (RecipeHolder<FacraftRecipe> holder : ClientRecipes.recipes()) {
            if (holder.value().resultStack().is(ModItems.ROCKET_PART.get())) {
                return holder.value();
            }
        }
        return null;
    }
}
