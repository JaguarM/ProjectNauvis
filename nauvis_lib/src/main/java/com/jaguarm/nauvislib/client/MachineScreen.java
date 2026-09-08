package com.jaguarm.nauvislib.client;

import com.jaguarm.nauvislib.NauvisLib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import org.jspecify.annotations.Nullable;

/** The screen every machine in the pack is drawn on. */
public abstract class MachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    // Facrafting's palette, so the machine screens and the crafting panel match.
    public static final int COLOR_FRAME = 0xFF000000;
    public static final int COLOR_BACKGROUND = 0xF0141414;
    public static final int COLOR_TEXT = 0xFFFFFFFF;
    public static final int COLOR_MUTED = 0xFF909090;
    public static final int COLOR_TRACK = 0xFF2A2A2A;
    public static final int COLOR_FILL = 0xFF55FF55;
    public static final int COLOR_BUTTON = 0xFF3B3B3B;
    public static final int COLOR_BUTTON_HOVER = 0xFF6A6A6A;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    public static final int COLOR_UNLIT = 0xFF3B3B3B;

    /** Vanilla's slot, the one vanilla sprite here, because a slot is meant to be a box. */
    public static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    public static final Identifier FLAME = Identifier.fromNamespaceAndPath(NauvisLib.MODID, "meter_flame");
    public static final Identifier BOLT = Identifier.fromNamespaceAndPath(NauvisLib.MODID, "charge_bolt");
    public static final Identifier ARROW = Identifier.fromNamespaceAndPath(NauvisLib.MODID, "meter_arrow");

    /** The flame and the bolt are fourteen by fourteen; the arrow is twenty-four by sixteen. */
    public static final int METER = 14;
    public static final int ARROW_SPRITE_WIDTH = 24;
    public static final int ARROW_SPRITE_HEIGHT = 16;

    protected MachineScreen(T menu, Inventory inventory, Component title) {
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

        paint(graphics, x, y, mouseX, mouseY);
    }

    /** The machine's own meters, bars and buttons, over the panel and its slots. */
    protected abstract void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY);

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);

        graphics.text(font, statusLine(), 8, statusY(), COLOR_MUTED, false);
    }

    /** One line saying what the machine is doing, or why it is not. */
    protected abstract Component statusLine();

    /** Where {@link #statusLine} goes: the screen's own {@code STATUS_Y}, which the layout checker reads. */
    protected abstract int statusY();

    /**
     * A fourteen-pixel meter drawn the way vanilla's furnace draws its flame: the whole sprite
     * tinted dark as the empty meter, then the bright sprite over it from the bottom up, as far
     * as it is full. A meter that is on at all is never less than a pixel, which is vanilla's
     * rule too.
     */
    protected static void meter(GuiGraphicsExtractor graphics, Identifier sprite, int left, int top, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, top, METER, METER, COLOR_UNLIT);
        if (fill <= 0.0f) {
            return;
        }
        int lit = Mth.ceil(fill * (METER - 1)) + 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, METER, METER, 0, METER - lit,
                left, top + METER - lit, METER, lit);
    }

    /** The arrow, the same way but from the left. */
    protected static void arrow(GuiGraphicsExtractor graphics, int left, int top, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, left, top,
                ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT, COLOR_UNLIT);
        int filled = Mth.ceil(fill * ARROW_SPRITE_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT,
                    0, 0, left, top, filled, ARROW_SPRITE_HEIGHT);
        }
    }

    /** A flat bar filling left to right, for the things no sprite fits: a craft, a swing, steam. */
    protected static void bar(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
            float fill, int colour) {
        graphics.fill(left, top, left + width, top + height, COLOR_TRACK);
        int amount = Math.round(width * fill);
        if (amount > 0) {
            graphics.fill(left, top, left + amount, top + height, colour);
        }
    }

    /**
     * A bar of a fluid, in the colour the fluid is drawn with in the world.
     *
     * <p>The colour is read off the fluid's own model - the tint its still texture is drawn with -
     * so a tank of heavy oil is the brown Factorio made it and a tank of water is water, with no
     * table of colours kept here to drift from the one in the fluid mod. An empty bar, or one with
     * no fluid to name, is the plain fill colour.
     */
    protected static void fluidBar(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
            float fill, @Nullable Fluid fluid) {
        int colour = fluid == null || fluid == Fluids.EMPTY ? COLOR_FILL : fluidColour(fluid);
        bar(graphics, left, top, width, height, fill, colour);
    }

    /** The colour a fluid is drawn with in the world, opaque. */
    public static int fluidColour(Fluid fluid) {
        FluidState state = fluid.defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state);
        FluidTintSource tint = model.fluidTintSource();
        return tint == null ? 0xFFFFFFFF : tint.color(state) | 0xFF000000;
    }

    protected static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
