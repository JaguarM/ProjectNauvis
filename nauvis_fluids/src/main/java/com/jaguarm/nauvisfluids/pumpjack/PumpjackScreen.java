package com.jaguarm.nauvisfluids.pumpjack;

import com.jaguarm.nauvislib.client.MachineScreen;
import com.jaguarm.nauvisfluids.registry.ModFluids;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The pumpjack's screen: its two module slots, the charge, the cycle, and how full the tank is.
 * The status line says what the hover readout says.
 */
public class PumpjackScreen extends MachineScreen<PumpjackMenu> {

    /** The bolt, left of the module row. */
    private static final int CHARGE_X = 44;
    private static final int CHARGE_Y = 26;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /** The cycle, a row under the modules. */
    private static final int ARROW_X = 72;
    private static final int ARROW_Y = 38;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    /** The tank, in crude oil's colour, to the right of the arrow. */
    private static final int TANK_X = 104;
    private static final int TANK_Y = 42;
    private static final int TANK_WIDTH = 64;
    private static final int TANK_HEIGHT = 8;

    /** Clear of the arrow, which ends at 54, and vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    public PumpjackScreen(PumpjackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        meter(graphics, BOLT, x + CHARGE_X, y + CHARGE_Y, menu.charge());
        arrow(graphics, x + ARROW_X, y + ARROW_Y, menu.cycleProgress());
        fluidBar(graphics, x + TANK_X, y + TANK_Y, TANK_WIDTH, TANK_HEIGHT, menu.tankFill(),
                ModFluids.CRUDE_OIL.get());
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    @Override
    protected Component statusLine() {
        return statusText(menu.status());
    }

    /** The hover readout's words, one line per status. */
    public static Component statusText(PumpjackStatus status) {
        return Component.translatable(switch (status) {
            case PUMPING -> "jade.nauvis_fluids.pumpjack.pumping";
            case OUTPUT_FULL -> "jade.nauvis_fluids.pumpjack.full";
            case NO_POWER -> "jade.nauvis_fluids.pumpjack.no_power";
            case NO_WELL -> "jade.nauvis_fluids.pumpjack.no_well";
        });
    }
}
