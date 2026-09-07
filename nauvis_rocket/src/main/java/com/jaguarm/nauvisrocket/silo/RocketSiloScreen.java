package com.jaguarm.nauvisrocket.silo;

import java.util.List;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The silo's screen: the part being built, the rocket filling up, the charge, the two launch
 * controls, and one line saying what it is waiting for.
 *
 * <p>The controls are Factorio's: a toggle for automatic launch, on by default, and a Launch
 * button that sends a complete rocket up by hand, cargo or none. Both go to the server through
 * vanilla's menu-button packet, which is what the enchanting table and the loom use, so there
 * is no payload of this mod's to carry.
 */
public class RocketSiloScreen extends MachineScreen<RocketSiloMenu> {

    /** The part under way: a bar between the satellite's well and the output's. */
    private static final int ARROW_X = 100;
    private static final int ARROW_Y = 24;
    private static final int ARROW_WIDTH = 30;
    private static final int ARROW_HEIGHT = 6;

    /** The charge, a bolt at the right end of the first row, past the output's well. */
    private static final int CHARGE_X = 156;
    private static final int CHARGE_Y = 17;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /** The rocket: parts built over parts needed, to the right of the module slots. */
    private static final int ROCKET_X = 84;
    private static final int ROCKET_Y = 40;
    private static final int ROCKET_WIDTH = 24;
    private static final int ROCKET_HEIGHT = 6;

    /** The Launch button, lit only while there is a rocket to launch. */
    private static final int LAUNCH_X = 110;
    private static final int LAUNCH_Y = 36;
    private static final int LAUNCH_WIDTH = 40;
    private static final int LAUNCH_HEIGHT = 14;

    /** The automatic-launch toggle: an A, green when on. */
    private static final int AUTO_X = 152;
    private static final int AUTO_Y = 36;
    private static final int AUTO_WIDTH = 16;
    private static final int AUTO_HEIGHT = 14;

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

        boolean launchHovered = within(mouseX, mouseY, x + LAUNCH_X, y + LAUNCH_Y, LAUNCH_WIDTH, LAUNCH_HEIGHT);
        graphics.fill(x + LAUNCH_X, y + LAUNCH_Y, x + LAUNCH_X + LAUNCH_WIDTH, y + LAUNCH_Y + LAUNCH_HEIGHT,
                menu.canLaunch() ? (launchHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON) : COLOR_TRACK);

        boolean autoHovered = within(mouseX, mouseY, x + AUTO_X, y + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT);
        graphics.fill(x + AUTO_X, y + AUTO_Y, x + AUTO_X + AUTO_WIDTH, y + AUTO_Y + AUTO_HEIGHT,
                autoHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component launch = Component.translatable("screen.nauvis_rocket.silo.launch");
        graphics.text(font, launch, LAUNCH_X + (LAUNCH_WIDTH - font.width(launch)) / 2, LAUNCH_Y + 3,
                menu.canLaunch() ? COLOR_TEXT : COLOR_MUTED, false);
        Component auto = Component.translatable("screen.nauvis_rocket.silo.auto");
        graphics.text(font, auto, AUTO_X + (AUTO_WIDTH - font.width(auto)) / 2, AUTO_Y + 3,
                menu.autoLaunch() ? COLOR_FILL : COLOR_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (within(mouseX, mouseY, leftPos + AUTO_X, topPos + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(menu.autoLaunch()
                    ? "screen.nauvis_rocket.silo.auto.on" : "screen.nauvis_rocket.silo.auto.off")), mouseX, mouseY);
        } else if (within(mouseX, mouseY, leftPos + LAUNCH_X, topPos + LAUNCH_Y, LAUNCH_WIDTH, LAUNCH_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(menu.canLaunch()
                    ? "screen.nauvis_rocket.silo.launch.ready" : "screen.nauvis_rocket.silo.launch.not_yet")), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (within(event.x(), event.y(), leftPos + AUTO_X, topPos + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT)) {
            press(RocketSiloMenu.BUTTON_AUTO_LAUNCH);
            return true;
        }
        if (menu.canLaunch() && within(event.x(), event.y(), leftPos + LAUNCH_X, topPos + LAUNCH_Y, LAUNCH_WIDTH, LAUNCH_HEIGHT)) {
            press(RocketSiloMenu.BUTTON_LAUNCH);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Vanilla's menu-button packet: the server's menu gets {@code clickMenuButton} with the id. */
    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /** What the silo is doing, or waiting for, and how far the rocket has got. */
    @Override
    protected Component statusLine() {
        return statusText(menu.status(), menu.parts(), menu.partsNeeded(), menu.owed(), menu.autoLaunch());
    }

    /** One line per state, shared with the hover readout so the two say the same thing. */
    public static Component statusText(RocketSiloStatus status, int parts, int needed, int owed, boolean auto) {
        return switch (status) {
            case NO_RECIPE -> Component.translatable("status.nauvis_rocket.silo.no_recipe");
            case NO_INGREDIENTS -> Component.translatable("status.nauvis_rocket.silo.no_ingredients", parts, needed);
            case NO_POWER -> Component.translatable("status.nauvis_rocket.silo.no_power");
            case BUILDING -> Component.translatable("status.nauvis_rocket.silo.building", parts, needed);
            case READY -> Component.translatable(auto
                    ? "status.nauvis_rocket.silo.ready" : "status.nauvis_rocket.silo.ready_manual");
            case LAUNCHING -> owed > 0
                    ? Component.translatable("status.nauvis_rocket.silo.owed", owed)
                    : Component.translatable("status.nauvis_rocket.silo.launching");
        };
    }
}
