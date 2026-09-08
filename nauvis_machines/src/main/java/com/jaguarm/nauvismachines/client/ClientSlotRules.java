package com.jaguarm.nauvismachines.client;

import java.util.function.Predicate;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerInventory;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The furnace's slot rules as the client can work them out, so a shift-click lands where the
 * server will put it.
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

    /**
     * How many of a resource the assembler here wants in a slot for one craft, by the recipe key
     * on its synced block entity and the client's copy of the recipe. Zero for no machine, no
     * recipe, a recipe this client was never sent, or a slot that is not that ingredient's.
     */
    public static AssemblerInventory.Wants assemblerWants(Level level, BlockPos machinePos) {
        return (slot, resource) -> {
            if (!(level.getBlockEntity(machinePos) instanceof AssemblerBlockEntity assembler)
                    || assembler.recipeKey() == null) {
                return 0;
            }
            RecipeHolder<FacraftRecipe> holder = ClientRecipes.byId(assembler.recipeKey());
            return holder == null ? 0 : AssemblerBlockEntity.wanted(holder.value(), slot, resource);
        };
    }
}
