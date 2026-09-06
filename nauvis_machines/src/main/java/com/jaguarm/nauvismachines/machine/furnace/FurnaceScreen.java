package com.jaguarm.nauvismachines.machine.furnace;

import java.util.List;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The furnace's screen: what goes in, what is burning, what comes out, and how far along.
 *
 * <p>Painted in the assembler's flat colours, so the two machine screens and Facrafting's panel
 * read as one interface. The burner tiers show a flame beside the fuel slot, the electric tier a
 * charge bar under the progress bar; nothing else differs, which is the point of one screen.
 */
public class FurnaceScreen extends AbstractContainerScreen<FurnaceMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_SLOT = 0xFF3B3B3B;
    private static final int COLOR_SLOT_EDGE = 0xFF1E1E1E;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_FILL = 0xFF55FF55;
    /** Fire, and the only warm colour on the panel. */
    private static final int COLOR_FLAME = 0xFFFF9A3C;
    /** Electricity, the assembler's yellow. */
    private static final int COLOR_CHARGE = 0xFFFFD24A;

    /** The progress bar, between the input column and the output well. */
    private static final int ARROW_X = 70;
    private static final int ARROW_Y = 32;
    private static final int ARROW_WIDTH = 34;
    private static final int ARROW_HEIGHT = 6;

    /** The flame, beside the fuel slot and under the input: 26..40 is clear of both wells. */
    private static final int FLAME_X = 26;
    private static final int FLAME_Y = 35;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** An electric furnace's charge, under the progress bar. */
    private static final int CHARGE_X = 70;
    private static final int CHARGE_Y = 44;
    private static final int CHARGE_WIDTH = 34;
    private static final int CHARGE_HEIGHT = 4;

    /** Clear of the fuel well, which ends at 52, and vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    public FurnaceScreen(FurnaceMenu menu, Inventory inventory, Component title) {
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
                    x + slot.x() + 17, y + slot.y() + 17, COLOR_SLOT_EDGE);
            graphics.fill(x + slot.x(), y + slot.y(),
                    x + slot.x() + 16, y + slot.y() + 16, COLOR_SLOT);
        }

        drawProgress(graphics, x, y);
        if (menu.isBurner()) {
            drawFlame(graphics, x, y);
        } else {
            drawCharge(graphics, x, y);
        }
    }

    private List<Slotish> slotWells() {
        return menu.slots.stream().map(slot -> new Slotish(slot.x, slot.y)).toList();
    }

    private record Slotish(int x, int y) {}

    private void drawProgress(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + ARROW_X;
        int top = originY + ARROW_Y;
        graphics.fill(left, top, left + ARROW_WIDTH, top + ARROW_HEIGHT, COLOR_TRACK);
        int filled = Math.round(ARROW_WIDTH * menu.craftProgress());
        if (filled > 0) {
            graphics.fill(left, top, left + filled, top + ARROW_HEIGHT, COLOR_FILL);
        }
    }

    /** Burns downward, so an almost-spent piece of coal is an almost-empty box. */
    private void drawFlame(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + FLAME_X;
        int top = originY + FLAME_Y;
        graphics.fill(left, top, left + FLAME_WIDTH, top + FLAME_HEIGHT, COLOR_TRACK);
        int height = Math.round(FLAME_HEIGHT * menu.burnProgress());
        if (height > 0) {
            graphics.fill(left, top + FLAME_HEIGHT - height, left + FLAME_WIDTH,
                    top + FLAME_HEIGHT, COLOR_FLAME);
        }
    }

    private void drawCharge(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + CHARGE_X;
        int top = originY + CHARGE_Y;
        graphics.fill(left, top, left + CHARGE_WIDTH, top + CHARGE_HEIGHT, COLOR_TRACK);
        int filled = Math.round(CHARGE_WIDTH * menu.charge());
        if (filled > 0) {
            graphics.fill(left, top, left + filled, top + CHARGE_HEIGHT, COLOR_CHARGE);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);
        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);
    }

    /**
     * One line saying what the furnace is doing, or why it is not. The same words the hover
     * readout uses, because they are the same facts.
     */
    private Component statusLine() {
        FurnaceBlockEntity.Status status = menu.status();
        if (status == FurnaceBlockEntity.Status.SMELTING) {
            ResourceKey<Recipe<?>> key = menu.smelting();
            RecipeHolder<FacraftRecipe> holder = key == null ? null : ClientRecipes.byId(key);
            if (holder == null) {
                return Component.translatable("status.nauvis_machines.furnace.unknown");
            }
            return Component.translatable("status.nauvis_machines.furnace.smelting",
                    holder.value().result().create().getHoverName());
        }
        return statusText(status);
    }

    /** The one lang key per status, shared with the hover readout. */
    public static Component statusText(FurnaceBlockEntity.Status status) {
        return Component.translatable("status.nauvis_machines.furnace." + switch (status) {
            case IDLE -> "idle";
            case SMELTING -> "smelting";
            case CANNOT_SMELT -> "cannot_smelt";
            case WAITING -> "waiting";
            case OUTPUT_FULL -> "output_full";
            case NO_FUEL -> "no_fuel";
            case NO_POWER -> "no_power";
        });
    }
}
