package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvismining.NauvisMining;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class MinerScreen extends AbstractContainerScreen<MinerMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            NauvisMining.MODID, "textures/gui/container/miner.png");

    /** Dark grey, matching the vanilla container label colour. */
    private static final int COLOUR_OK = -12566464;
    /** Muted red, for a machine that is waiting on the player. */
    private static final int COLOUR_BLOCKED = 0xFF9C2A2A;

    /** Semi-transparent black over module slots this tier cannot use. */
    private static final int LOCKED_SLOT_OVERLAY = 0xA0101010;

    /** Must match MinerMenu's module slot placement. */
    private static final int MODULE_SLOT_X = 44;
    private static final int MODULE_SLOT_Y = 53;

    /**
     * Its own row under the slot blocks, wide enough that a label never has to be clipped
     * or scrolled. The previous 34px button, wedged between two slots, marqueed "Clear +
     * Fill" because vanilla scrolls any label wider than its button.
     */
    private static final int MODE_BUTTON_X = 7;
    private static final int MODE_BUTTON_Y = 55;
    private static final int MODE_BUTTON_W = 102;
    private static final int MODE_BUTTON_H = 16;

    /**
     * Vanilla's furnace flame, borrowed whole: the same 14x14 sprite from the vanilla GUI
     * atlas, uncovered from the bottom over the same 13 pixels of travel, so a drill burning
     * coal empties at visibly the same rate as a furnace burning it.
     */
    private static final Identifier LIT_PROGRESS_SPRITE =
            Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final int FLAME_SIZE = 14;
    /** How far the flame travels. Vanilla's 13, with the +1 that keeps a dying flame visible. */
    private static final int FLAME_TRAVEL = 13;

    /** The tall gauge down the left edge, the shape tech mods use for power. */
    private static final int ENERGY_BAR_X = 8;
    private static final int ENERGY_BAR_Y = 17;
    private static final int ENERGY_BAR_W = 16;
    private static final int ENERGY_BAR_H = 34;

    /** Panel base colour, used to paint out module slots a tier has not unlocked. */
    private static final int PANEL = 0xFFC6C6C6;
    /** Lighter than the slot interior, so an empty gauge reads as empty rather than absent. */
    private static final int ENERGY_EMPTY = 0xFF4A4A4A;
    private static final int ENERGY_FULL = 0xFFE8A22B;

    /** 18px taller than the vanilla 166, leaving a clear row for the status line. */
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 184;

    public MinerScreen(MinerMenu menu, Inventory inventory, Component title) {
        // Passing the size here rather than assigning afterwards: imageHeight is final, and
        // this constructor also derives inventoryLabelY from it, so the "Inventory" label
        // moves down with the panel instead of colliding with the status line.
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private @Nullable Button digModeButton;

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        // Sits in the gap between the fill and fuel slots on the left column, which is the
        // only space in the layout that is not a slot.
        this.digModeButton = addRenderableWidget(Button.builder(
                        this.menu.digMode().label(),
                        button -> cycleDigMode())
                .bounds(this.leftPos + MODE_BUTTON_X, this.topPos + MODE_BUTTON_Y,
                        MODE_BUTTON_W, MODE_BUTTON_H)
                .tooltip(Tooltip.create(this.menu.digMode().description()))
                .build());
    }

    /**
     * Sends the cycle through vanilla's menu-button channel. The server owns the setting;
     * the label follows from synced data rather than being predicted here, so a rejected
     * click simply does nothing instead of showing a mode the machine is not in.
     */
    private void cycleDigMode() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId, MinerMenu.BUTTON_CYCLE_DIG_MODE);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.digModeButton != null) {
            DigMode mode = this.menu.digMode();
            this.digModeButton.setMessage(mode.label());
            this.digModeButton.setTooltip(Tooltip.create(mode.description()));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        MinerStatus status = this.menu.status();
        int colour = status == MinerStatus.RUNNING || status == MinerStatus.COMPLETE
                ? COLOUR_OK
                : COLOUR_BLOCKED;
        graphics.text(this.font, status.label(), 8, 73, colour, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                x, y,
                0.0F, 0.0F,
                this.imageWidth, this.imageHeight,
                256, 256);

        shadeLockedModuleSlots(graphics, x, y);
        hideUnusedPowerUi(graphics, x, y);
        drawEnergyBar(graphics, x, y);
        drawFlame(graphics, x, y);
    }

    /**
     * The burn timer, drawn only on burner drills.
     *
     * <p>Sits in the column the energy gauge would occupy, which this tier paints out, and
     * lines up with the fuel slot it belongs to. The maths is vanilla's
     * {@code AbstractFurnaceScreen} verbatim rather than a fraction of the sprite height:
     * ceiling plus one, so the last tick of fuel still shows a sliver of flame instead of
     * blinking out a frame early.
     */
    private void drawFlame(GuiGraphicsExtractor graphics, int originX, int originY) {
        if (this.menu.isElectric() || !this.menu.isBurning()) {
            return;
        }
        int left = originX + ENERGY_BAR_X + (ENERGY_BAR_W - FLAME_SIZE) / 2;
        int top = originY + MinerMenu.FUEL_Y + 1;
        int height = Mth.ceil(this.menu.fuelProgress() * FLAME_TRAVEL) + 1;
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                LIT_PROGRESS_SPRITE,
                FLAME_SIZE, FLAME_SIZE,
                0, FLAME_SIZE - height,
                left, top + FLAME_SIZE - height,
                FLAME_SIZE, height);
    }

    /**
     * Removes whichever power UI this drill does not have.
     *
     * <p>The texture carries both a fuel slot and a gauge well so one image serves both
     * drills, but a burner has no energy and an electric has no use for coal. Leaving the
     * wrong one visible invites the player to try filling it.
     */
    private void hideUnusedPowerUi(GuiGraphicsExtractor graphics, int originX, int originY) {
        if (this.menu.isElectric()) {
            paintOverWell(graphics, originX + MinerMenu.FUEL_X, originY + MinerMenu.FUEL_Y, 16, 16);
        } else {
            paintOverWell(graphics, originX + ENERGY_BAR_X, originY + ENERGY_BAR_Y,
                    ENERGY_BAR_W, ENERGY_BAR_H);
        }
    }

    /** Covers a well and its bevel in panel colour, so it is simply not there. */
    private void paintOverWell(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL);
    }

    /**
     * Charge gauge, drawn only on electric miners.
     *
     * <p>Occupies the fuel slot's position, which is unused on this tier: the fuel slot
     * rejects everything, so the space would otherwise be a slot that silently refuses
     * items. A gauge there says "this machine eats power, not coal" without needing a
     * separate panel.
     */
    private void drawEnergyBar(GuiGraphicsExtractor graphics, int originX, int originY) {
        if (!this.menu.isElectric()) {
            return;
        }
        int left = originX + ENERGY_BAR_X;
        int top = originY + ENERGY_BAR_Y;

        // Inset by a pixel so the slot well's own bevel frames the gauge. Filling the
        // whole well edge to edge in near-black read as a hole in the panel rather than as
        // an empty meter.
        int x0 = left + 1;
        int y0 = top + 1;
        int x1 = left + ENERGY_BAR_W - 1;
        int y1 = top + ENERGY_BAR_H - 1;

        graphics.fill(x0, y0, x1, y1, ENERGY_EMPTY);
        int height = y1 - y0;
        int filled = Math.round(height * Math.clamp(this.menu.energyProgress(), 0.0f, 1.0f));
        if (filled > 0) {
            // Fills upward, the way a tank or battery reads.
            graphics.fill(x0, y1 - filled, x1, y1, ENERGY_FULL);
        }
    }

    /**
     * Paints out module slots this tier has not unlocked.
     *
     * <p>The texture always carries four wells so the layout is fixed, but a burner drill
     * unlocks one. Greying the other three left it looking like a broken electric drill
     * rather than a simpler machine, so the unused wells are covered in the panel colour
     * and simply are not there.
     */
    private void shadeLockedModuleSlots(GuiGraphicsExtractor graphics, int originX, int originY) {
        for (int i = 0; i < MinerBlockEntity.MODULE_SLOTS; i++) {
            if (this.menu.isModuleSlotUnlocked(i)) {
                continue;
            }
            // Cover the whole 18x18 well, bevel included, or its edges stay behind as an
            // outline of a slot that is not there.
            paintOverWell(graphics,
                    originX + MinerMenu.MODULE_X + (i % 2) * 18,
                    originY + MinerMenu.MODULE_Y + (i / 2) * 18,
                    16, 16);
        }
    }
}
