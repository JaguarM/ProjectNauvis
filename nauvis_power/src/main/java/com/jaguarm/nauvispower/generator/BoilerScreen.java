package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The boiler's screen: what it is burning, how much is left of it, and how much steam is banked.
 */
public class BoilerScreen extends MachineScreen<BoilerMenu> {

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
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        meter(graphics, FLAME, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        bar(graphics, x + STEAM_X, y + STEAM_Y, STEAM_WIDTH, STEAM_HEIGHT, menu.steam(), COLOR_STEAM);
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /**
     * One line saying which of the three things a stopped boiler is doing.
     *
     * <p>Full and out of fuel look identical otherwise, and they want completely different things
     * done about them: one needs coal, the other needs somebody to draw the steam off.
     */
    @Override
    protected Component statusLine() {
        if (menu.isBurning()) {
            return Component.translatable("screen.nauvis_power.boiler.burning");
        }
        if (menu.steam() >= 1.0f) {
            return Component.translatable("screen.nauvis_power.boiler.full");
        }
        return Component.translatable("screen.nauvis_power.boiler.idle");
    }
}
