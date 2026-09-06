package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The burner inserter's screen: what it is burning, and how far through a swing it is.
 */
public class BurnerInserterScreen extends MachineScreen<BurnerInserterMenu> {

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
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        meter(graphics, FLAME, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        bar(graphics, x + SWING_X, y + SWING_Y, SWING_WIDTH, SWING_HEIGHT, menu.swingProgress(), COLOR_FILL);
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /**
     * Which of the two reasons a stopped inserter has stopped for.
     *
     * <p>Out of coal and nothing-to-move look identical from outside the machine, and only one of
     * them is something the player has to do anything about.
     */
    @Override
    protected Component statusLine() {
        if (!menu.isBurning()) {
            return Component.translatable("screen.nauvis_logistics.burner_inserter.no_fuel");
        }
        return Component.translatable(menu.isSwinging()
                ? "screen.nauvis_logistics.burner_inserter.working"
                : "screen.nauvis_logistics.burner_inserter.waiting");
    }
}
