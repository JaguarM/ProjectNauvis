package com.jaguarm.nauvismilitary.turret;

import com.jaguarm.nauvislib.client.MachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The turret's screen: the magazine slot, and a line saying what it is doing. */
public class GunTurretScreen extends MachineScreen<GunTurretMenu> {

    /** Clear of the slot well, which ends at 47, and vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    public GunTurretScreen(GunTurretMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        // The slot is drawn by the panel; a turret has no meter.
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    @Override
    protected Component statusLine() {
        return statusText(menu.status(), menu.roundsLeft());
    }

    /** The one lang key per status, shared with the hover readout; a loaded turret says how many rounds. */
    public static Component statusText(GunTurretBlockEntity.Status status, int rounds) {
        return switch (status) {
            case NO_AMMO -> Component.translatable("status.nauvis_military.turret.no_ammo");
            case WATCHING -> Component.translatable("status.nauvis_military.turret.watching", rounds);
            case FIRING -> Component.translatable("status.nauvis_military.turret.firing", rounds);
        };
    }
}
