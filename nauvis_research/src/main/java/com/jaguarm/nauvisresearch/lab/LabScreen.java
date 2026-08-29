package com.jaguarm.nauvisresearch.lab;

import java.util.List;

import com.jaguarm.nauvisresearch.client.ResearchScreen;
import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Technology;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;

/**
 * The lab's screen: what it is working through, how far into a cycle it is, and how much research
 * it has done.
 *
 * <p>Painted in the same flat colours as the boiler's, the assembler's and Facrafting's panel, so
 * the interface reads as one thing rather than four. There is no background texture because there
 * is no art yet, and a machine drawn in flat colours reads as unfinished on purpose where a
 * machine drawn on a borrowed vanilla panel reads as finished and slightly wrong.
 *
 * <p>The palette and the well-drawing are copied from {@code BoilerScreen} rather than shared, for
 * the reason that file gives: a shared screen base could only live in Facrafting, and that would
 * make this mod require it.
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_SLOT = 0xFF3B3B3B;
    /** Darker than the well it frames, or a row of slots renders as one grey slab. */
    private static final int COLOR_SLOT_EDGE = 0xFF1E1E1E;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** Research. The one colour on the panel that is not a machine colour. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;
    /** Charge, the same amber the assembler uses for the same thing. */
    private static final int COLOR_CHARGE = 0xFFE0B040;

    /** The cycle bar, filling left to right under the pack slots. */
    private static final int PROGRESS_X = 26;
    private static final int PROGRESS_Y = 56;
    private static final int PROGRESS_WIDTH = 108;
    private static final int PROGRESS_HEIGHT = 6;

    /** Charge, a thin bar beneath the cycle bar. */
    private static final int CHARGE_X = 26;
    private static final int CHARGE_Y = 66;
    private static final int CHARGE_WIDTH = 108;
    private static final int CHARGE_HEIGHT = 4;

    /** Clear of the bars above and vanilla's "Inventory" label at y=72. */
    private static final int STATUS_Y = 20;

    /**
     * The button that opens the technology list.
     *
     * <p>The lab is where a player is standing when they wonder what to research next, so it is
     * where the list lives. Factorio puts it behind a key of its own as well, which is worth
     * having and is not this change.
     *
     * <p>It sits to the right of the pack row because that is the only part of a 176-wide panel
     * nothing else claims: the title and the status line are each a full-width band, the bars run
     * from 26 to 134 under the slots, and vanilla's "Inventory" label owns everything from y=72
     * down. {@code tools/check_gui_layout.py} knows about this box and would fail the build if it
     * were put anywhere it overlapped something, which is how it was found - the first version of
     * it was drawn straight through the status line.
     */
    private static final int RESEARCH_X = 138;
    private static final int RESEARCH_Y = 36;
    private static final int RESEARCH_WIDTH = 30;
    private static final int RESEARCH_HEIGHT = 14;

    private static final int COLOR_BUTTON = 0xFF3B3B3B;
    private static final int COLOR_BUTTON_HOVER = 0xFF6A6A6A;

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;

        graphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, COLOR_FRAME);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BACKGROUND);

        // Two passes, because adjacent slots are exactly 18 apart and a well is 18 across: drawn
        // in one colour they tile into an unbroken rectangle.
        for (Slotish slot : slotWells()) {
            graphics.fill(x + slot.x() - 1, y + slot.y() - 1,
                    x + slot.x() + 17, y + slot.y() + 17, COLOR_SLOT_EDGE);
            graphics.fill(x + slot.x(), y + slot.y(),
                    x + slot.x() + 16, y + slot.y() + 16, COLOR_SLOT);
        }

        bar(graphics, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT,
                menu.progress(), COLOR_PROGRESS);
        bar(graphics, x + CHARGE_X, y + CHARGE_Y, CHARGE_WIDTH, CHARGE_HEIGHT,
                menu.charge(), COLOR_CHARGE);

        boolean hovered = within(mouseX, mouseY, x + RESEARCH_X, y + RESEARCH_Y,
                RESEARCH_WIDTH, RESEARCH_HEIGHT);
        graphics.fill(x + RESEARCH_X, y + RESEARCH_Y,
                x + RESEARCH_X + RESEARCH_WIDTH, y + RESEARCH_Y + RESEARCH_HEIGHT,
                hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (within(event.x(), event.y(), leftPos + RESEARCH_X, topPos + RESEARCH_Y,
                RESEARCH_WIDTH, RESEARCH_HEIGHT)) {
            minecraft.gui.setScreen(new ResearchScreen());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void bar(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
            float filled, int colour) {
        graphics.fill(left, top, left + width, top + height, COLOR_TRACK);
        int amount = Math.round(width * filled);
        if (amount > 0) {
            graphics.fill(left, top, left + amount, top + height, colour);
        }
    }

    /** Every slot's well, taken from the menu so the screen cannot disagree about where they are. */
    private List<Slotish> slotWells() {
        return menu.slots.stream().map(slot -> new Slotish(slot.x, slot.y)).toList();
    }

    private record Slotish(int x, int y) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        // Vanilla's own "Inventory" label sits on a light panel; on this one it would vanish.
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED,
                false);

        graphics.text(font, statusLine(), 8, STATUS_Y, COLOR_MUTED, false);

        Component open = Component.translatable("screen.nauvis_research.lab.open_research");
        graphics.text(font, open,
                RESEARCH_X + (RESEARCH_WIDTH - font.width(open)) / 2, RESEARCH_Y + 2,
                COLOR_TEXT, false);
    }

    /**
     * One line saying which of the four things a stopped lab is doing.
     *
     * <p>They look identical from outside and want completely different things done about them:
     * a wire, some science packs, a technology picked on the research screen, or nothing at all
     * because it is already working. The one that is easy to leave out is the third, and it is
     * the one a player meets first - a lab that is fed and powered and still does nothing is
     * indistinguishable from a broken lab unless it says so.
     *
     * <p>The current research is read from {@link ClientResearch} rather than sent with the menu:
     * it is a fact about the world, the client already has it, and a copy in the menu would be a
     * second answer that could disagree.
     */
    private Component statusLine() {
        if (!menu.hasPower()) {
            return Component.translatable("screen.nauvis_research.lab.no_power");
        }

        ResourceKey<Technology> current = ClientResearch.current();
        if (current == null) {
            return Component.translatable("screen.nauvis_research.lab.no_research");
        }
        if (!menu.isWorking()) {
            return Component.translatable("screen.nauvis_research.lab.idle");
        }

        Technology technology = ModTechnologies.registry(minecraft.level.registryAccess())
                .get(current).map(Holder.Reference::value).orElse(null);
        if (technology == null) {
            return Component.translatable("screen.nauvis_research.lab.idle");
        }
        return Component.translatable("screen.nauvis_research.lab.researching",
                technology.title(current), menu.cycles());
    }
}
