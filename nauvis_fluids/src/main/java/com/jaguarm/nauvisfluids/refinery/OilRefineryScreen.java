package com.jaguarm.nauvisfluids.refinery;

import java.util.List;

import com.jaguarm.nauvisfluids.processing.ProcessingScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The refinery's screen: the two inputs down the left, the three outputs down the right, and the
 * progress and charge between them. The same arrangement as the machine's own ports, seen from
 * the front.
 *
 * <p>The numbers are here rather than in the base class because {@code tools/check_gui_layout.py}
 * reads them out of this file and fails the build on two things drawn through each other.
 */
public class OilRefineryScreen extends ProcessingScreen<OilRefineryMenu> {

    /** The input bars: water above, crude below, at the left. */
    private static final int IN0_X = 8;
    private static final int IN0_Y = 18;
    private static final int IN0_WIDTH = 56;
    private static final int IN0_HEIGHT = 8;
    private static final int IN1_X = 8;
    private static final int IN1_Y = 30;
    private static final int IN1_WIDTH = 56;
    private static final int IN1_HEIGHT = 8;

    /** The output bars: heavy, light, petroleum, top to bottom, at the right. */
    private static final int OUT0_X = 112;
    private static final int OUT0_Y = 18;
    private static final int OUT0_WIDTH = 56;
    private static final int OUT0_HEIGHT = 8;
    private static final int OUT1_X = 112;
    private static final int OUT1_Y = 30;
    private static final int OUT1_WIDTH = 56;
    private static final int OUT1_HEIGHT = 8;
    private static final int OUT2_X = 112;
    private static final int OUT2_Y = 42;
    private static final int OUT2_WIDTH = 56;
    private static final int OUT2_HEIGHT = 8;

    /** The progress bar, between the two columns. */
    private static final int ARROW_X = 70;
    private static final int ARROW_Y = 24;
    private static final int ARROW_WIDTH = 34;
    private static final int ARROW_HEIGHT = 6;

    /** The charge, a bolt under the progress bar. */
    private static final int CHARGE_X = 80;
    private static final int CHARGE_Y = 34;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /** The status line, under the bars and clear of vanilla's "Inventory" label at 72. */
    private static final int STATUS_Y = 58;

    private static final List<Box> TANKS = List.of(
            new Box(IN0_X, IN0_Y, IN0_WIDTH, IN0_HEIGHT),
            new Box(IN1_X, IN1_Y, IN1_WIDTH, IN1_HEIGHT),
            new Box(OUT0_X, OUT0_Y, OUT0_WIDTH, OUT0_HEIGHT),
            new Box(OUT1_X, OUT1_Y, OUT1_WIDTH, OUT1_HEIGHT),
            new Box(OUT2_X, OUT2_Y, OUT2_WIDTH, OUT2_HEIGHT));
    private static final Box PROGRESS = new Box(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT);

    public OilRefineryScreen(OilRefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected List<Box> tankBars() {
        return TANKS;
    }

    @Override
    protected Box progressBar() {
        return PROGRESS;
    }

    @Override
    protected int chargeX() {
        return CHARGE_X;
    }

    @Override
    protected int chargeY() {
        return CHARGE_Y;
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }
}
