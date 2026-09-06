package com.jaguarm.nauvismachines.machine.furnace;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The furnace's screen, which is vanilla's furnace screen.
 *
 * <p>The same panel, the same three slots in the same places, the same flame and the same
 * arrow, from vanilla's own texture and sprites - because a furnace is the one machine here that
 * Minecraft already has a picture of, and Yannic has said vanilla's interface fits Minecraft's
 * art far better than a flat painted panel does. The assembler's screen is still painted, and is
 * the next to change.
 *
 * <p>What vanilla's furnace does not have is a status line, so the reason a furnace has stopped
 * is a tooltip over the arrow rather than a line of text, and the hover readout outside the
 * screen says the same thing. The electric tier has no fuel slot: its well is painted over with a
 * patch of the panel and the flame's place holds a charge bar instead.
 */
public class FurnaceScreen extends AbstractContainerScreen<FurnaceMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier BURN_PROGRESS = Identifier.withDefaultNamespace("container/furnace/burn_progress");

    /** Vanilla's flame, between the input and the fuel slot. */
    private static final int FLAME_X = 56;
    private static final int FLAME_Y = 36;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** Vanilla's arrow, filling left to right. */
    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 34;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    /**
     * A patch of plain panel from the texture, for covering the fuel well on the electric tier:
     * the space left of the input slot is background and nothing else.
     */
    private static final int PATCH_U = 8;
    private static final int PATCH_V = 17;

    /** The charge bar, in the flame's place, in the assembler's electricity colour. */
    private static final int COLOR_TRACK = 0xFF373737;
    private static final int COLOR_CHARGE = 0xFFFFD24A;

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
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        if (menu.isBurner()) {
            if (menu.isBurning()) {
                int lit = Mth.ceil(menu.burnProgress() * 13.0F) + 1;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, FLAME_WIDTH, FLAME_HEIGHT,
                        0, FLAME_HEIGHT - lit, x + FLAME_X, y + FLAME_Y + FLAME_HEIGHT - lit, FLAME_WIDTH, lit);
            }
        } else {
            // No fuel slot: the well is painted over, and the flame's place is the charge.
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + FurnaceMenu.FUEL_X - 1, y + FurnaceMenu.FUEL_Y - 1, PATCH_U, PATCH_V, 18, 18, 256, 256);
            int left = x + FLAME_X;
            int top = y + FLAME_Y;
            graphics.fill(left, top, left + FLAME_WIDTH, top + FLAME_HEIGHT, COLOR_TRACK);
            int height = Math.round(FLAME_HEIGHT * menu.charge());
            if (height > 0) {
                graphics.fill(left, top + FLAME_HEIGHT - height, left + FLAME_WIDTH, top + FLAME_HEIGHT, COLOR_CHARGE);
            }
        }

        int filled = Mth.ceil(menu.craftProgress() * ARROW_WIDTH);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_PROGRESS, ARROW_WIDTH, ARROW_HEIGHT,
                0, 0, x + ARROW_X, y + ARROW_Y, filled, ARROW_HEIGHT);
    }

    /** Hovering the arrow says what the furnace is doing, or why it is not. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int left = leftPos + ARROW_X;
        int top = topPos + ARROW_Y;
        if (mouseX >= left && mouseX < left + ARROW_WIDTH && mouseY >= top && mouseY < top + ARROW_HEIGHT) {
            graphics.setComponentTooltipForNextFrame(font, List.of(statusLine()), mouseX, mouseY);
        }
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
