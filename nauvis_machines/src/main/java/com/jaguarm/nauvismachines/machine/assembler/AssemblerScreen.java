package com.jaguarm.nauvismachines.machine.assembler;

import java.util.List;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvislib.client.MachineScreen;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The assembler's screen: what it is making, how far along, and the slots either side of that.
 *
 * <p>No recipe list here - that is the panel's job. What this screen adds is the half the
 * panel cannot know: which slots this particular machine has, what is in them, and how close the
 * current craft is to finishing.
 */
public class AssemblerScreen extends MachineScreen<AssemblerMenu> {

    /**
     * The progress bar, in the gap between the ingredient block and the result.
     *
     * <p>Ingredients end at x=62 and the output well starts at x=115, so 70..104 is clear. Its
     * vertical centre is the same y=35 everything else is arranged around.
     */
    private static final int ARROW_X = 70;
    private static final int ARROW_Y = 32;
    private static final int ARROW_WIDTH = 34;
    private static final int ARROW_HEIGHT = 6;

    /**
     * The charge, a bolt under the progress bar.
     *
     * <p>An assembler that has stopped for want of electricity is otherwise indistinguishable
     * from one that has stopped for want of ingredients, and the two want completely different
     * things done about them. 42..56 is clear: the ingredient block ends at x=61, the output
     * well at y=43, and the status line starts at y=58.
     */
    private static final int CHARGE_X = 80;
    private static final int CHARGE_Y = 42;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /**
     * The status line, clear of both the slots above and vanilla's "Inventory" label below.
     *
     * <p>{@code inventoryLabelY} is {@code imageHeight - 94}, which is 72 here. The first version
     * put this at 70 and the two lines were drawn through each other.
     */
    private static final int STATUS_Y = 58;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        // A bar rather than the arrow sprite, which is furnace-shaped. It fills left to right
        // over exactly the recipe's craft time.
        bar(graphics, x + ARROW_X, y + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, menu.craftProgress(), COLOR_FILL);
        meter(graphics, BOLT, x + CHARGE_X, y + CHARGE_Y, menu.charge());
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /**
     * The one line that tells a player what is going on: what it is making, or how to say.
     *
     * <p>The recipe comes from the client's synced copy of the recipe list rather than from a
     * name baked into the machine, so an item renamed by a datapack renames here too.
     */
    @Override
    protected Component statusLine() {
        ResourceKey<Recipe<?>> selected = menu.selectedRecipe();
        if (selected == null) {
            return Component.translatable("screen.nauvis_machines.assembler.idle");
        }

        RecipeHolder<FacraftRecipe> holder = ClientRecipes.byId(selected);
        if (holder == null) {
            // The server chose a recipe this client was never sent. Say something true rather
            // than nothing.
            return Component.translatable("screen.nauvis_machines.assembler.unknown");
        }

        // Said before what it is making, because it is the reason nothing is happening. A bar
        // at zero says the same thing, but only to somebody who already knows to look at it.
        if (!menu.hasPower()) {
            return Component.translatable("screen.nauvis_machines.assembler.no_power");
        }

        return Component.translatable("screen.nauvis_machines.assembler.making",
                holder.value().displayName());
    }

    /**
     * Hovering an empty ingredient slot says what the recipe wants there.
     *
     * <p>An assembler with a recipe and empty slots looks identical to one with no recipe at all,
     * and the difference is the whole reason a player opens it.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);

        if (hoveredSlot == null || hoveredSlot.hasItem()
                || hoveredSlot.index >= AssemblerBlockEntity.INPUT_SLOTS) {
            return;
        }

        FacraftRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return;
        }

        List<Component> lines = recipe.ingredients().stream()
                .map(AssemblerScreen::ingredientLine)
                .toList();
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private @Nullable FacraftRecipe selectedRecipe() {
        ResourceKey<Recipe<?>> selected = menu.selectedRecipe();
        if (selected == null) {
            return null;
        }
        RecipeHolder<FacraftRecipe> holder = ClientRecipes.byId(selected);
        return holder == null ? null : holder.value();
    }

    /**
     * An ingredient names the first item that satisfies it. Factorio's recipes are all concrete
     * items, so "first" and "only" are the same thing here; a tag ingredient from some other pack
     * would show one member of the tag rather than all of them, which is still better than a
     * blank line.
     */
    private static Component ingredientLine(SizedIngredient ingredient) {
        Component name = ingredient.ingredient().items().findFirst()
                .map(holder -> new ItemStack(holder).getHoverName())
                .orElse(Component.literal("?"));
        return Component.translatable("screen.nauvis_machines.assembler.wants", ingredient.count(), name);
    }
}
