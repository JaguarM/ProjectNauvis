package com.jaguarm.nauvisresearch.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.StartResearchPayload;
import com.jaguarm.nauvisresearch.research.Technology;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The technology list.
 *
 * <h2>A list, and not the tree - on purpose</h2>
 *
 * <p>Factorio draws a graph with pan, zoom and a layout, and that is worth having eventually.
 * What it is not is where to start, because none of it is the <em>model</em>: the registry, the
 * saved state, the lock hook and the four gates behind it are the same underneath whatever is
 * drawn on top. Replacing this screen with a real tree touches this file and nothing else, which
 * is the property that made drawing a list first the cheap decision rather than the lazy one.
 *
 * <p>So: rows. The current research first with its progress, then everything whose prerequisites
 * are met, in Factorio's own order. Click a row to make it the current research. Completed
 * technologies are behind a toggle rather than in the list, because the list a player is looking
 * at is the list of things they could do next.
 *
 * <p>Painted in the same flat colours as {@code LabScreen} and Facrafting's panel. The palette is
 * copied rather than shared for the reason {@code LabScreen} gives: a shared base could only live
 * in Facrafting, and that would make this mod require it.
 */
public class ResearchScreen extends Screen {

    private static final int WIDTH = 320;
    private static final int ROW = 26;
    private static final int PADDING = 6;
    private static final int ICON = 16;

    /** How many rows fit; the rest scroll. */
    private static final int ROWS = 9;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_ROW = 0xFF242424;
    private static final int COLOR_ROW_HOVER = 0xFF3B3B3B;
    private static final int COLOR_CURRENT = 0xFF2A3F46;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    /** Research, the same blue the lab's own bar uses for the same thing. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;
    private static final int COLOR_DONE = 0xFF6FDF8F;

    /** Whether the list shows what is left to do or what has been done. Outlives the screen. */
    private static boolean showingCompleted;

    private int left;
    private int top;
    private int scroll;

    private List<Holder.Reference<Technology>> rows = List.of();

    public ResearchScreen() {
        super(Component.translatable("screen.nauvis_research.research"));
    }

    @Override
    protected void init() {
        super.init();
        left = (width - WIDTH) / 2;
        top = Math.max(PADDING, (height - listHeight()) / 2);
        refresh();
    }

    private int listHeight() {
        return PADDING * 3 + font.lineHeight + ROWS * ROW;
    }

    /**
     * Rebuilt on every frame's worth of change rather than cached.
     *
     * <p>Two hundred technologies filtered and sorted is nothing next to drawing them, and the
     * alternative is a cache that has to be invalidated when research completes - which happens
     * while this screen is open, which is the case that would be got wrong.
     */
    private void refresh() {
        List<Holder.Reference<Technology>> found = new ArrayList<>();
        var access = minecraft.level.registryAccess();
        for (Holder.Reference<Technology> holder : ModTechnologies.all(access)) {
            boolean done = ClientResearch.isCompleted(holder.key());
            if (done == showingCompleted && (done || ClientResearch.isAvailable(access, holder.key()))) {
                found.add(holder);
            }
        }
        rows = found;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        refresh();

        int height = listHeight();
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + height + 1, COLOR_FRAME);
        graphics.fill(left, top, left + WIDTH, top + height, COLOR_BACKGROUND);

        graphics.text(font, heading(), left + PADDING, top + PADDING, COLOR_TEXT, false);

        Component toggle = Component.translatable(showingCompleted
                ? "screen.nauvis_research.research.show_available"
                : "screen.nauvis_research.research.show_completed");
        graphics.text(font, toggle, left + WIDTH - PADDING - font.width(toggle), top + PADDING,
                COLOR_MUTED, false);

