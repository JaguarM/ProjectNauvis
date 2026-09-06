package com.jaguarm.nauvismachines.client;

import java.util.function.Predicate;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The furnace's slot rules as the client can work them out, so a shift-click lands where the
 * server will put it.
 *
 * <p>A menu's client half is built with a stand-in inventory, and the stand-in's {@code isValid}
 * is what {@code mayPlace} reads when the client predicts a shift-click. With a permissive
 * stand-in, coal jumped into the input slot and was moved to the fuel slot a tick later when the
 * server's answer arrived, and a stick went in and bounced back. Vanilla's furnace never does
 * that because its menu can ask the same questions on both sides - and so can this one: fuel
 * values are synced to the client, vanilla's furnace inputs are a synced property set, and
 * Facrafting sends every timed recipe. Only the research lock is a guess, and the client's guess
 * is the one the panel already draws with.
 *
 * <p>Client package because {@link ClientRecipes} is a client class; the menu's client
 * constructor is the only caller, and a dedicated server never runs it.
 */
public final class ClientSlotRules {

    private ClientSlotRules() {}

    /** Whether a furnace here would smelt this: Factorio's recipes as the client knows them, then vanilla's. */
    public static Predicate<ItemResource> smeltable(Level level) {
        return resource -> {
            ItemStack stack = resource.toStack(1);
            for (RecipeHolder<FacraftRecipe> holder : ClientRecipes.unlocked()) {
                FacraftRecipe recipe = holder.value();
                if (FurnaceBlockEntity.SMELTING.equals(recipe.category())
                        && recipe.ingredients().size() == 1
                        && recipe.ingredients().get(0).ingredient().test(stack)) {
                    return true;
                }
            }
            return level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
        };
    }

    /** Whether this burns, by the fuel values the server sent. */
    public static Predicate<ItemResource> fuel(Level level) {
        return resource -> resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0;
    }
}
