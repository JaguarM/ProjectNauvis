package com.jaguarm.nauvisresearch.lab;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The lab's screen: what it is working through, how far into a cycle it is, and how much research
 * it has done.
 *
 * <p>Painted in the same flat colours as the boiler's, the assembler's and Facrafting's panel, so
 * the interface reads as one thing rather than four. There is no background texture because there
 * is no art yet, and a machine drawn in flat colours reads as unfinished on purpose where a
 * machine drawn on a borrowed vanilla panel reads as finished and slightly wrong.
 *
 * <p>The palette and the well-drawing are copied from {@code BoilerScreen} rather than shared, for
 * the reason that file gives: a shared screen base could only live in Facrafting, and that would
 * make this mod require it.
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_SLOT = 0xFF3B3B3B;
    /** Darker than the well it frames, or a row of slots renders as one grey slab. */
    private static final int COLOR_SLOT_EDGE = 0xFF1E1E1E;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** Research. The one colour on the panel that is not a machine colour. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;
    /** Charge, the same amber the assembler uses for the same thing. */
    private static final int COLOR_CHARGE = 0xFFE0B040;

    /** The cycle bar, filling left to right under the pack slots. */
    private static final int PROGRESS_X = 26;
    private static final int PROGRESS_Y = 56;
    private static final int PROGRESS_WIDTH = 108;
    private static final int PROGRESS_HEIGHT = 6;

    /** Charge, a thin bar beneath the cycle bar. */
    private static final int CHARGE_X = 26;
    private static final int CHARGE_Y = 66;
    private static final int CHARGE_WIDTH = 108;
    private static final int CHARGE_HEIGHT = 4;

    /** Clear of the bars above and vanilla's "Inventory" label at y=72. */
    private static final int STATUS_Y = 20;

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;

        graphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, COLOR_FRAME);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BACKGROUND);

        // Two passes, because adjacent slots are exactly 18 apart and a well is 18 across: drawn
        // in one colour they tile into an unbroken rectangle.
        for (Slotish slot : slotWells()) {
            graphics.fill(x + slot.x() - 1, y + slot.y() - 1,
                    x + slot.x() + 17, y + slot.y() + 17, COLOR_SLOT_EDGE);
            graphics.fill(x + slot.x(), y + slot.y(),
                    x + slot.x() + 16, y + slot.y() + 16, COLOR_SLOT);
        }

        bar(graphics, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT,
                menu.progress(), COLOR_PROGRESS);
        bar(graphics, x + CHARGE_X, y + CHARGE_Y, CHARGE_WIDTH, CHARGE_HEIGHT,
                menu.charge(), COLOR_CHARGE);
    }

    private static void bar(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
            float filled, int colour) {
        graphics.fill(left, top, left + width, top + height, COLOR_TRACK);
        int amount = Math.round(width * filled);
        if (amount > 0) {
            graphics.fill(left, top, left + amount, top + height, colour);
        }
    }

    /** Every slot's well, taken from the menu so the screen cannot disagree about where they are. */
    private List<Slotish> slotWells() {
        return menu.slots.stream().map(slot -> new Slotish(slot.x, slot.y)).toList();
    }

    private record Slotish(int x, int y) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED,
                false);

        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);
    }

    /**
     * One line saying which of the three things a stopped lab is doing.
     *
     * <p>Idle and unpowered look identical otherwise and want different things done about them:
     * one needs science packs, the other needs a wire.
     */
    private Component statusLine() {
        if (!menu.hasPower()) {
            return Component.translatable("screen.nauvis_research.lab.no_power");
        }
        if (!menu.isWorking()) {
            return Component.translatable("screen.nauvis_research.lab.idle");
        }
        return Component.translatable("screen.nauvis_research.lab.researching", menu.cycles());
    }
}
