package com.jaguarm.nauvisrocket.silo;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The silo's screen: the part being built, the rocket filling up, the charge, and one line
 * saying what it is waiting for.
 */
public class RocketSiloScreen extends MachineScreen<RocketSiloMenu> {

    /** The part under way: a bar between the satellite's well and the output's. */
    private static final int ARROW_X = 100;
    private static final int ARROW_Y = 24;
    private static final int ARROW_WIDTH = 30;
    private static final int ARROW_HEIGHT = 6;

    /** The rocket: parts built over parts needed, to the right of the module slots. */
    private static final int ROCKET_X = 84;
    private static final int ROCKET_Y = 38;
    private static final int ROCKET_WIDTH = 54;
    private static final int ROCKET_HEIGHT = 6;

    /** The charge, a bolt at the right of the second row. */
    private static final int CHARGE_X = 152;
    private static final int CHARGE_Y = 36;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /** Clear of the second row above and vanilla's "Inventory" label at y=72. */
    private static final int STATUS_Y = 58;

    /** The rocket's colour, which is nothing else's. */
    private static final int COLOR_ROCKET = 0xFFF0F0F0;

    public RocketSiloScreen(RocketSiloMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        bar(graphics, x + ARROW_X, y + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, menu.partProgress(), COLOR_FILL);
        bar(graphics, x + ROCKET_X, y + ROCKET_Y, ROCKET_WIDTH, ROCKET_HEIGHT, menu.rocketProgress(), COLOR_ROCKET);
        meter(graphics, BOLT, x + CHARGE_X, y + CHARGE_Y, menu.charge());
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /** What the silo is doing, or waiting for, and how far the rocket has got. */
    @Override
    protected Component statusLine() {
        return statusText(menu.status(), menu.parts(), menu.partsNeeded(), menu.owed());
    }

    /** One line per state, shared with the hover readout so the two say the same thing. */
    public static Component statusText(RocketSiloStatus status, int parts, int needed, int owed) {
        return switch (status) {
            case NO_RECIPE -> Component.translatable("status.nauvis_rocket.silo.no_recipe");
            case NO_INGREDIENTS -> Component.translatable("status.nauvis_rocket.silo.no_ingredients", parts, needed);
            case NO_POWER -> Component.translatable("status.nauvis_rocket.silo.no_power");
            case BUILDING -> Component.translatable("status.nauvis_rocket.silo.building", parts, needed);
            case READY -> Component.translatable("status.nauvis_rocket.silo.ready");
            case LAUNCHING -> owed > 0
                    ? Component.translatable("status.nauvis_rocket.silo.owed", owed)
                    : Component.translatable("status.nauvis_rocket.silo.launching");
        };
    }
}
