package com.jaguarm.nauvismachines.machine.assembler;

import java.util.List;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvismachines.NauvisMachines;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The assembler's screen: what it is making, how far along, and the slots either side of that.
 *
 * <p>A dark panel, which Yannic likes, with vanilla's own slot sprite in it, which he also likes:
 * the panel is painted in Facrafting's palette so this screen and the panel that opens beside it
 * read as one interface, and the slots and the meters are vanilla's pixels so it reads as
 * Minecraft. Electricity is a bolt drawn the way vanilla's furnace draws its flame - the sprite
 * dark, then lit from the bottom as far as the buffer is full.
 *
 * <p>No recipe list here - that is the panel's job. What this screen adds is the half the
 * panel cannot know: which slots this particular machine has, what is in them, and how close the
 * current craft is to finishing.
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    // Facrafting's palette, so the two halves of the interface match.
    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_FILL = 0xFF55FF55;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    private static final int COLOR_UNLIT = 0xFF3B3B3B;

    /** Vanilla's slot, and this mod's bolt, both from the GUI atlas. */
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier BOLT_SPRITE = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "charge_bolt");
    private static final int METER = 14;

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

        // Vanilla's slot sprite, at every slot the menu has, so the screen cannot disagree with
        // the menu about where they are. The sprite is the well and its edge in one.
        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    x + slot.x - 1, y + slot.y - 1, METER + 4, METER + 4);
        }

        drawProgress(graphics, x, y);
        meter(graphics, BOLT_SPRITE, x + CHARGE_X, y + CHARGE_Y, menu.charge());
    }

    /**
     * A bar rather than vanilla's arrow sprite, because there is no sprite to borrow that is not
     * furnace-shaped. It fills left to right over exactly the recipe's craft time.
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

    /**
     * A fourteen-pixel meter drawn the way vanilla's furnace draws its flame: the whole sprite
     * tinted dark as the empty meter, then the bright sprite over it from the bottom up, as far
     * as it is full.
     */
    private static void meter(GuiGraphicsExtractor graphics, Identifier sprite, int left, int top, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, top, METER, METER, COLOR_UNLIT);
        if (fill <= 0.0f) {
            return;
        }
        int lit = Mth.ceil(fill * (METER - 1)) + 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, METER, METER, 0, METER - lit,
                left, top + METER - lit, METER, lit);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);

        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);
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

        // Said before what it is making, because it is the reason nothing is happening. A bar
        // at zero says the same thing, but only to somebody who already knows to look at it.
        if (!menu.hasPower()) {
            return Component.translatable("screen.nauvis_machines.assembler.no_power");
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
