package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The furnace's screen: what goes in, what is burning, what comes out, and how far along.
 *
 * <p>The flame beside the fuel slot and the arrow to the output, drawn as vanilla's furnace draws
 * them. The electric tier has no fuel slot and no flame; in the flame's place it has the bolt
 * every electric machine here draws.
 */
public class FurnaceScreen extends MachineScreen<FurnaceMenu> {

    /** The flame, beside the fuel slot and under the input: 26..40 is clear of both wells. */
    private static final int FLAME_X = 26;
    private static final int FLAME_Y = 35;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    /** The arrow, between the input column and the output well. */
    private static final int ARROW_X = 72;
    private static final int ARROW_Y = 30;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    /** Clear of the fuel well, which ends at 52, and vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    public FurnaceScreen(FurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        // The flame, or the bolt in its place: the same meter, drawn the same way.
        if (menu.isBurner()) {
            meter(graphics, FLAME, x + FLAME_X, y + FLAME_Y, menu.isBurning() ? menu.burnProgress() : 0.0f);
        } else {
            meter(graphics, BOLT, x + FLAME_X, y + FLAME_Y, menu.charge());
        }
        arrow(graphics, x + ARROW_X, y + ARROW_Y, menu.craftProgress());
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /** One line saying what the furnace is doing, or why it is not. The hover readout says the same. */
    @Override
    protected Component statusLine() {
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
