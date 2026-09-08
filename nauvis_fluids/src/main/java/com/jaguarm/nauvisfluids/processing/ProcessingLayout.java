package com.jaguarm.nauvisfluids.processing;

import com.jaguarm.facrafting.recipe.FacraftRecipe;

/**
 * What a fluid-processing machine has room for: how many fluids in and out, how many items in
 * and out, which recipe category it runs, what a tick of it costs, and how many modules it
 * takes.
 *
 * @param category      the {@link FacraftRecipe#category()} this machine runs, and no other
 * @param fluidInputs   fluid ports in, and the tanks behind them
 * @param fluidOutputs  fluid ports out
 * @param itemInputs    item slots in. Zero for a machine with no hands
 * @param itemOutputs   item slots out
 * @param energyPerTick FE spent per tick of a craft, at the pack's ratio of 120 FE/t to
 * @param moduleSlots   Factorio's module slots, which are identity like the ports
 */
public record ProcessingLayout(String category, int fluidInputs, int fluidOutputs,
        int itemInputs, int itemOutputs, int energyPerTick, int moduleSlots, double pollutionPerMinute) {

    public int tankCount() {
        return fluidInputs + fluidOutputs;
    }

    public int itemSlots() {
        return itemInputs + itemOutputs;
    }

    /**
     * Whether a recipe is this machine's to run: its category, and no more of anything than
     * there are ports and slots for.
     */
    public boolean accepts(FacraftRecipe recipe) {
        return category.equals(recipe.category())
                && recipe.fluidIngredients().size() <= fluidInputs
                && recipe.fluidResults().size() <= fluidOutputs
                && recipe.ingredients().size() <= itemInputs
                && (!recipe.hasItemResult() || itemOutputs >= 1);
    }
}
