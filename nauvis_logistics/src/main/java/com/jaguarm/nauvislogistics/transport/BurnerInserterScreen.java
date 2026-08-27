package com.jaguarm.nauvislogistics.transport;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The burner inserter's screen: what it is burning, and how far through a swing it is.
 *
 * <p>Painted in the same flat colours as the assembler's, the boiler's and Facrafting's panel, so
 * the interface reads as one thing rather than four. The palette and the well-drawing are copied
 * rather than shared: Facrafting is the only place shared code may live, and putting a screen base
 * there would make this mod require it at compile time - which would make
 * {@code burner_inserter_standalone}, the recipe that exists precisely for Facrafting being
 * absent, impossible to reach. See {@code FuelAccess}, duplicated for the same reason.
 */
public class BurnerInserterScreen extends AbstractContainerScreen<BurnerInserterMenu> {

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
    private static final int COLOR_FLAME = 0xFFFF9A3C;
    private static final int COLOR_FILL = 0xFF55FF55;

    /** The flame, above the fuel slot, where players have looked for it since the furnace. */
    private static final int FLAME_X = 45;
    private static final int FLAME_Y = 18;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /**
     * The swing, filling left to right over exactly the thirty ticks one takes.
     *
     * <p>The one thing a burner inserter's screen can tell you that watching it cannot: the swing
     * has no animation yet, so from outside a working inserter and a stalled one look identical.
     */
    private static final int SWING_X = 74;
    private static final int SWING_Y = 38;
    private static final int SWING_WIDTH = 90;
    private static final int SWING_HEIGHT = 6;

    /** Clear of the fuel well above it and vanilla's "Inventory" label at y=72 below. */
    private static final int STATUS_Y = 58;

    public BurnerInserterScreen(BurnerInserterMenu menu, Inventory inventory, Component title) {
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

        drawFlame(graphics, x, y);
        drawSwing(graphics, x, y);
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

    private void drawSwing(GuiGraphicsExtractor graphics, int originX, int originY) {
        int left = originX + SWING_X;
        int top = originY + SWING_Y;

        graphics.fill(left, top, left + SWING_WIDTH, top + SWING_HEIGHT, COLOR_TRACK);

        int width = Math.round(SWING_WIDTH * menu.swingProgress());
        if (width > 0) {
            graphics.fill(left, top, left + width, top + SWING_HEIGHT, COLOR_FILL);
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
     * Which of the two reasons a stopped inserter has stopped for.
     *
     * <p>Out of coal and nothing-to-move look identical from outside the machine, and only one of
     * them is something the player has to do anything about.
     */
    private Component statusLine() {
        if (!menu.isBurning()) {
            return Component.translatable("screen.nauvis_logistics.burner_inserter.no_fuel");
        }
        return Component.translatable(menu.isSwinging()
                ? "screen.nauvis_logistics.burner_inserter.working"
                : "screen.nauvis_logistics.burner_inserter.waiting");
    }
}
