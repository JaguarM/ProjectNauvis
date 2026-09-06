package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.client.ResearchScreen;
import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Technology;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The lab's screen: what it is working through, how far into a cycle it is, and how much research
 * it has done.
 *
 * <p>The same dark panel as the boiler's, the assembler's and Facrafting's panel, so the interface
 * reads as one thing rather than four, with vanilla's slot sprite in it and electricity drawn as a
 * bolt the way vanilla's furnace draws its flame. That mix is the one Yannic asked for.
 *
 * <p>The palette and the drawing are copied from {@code BoilerScreen} rather than shared, for the
 * reason that file gives: a shared screen base could only live in Facrafting, and that would make
 * this mod require it.
 */
public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** What a meter's sprite is tinted while it is empty: a silhouette on the panel. */
    private static final int COLOR_UNLIT = 0xFF3B3B3B;
    /** Research. The one colour on the panel that is not a machine colour. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier BOLT_SPRITE = Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "charge_bolt");
    private static final int METER = 14;

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

        // Vanilla's slot sprite, at every slot the menu has, so the screen cannot disagree with
        // the menu about where they are. The sprite is the well and its edge in one.
        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    x + slot.x - 1, y + slot.y - 1, METER + 4, METER + 4);
        }

        bar(graphics, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT,
                menu.progress(), COLOR_PROGRESS);
        meter(graphics, BOLT_SPRITE, x + CHARGE_X, y + CHARGE_Y, menu.charge());

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

    /**
     * A fourteen-pixel meter drawn the way vanilla's furnace draws its flame: the whole sprite
     * tinted dark as the empty meter, then the bright sprite over it from the bottom up, as far
     * as it is full.
     */
    private static void meter(GuiGraphicsExtractor graphics, Identifier sprite, int left, int top, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, top, METER, METER, COLOR_UNLIT);
        if (fill <= 0.0f) {
            return;
        }
        int lit = Mth.ceil(fill * (METER - 1)) + 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, METER, METER, 0, METER - lit,
                left, top + METER - lit, METER, lit);
    }

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
