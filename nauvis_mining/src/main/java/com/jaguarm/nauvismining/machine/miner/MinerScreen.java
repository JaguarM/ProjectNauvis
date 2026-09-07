package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

/**
 * The drill's screen: the pickaxe it digs with, what it burns or how charged it is, what it has
 * dug, and how far into the next ore it is.
 *
 * <p>The flame beside the fuel slot and the arrow to the output, drawn as the furnace draws
 * them. The electric tier has no fuel slot and no flame; in the flame's place it has the bolt
 * every electric machine here draws, and a row of module slots along the top.
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
