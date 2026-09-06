package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvismachines.NauvisMachines;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The furnace's screen: what goes in, what is burning, what comes out, and how far along.
 *
 * <p>The assembler's dark panel, with vanilla's slot sprite on it and a flame and an arrow in
 * vanilla's pixel idiom, drawn as vanilla's furnace draws them - dark as their empty meters and
 * lit as far as they are full. Not vanilla's own flame and arrow: those carry the panel's grey
 * behind them and are boxes on a dark panel, so the two are this pack's, from
 * {@code texture-workshop/make_gui_textures.py}. The electric tier has no fuel slot and no flame;
 * in the flame's place it has the bolt every electric machine here draws.
 */
public class FurnaceScreen extends AbstractContainerScreen<FurnaceMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    private static final int COLOR_UNLIT = 0xFF3B3B3B;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier FLAME_SPRITE = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "meter_flame");
    private static final Identifier ARROW_SPRITE = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "meter_arrow");
    private static final Identifier BOLT_SPRITE = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "charge_bolt");
    private static final int METER = 14;

    /** The flame, beside the fuel slot and under the input: 26..40 is clear of both wells. */
    private static final int FLAME_X = 26;
    private static final int FLAME_Y = 35;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** Vanilla's arrow, between the input column and the output well. */
    private static final int ARROW_X = 72;
    private static final int ARROW_Y = 30;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

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

        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    x + slot.x - 1, y + slot.y - 1, METER + 4, METER + 4);
        }

        // The flame, or the bolt in its place: the same meter, drawn the same way.
        if (menu.isBurner()) {
            meter(graphics, FLAME_SPRITE, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        } else {
            meter(graphics, BOLT_SPRITE, x + FLAME_X, y + FLAME_Y, menu.charge());
        }

        // The arrow: the whole sprite dark, then the lit one over it as far as the smelt is.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE,
                x + ARROW_X, y + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, COLOR_UNLIT);
        int filled = Mth.ceil(menu.craftProgress() * ARROW_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE, ARROW_WIDTH, ARROW_HEIGHT,
                    0, 0, x + ARROW_X, y + ARROW_Y, filled, ARROW_HEIGHT);
        }
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);
        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);
    }

    /** One line saying what the furnace is doing, or why it is not. The hover readout says the same. */
    private Component statusLine() {
        FurnaceBlockEntity.Status status = menu.status();
        if (status == FurnaceBlockEntity.Status.SMELTING) {
            Item making = menu.smelting();
            if (making == null) {
                return Component.translatable("status.nauvis_machines.furnace.unknown");
            }
            return Component.translatable("status.nauvis_machines.furnace.smelting",
                    new ItemStack(making).getHoverName());
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
