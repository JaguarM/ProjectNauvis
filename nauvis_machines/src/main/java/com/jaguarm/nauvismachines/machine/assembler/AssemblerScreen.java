package com.jaguarm.nauvismachines.machine.assembler;

import java.util.List;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * <p>Painted rather than blitted. There is no background texture, because there is no art yet and
 * a machine drawn in flat colours reads as unfinished on purpose, where a machine drawn on a
 * borrowed vanilla panel reads as finished and slightly wrong. The palette is Facrafting's, so
 * this screen and the panel that opens beside it look like one interface rather than two.
 *
 * <p>No recipe list here either - that is the panel's job. What this screen adds is the half the
 * panel cannot know: which slots this particular machine has, what is in them, and how close the
 * current craft is to finishing.
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    // Facrafting's palette, so the two halves of the interface match.
    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_SLOT = 0xFF3B3B3B;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_FILL = 0xFF55FF55;

    /** The progress bar, between the ingredients and the result. */
    private static final int ARROW_X = 74;
    private static final int ARROW_Y = 39;
    private static final int ARROW_WIDTH = 32;
    private static final int ARROW_HEIGHT = 6;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;

        graphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, COLOR_FRAME);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BACKGROUND);

        for (Slotish slot : slotWells()) {
            graphics.fill(x + slot.x() - 1, y + slot.y() - 1,
                    x + slot.x() + 17, y + slot.y() + 17, COLOR_SLOT);
        }

        drawProgress(graphics, x, y);
    }

    /** Every slot's well, taken from the menu so the screen cannot disagree about where they are. */
    private List<Slotish> slotWells() {
        return menu.slots.stream().map(slot -> new Slotish(slot.x, slot.y)).toList();
    }

    private record Slotish(int x, int y) {}

    /**
     * A bar rather than vanilla's arrow sprite, because there is no sprite to borrow that is not
     * furnace-shaped. It empties left to right over exactly the recipe's craft time.
     */
    private void drawProgress(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + ARROW_X;
        int top = originY + ARROW_Y;

        graphics.fill(left, top, left + ARROW_WIDTH, top + ARROW_HEIGHT, COLOR_TRACK);

        float progress = menu.craftProgress();
        int filled = Math.round(ARROW_WIDTH * progress);
        if (filled > 0) {
            graphics.fill(left, top, left + filled, top + ARROW_HEIGHT, COLOR_FILL);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);

        graphics.text(font, statusLine(), 8, 70, COLOR_MUTED, false);
    }

    /**
     * The one line that tells a player what is going on: what it is making, or how to say.
     *
     * <p>The recipe comes from the client's synced copy of the recipe list rather than from a
     * name baked into the machine, so an item renamed by a datapack renames here too.
     */
    private Component statusLine() {
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
        return Component.translatable("screen.nauvis_machines.assembler.making",
                holder.value().result().create().getHoverName());
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
