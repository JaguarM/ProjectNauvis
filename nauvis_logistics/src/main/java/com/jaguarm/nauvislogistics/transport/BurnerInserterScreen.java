package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The burner inserter's screen: what it is burning, and how far through a swing it is.
 *
 * <p>The same dark panel as the assembler's, the boiler's and Facrafting's panel, so the
 * interface reads as one thing rather than four, with vanilla's slot sprite on it and a flame in
 * vanilla's pixel idiom, drawn the way vanilla's furnace draws its flame - the mix Yannic asked
 * for. The flame is the pack's own sprite, because vanilla's carries the panel's grey behind it
 * and is a box on a dark panel. The palette and the
 * drawing are copied rather than shared: Facrafting is the only place shared code may live, and
 * putting a screen base there would make this mod require it at compile time - which would make
 * {@code burner_inserter_standalone}, the recipe that exists precisely for Facrafting being
 * absent, impossible to reach. See {@code FuelAccess}, duplicated for the same reason.
 */
public class BurnerInserterScreen extends AbstractContainerScreen<BurnerInserterMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    private static final int COLOR_UNLIT = 0xFF3B3B3B;
    private static final int COLOR_FILL = 0xFF55FF55;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier FLAME_SPRITE = Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "meter_flame");
    private static final int METER = 14;

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

        // Vanilla's slot sprite, at every slot the menu has, so the screen cannot disagree with
        // the menu about where they are. The sprite is the well and its edge in one.
        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    x + slot.x - 1, y + slot.y - 1, METER + 4, METER + 4);
        }

        meter(graphics, FLAME_SPRITE, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        drawSwing(graphics, x, y);
    }

    /**
     * A fourteen-pixel meter drawn the way vanilla's furnace draws its flame: the whole sprite
     * tinted dark as the empty meter, then the bright sprite over it from the bottom up, as far
     * as it is full. A lit flame is never less than a pixel, which is vanilla's rule too.
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
