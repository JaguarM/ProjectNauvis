package com.jaguarm.nauvispower.generator;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The boiler's screen: what it is burning, how much is left of it, and how much steam is banked.
 *
 * <p>Painted in the same flat colours as the assembler's and Facrafting's panel, so the interface
 * reads as one thing rather than three. There is no background texture because there is no art
 * yet, and a machine drawn in flat colours reads as unfinished on purpose where a machine drawn on
 * a borrowed vanilla panel reads as finished and slightly wrong.
 *
 * <p>The palette and the well-drawing are copied from {@code AssemblerScreen} rather than shared.
 * Facrafting is the only place shared code may live, and putting a screen base there would make
 * {@code nauvis_power} require it at compile time - which would make {@code boiler_standalone},
 * the recipe that exists precisely for Facrafting being absent, impossible to reach. Sixty
 * duplicated lines is the cheaper of the two wrong answers; see {@code FuelAccess}, duplicated for
 * the same reason.
 */
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {

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
    /** Fire, and the only warm colour on the panel. */
    private static final int COLOR_FLAME = 0xFFFF9A3C;
    /** Steam. Pale rather than white, or it reads as an empty bar that is somehow full. */
    private static final int COLOR_STEAM = 0xFFB8D8E8;

    /**
     * The flame, directly above the fuel slot, burning down as a furnace's does.
     *
     * <p>Above rather than beside, because that is where three decades of Minecraft players look
     * for it, and 18..32 is clear of the title at 6..15 and the fuel well starting at 33.
     */
    private static final int FLAME_X = 27;
    private static final int FLAME_Y = 18;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** The steam buffer, filling left to right in the space beside the fuel slot. */
    private static final int STEAM_X = 56;
    private static final int STEAM_Y = 36;
    private static final int STEAM_WIDTH = 100;
    private static final int STEAM_HEIGHT = 10;

    /** Clear of the fuel well above it and vanilla's "Inventory" label at y=72 below. */
    private static final int STATUS_Y = 58;

    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
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

        // Two passes, because adjacent slots are exactly 18 apart and a well is 18 across: drawn
        // in one colour they tile into an unbroken rectangle.
        for (Slotish slot : slotWells()) {
            graphics.fill(x + slot.x() - 1, y + slot.y() - 1,
                    x + slot.x() + 17, y + slot.y() + 17, COLOR_SLOT_EDGE);
            graphics.fill(x + slot.x(), y + slot.y(),
                    x + slot.x() + 16, y + slot.y() + 16, COLOR_SLOT);
        }

        drawFlame(graphics, x, y);
        drawSteam(graphics, x, y);
    }

    /** Every slot's well, taken from the menu so the screen cannot disagree about where they are. */
    private List<Slotish> slotWells() {
        return menu.slots.stream().map(slot -> new Slotish(slot.x, slot.y)).toList();
    }

    private record Slotish(int x, int y) {}

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

    private void drawSteam(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + STEAM_X;
        int top = originY + STEAM_Y;

        graphics.fill(left, top, left + STEAM_WIDTH, top + STEAM_HEIGHT, COLOR_TRACK);

        int width = Math.round(STEAM_WIDTH * menu.steam());
        if (width > 0) {
            graphics.fill(left, top, left + width, top + STEAM_HEIGHT, COLOR_STEAM);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);

        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);
    }

    /**
     * One line saying which of the three things a stopped boiler is doing.
     *
     * <p>Full and out of fuel look identical otherwise, and they want completely different things
     * done about them: one needs coal, the other needs somebody to draw the steam off.
     */
    private Component statusLine() {
        if (menu.isBurning()) {
            return Component.translatable("screen.nauvis_power.boiler.burning");
        }
        if (menu.steam() >= 1.0f) {
            return Component.translatable("screen.nauvis_power.boiler.full");
        }
        return Component.translatable("screen.nauvis_power.boiler.idle");
    }
}