        int y = top + PADDING * 2 + font.lineHeight;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            renderRow(graphics, rows.get(scroll + i), y + i * ROW, mouseX, mouseY);
        }

        if (rows.isEmpty()) {
            Component empty = Component.translatable("screen.nauvis_research.research.none");
            graphics.text(font, empty, left + PADDING, y + 4, COLOR_MUTED, false);
        }
    }

    private Component heading() {
        ResourceKey<Technology> current = ClientResearch.current();
        if (current == null) {
            return Component.translatable("screen.nauvis_research.research.nothing_selected");
        }
        Technology technology = technologyOf(current);
        if (technology == null) {
            return title;
        }
        return Component.translatable("screen.nauvis_research.research.current",
                technology.title(current), ClientResearch.units(), technology.units());
    }

    private void renderRow(GuiGraphicsExtractor graphics, Holder.Reference<Technology> holder, int y,
            int mouseX, int mouseY) {

        Technology technology = holder.value();
        boolean current = holder.key().equals(ClientResearch.current());
        boolean hovered = within(mouseX, mouseY, left + PADDING, y, WIDTH - PADDING * 2, ROW - 2);

        graphics.fill(left + PADDING, y, left + WIDTH - PADDING, y + ROW - 2,
                current ? COLOR_CURRENT : hovered ? COLOR_ROW_HOVER : COLOR_ROW);

        graphics.text(font, technology.title(holder.key()), left + PADDING + 4, y + 3,
                COLOR_TEXT, false);
        graphics.text(font, cost(technology), left + PADDING + 4, y + 3 + font.lineHeight + 1,
                COLOR_MUTED, false);

        // The packs, then what it hands over, right-aligned so the two columns line up down the
        // list even though each row holds a different number of each.
        int x = left + WIDTH - PADDING - 4 - ICON;
        for (Item item : unlockIcons(technology)) {
            graphics.item(new ItemStack(item), x, y + (ROW - 2 - ICON) / 2);
            x -= ICON;
        }

        if (current) {
            int width = WIDTH - PADDING * 2;
            int filled = technology.units() <= 0 ? 0
                    : Math.round(width * Mth.clamp(ClientResearch.units() / (float) technology.units(), 0f, 1f));
            graphics.fill(left + PADDING, y + ROW - 4, left + WIDTH - PADDING, y + ROW - 2, COLOR_TRACK);
            graphics.fill(left + PADDING, y + ROW - 4, left + PADDING + filled, y + ROW - 2,
                    showingCompleted ? COLOR_DONE : COLOR_PROGRESS);
        }
    }

    /**
     * "10 x 10s" plus the packs, which is the whole of what a technology costs.
     *
     * <p>A technology asking for a pack no mod registers yet says so instead of pricing itself,
     * because most of the tree is in that state and "1000 x 60s" with an unobtainable pack reads
     * as expensive rather than as impossible.
     */
    private Component cost(Technology technology) {
        if (!technology.isResearchable()) {
            return Component.translatable("screen.nauvis_research.research.unavailable_packs")
                    .withStyle(ChatFormatting.DARK_RED);
        }
        Component packs = Component.empty();
        for (Identifier id : technology.packs()) {
            packs = packs.copy().append(BuiltInRegistries.ITEM.getOptional(id)
                    .map(item -> new ItemStack(item).getHoverName().copy().append(" "))
                    .orElse(Component.literal(id + " ")));
        }
        return Component.translatable("screen.nauvis_research.research.cost",
                technology.units(), technology.ticksPerUnit() / 20.0f, packs);
    }

    /** At most four, so a technology unlocking eight recipes does not run into its own name. */
    private List<Item> unlockIcons(Technology technology) {
        List<Item> items = new ArrayList<>(4);
        for (ResourceKey<?> recipe : technology.unlocks()) {
            // A recipe is named after the item it makes - see tools/gen_technologies.py - so the
            // recipe's own path is the item's. An unlock for an item no mod registers yet draws
            // nothing, which is most of the tree today and is honest.
            BuiltInRegistries.ITEM.getOptional(recipe.identifier()).ifPresent(items::add);
            if (items.size() == 4) {
                break;
            }
        }
        return items;
    }

    private @Nullable Technology technologyOf(ResourceKey<Technology> key) {
        return ModTechnologies.registry(minecraft.level.registryAccess()).get(key)
                .map(Holder.Reference::value).orElse(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        Component toggle = Component.translatable(showingCompleted
                ? "screen.nauvis_research.research.show_available"
                : "screen.nauvis_research.research.show_completed");
        if (within(mouseX, mouseY, left + WIDTH - PADDING - font.width(toggle), top + PADDING,
                font.width(toggle), font.lineHeight)) {
            showingCompleted = !showingCompleted;
            scroll = 0;
            return true;
        }

        int y = top + PADDING * 2 + font.lineHeight;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            if (!within(mouseX, mouseY, left + PADDING, y + i * ROW, WIDTH - PADDING * 2, ROW - 2)) {
                continue;
            }
            Holder.Reference<Technology> holder = rows.get(scroll + i);
            if (showingCompleted || !holder.value().isResearchable()) {
                return true;
            }
            // Clicking the current research again stops it, which is the only way to stop.
            Optional<ResourceKey<Technology>> pick = holder.key().equals(ClientResearch.current())
                    ? Optional.empty()
                    : Optional.of(holder.key());
            ClientPacketDistributor.sendToServer(new StartResearchPayload(pick));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, rows.size() - ROWS));
        return true;
    }

    /** The research screen pauses nothing: a lab keeps working while it is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
