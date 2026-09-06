package com.jaguarm.nauvispower.generator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The boiler's screen: what it is burning, how much is left of it, and how much steam is banked.
 *
 * <p>The same dark panel as the assembler's and Facrafting's panel, so the interface reads as one
 * thing rather than three, with vanilla's own slot sprite and vanilla's furnace flame on it, drawn
 * the way vanilla draws them. That mix is the one Yannic asked for: the dark look, and the pixels
 * a Minecraft player has looked at for years where there is a slot or a fire.
 *
 * <p>The palette and the drawing are copied from {@code AssemblerScreen} rather than shared.
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
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    private static final int COLOR_UNLIT = 0xFF3B3B3B;
    /** Steam. Pale rather than white, or it reads as an empty bar that is somehow full. */
    private static final int COLOR_STEAM = 0xFFB8D8E8;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier FLAME_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final int METER = 14;

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

        // Vanilla's slot sprite, at every slot the menu has, so the screen cannot disagree with
        // the menu about where they are. The sprite is the well and its edge in one.
        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    x + slot.x - 1, y + slot.y - 1, METER + 4, METER + 4);
        }

        meter(graphics, FLAME_SPRITE, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        drawSteam(graphics, x, y);
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
