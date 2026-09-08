package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvislib.client.MachineScreen;
import com.jaguarm.nauvisresearch.client.ResearchScreen;
import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Technology;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;

/**
 * The lab's screen: what it is working through, how far into a cycle it is, and how much research
 * it has done.
 */
public class LabScreen extends MachineScreen<LabMenu> {

    /** Research. The one colour on the panel that is not a machine colour. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;

    /** The cycle bar, filling left to right under the pack slots. */
    private static final int PROGRESS_X = 26;
    private static final int PROGRESS_Y = 56;
    private static final int PROGRESS_WIDTH = 108;
    private static final int PROGRESS_HEIGHT = 6;

    /**
     * Charge, a bolt to the right of the cycle bar, under the research button.
     *
     * <p>The bar ends at x=134 and the button at y=50, so 138..152 by 54..68 is the one square
     * on the panel nothing else claims, and it clears vanilla's "Inventory" label at y=72.
     */
    private static final int CHARGE_X = 138;
    private static final int CHARGE_Y = 54;
    private static final int CHARGE_WIDTH = 14;
    private static final int CHARGE_HEIGHT = 14;

    /** Clear of the bars above and vanilla's "Inventory" label at y=72. */
    private static final int STATUS_Y = 20;

    /** The button that opens the technology list. */
    private static final int RESEARCH_X = 138;
    private static final int RESEARCH_Y = 36;
    private static final int RESEARCH_WIDTH = 30;
    private static final int RESEARCH_HEIGHT = 14;

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        bar(graphics, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT,
                menu.progress(), COLOR_PROGRESS);
        meter(graphics, BOLT, x + CHARGE_X, y + CHARGE_Y, menu.charge());

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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        Component open = Component.translatable("screen.nauvis_research.lab.open_research");
        graphics.text(font, open,
                RESEARCH_X + (RESEARCH_WIDTH - font.width(open)) / 2, RESEARCH_Y + 2,
                COLOR_TEXT, false);
    }

    @Override
    protected int statusY() {
        return STATUS_Y;
    }

    /** One line saying which of the four things a stopped lab is doing. */
    @Override
    protected Component statusLine() {
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
