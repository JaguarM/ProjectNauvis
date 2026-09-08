package com.jaguarm.nauvismining.machine.miner;

import java.util.List;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

/**
 * The drill's screen: the pickaxe it digs with, what it burns or how charged it is, what it has
 * dug, how far into the next ore it is, and the toggle that keeps it to Factorio's ores.
 */
public class MinerScreen extends MachineScreen<MinerMenu> {

    /** The flame, beside the fuel slot and under the pickaxe: 26..40 is clear of both wells. */
    private static final int FLAME_X = 26;
    private static final int FLAME_Y = 35;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** The arrow, between the tool column and the output well, a row under the modules. */
    private static final int ARROW_X = 72;
    private static final int ARROW_Y = 38;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    /** The Factorio-ores toggle: an F, green when on, left of the pickaxe on the first row. */
    private static final int MODE_X = 8;
    private static final int MODE_Y = 17;
    private static final int MODE_WIDTH = 16;
    private static final int MODE_HEIGHT = 14;

    /** Clear of the output well, which ends at 53, and vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    public MinerScreen(MinerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.isElectric()) {
            meter(graphics, BOLT, x + FLAME_X, y + FLAME_Y, menu.charge());
        } else {
            meter(graphics, FLAME, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        }
        arrow(graphics, x + ARROW_X, y + ARROW_Y, menu.cycleProgress());

        boolean hovered = within(mouseX, mouseY, x + MODE_X, y + MODE_Y, MODE_WIDTH, MODE_HEIGHT);
        graphics.fill(x + MODE_X, y + MODE_Y, x + MODE_X + MODE_WIDTH, y + MODE_Y + MODE_HEIGHT,
                hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component mode = Component.translatable("screen.nauvis_mining.miner.factorio_ores");
        graphics.text(font, mode, MODE_X + (MODE_WIDTH - font.width(mode)) / 2, MODE_Y + 3,
                menu.factorioOresOnly() ? COLOR_FILL : COLOR_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (within(mouseX, mouseY, leftPos + MODE_X, topPos + MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(menu.factorioOresOnly()
                    ? "screen.nauvis_mining.miner.factorio_ores.on"
                    : "screen.nauvis_mining.miner.factorio_ores.off")), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (within(event.x(), event.y(), leftPos + MODE_X, topPos + MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MinerMenu.BUTTON_FACTORIO_ORES);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /** One line saying what the drill is doing, or why it is not. The hover readout says the same. */
    @Override
    protected Component statusLine() {
        return statusText(menu.status(), menu.mining());
    }

    /** The one lang key per status, shared with the hover readout; a mining drill names its ore. */
    public static Component statusText(MinerStatus status, Block mining) {
        if (status == MinerStatus.MINING && mining != null) {
            return Component.translatable("gui.nauvis_mining.miner.status.mining_ore", mining.getName());
        }
        return status.label();
    }
}
