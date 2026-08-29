package com.jaguarm.nauvisresearch.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.StartResearchPayload;
import com.jaguarm.nauvisresearch.research.Technology;
import com.jaguarm.nauvisresearch.research.TechnologyLayout;

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
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The technology tree, drawn as a tree.
 *
 * <h2>This file paints; it does not decide</h2>
 *
 * <p>Where everything goes is {@link TechnologyLayout}'s, and that split is the whole reason this
 * was worth doing in two pieces. Nothing in this repository can look at a screen - a permanent
 * hole rather than a gap in the suite - so the half that is <em>true or false</em> lives in common
 * code with {@code technology_layout_is_sound} on it, and what is left here is the half that is
 * <em>nice or ugly</em> and can only ever be judged by somebody looking.
 *
 * <p>So this file has no idea what a prerequisite is. It is handed cells and arrows and turns them
 * into pixels.
 *
 * <h2>What a node says without being clicked</h2>
 *
 * <p>Colour is state, and the states are the questions a player actually has: done, being
 * researched now, startable, waiting on a craft, or not yet reachable. <b>A locked node is drawn
 * rather than hidden</b>, dimmed and still hoverable, because a tree is mostly a thing you read
 * ahead in - hiding what you cannot do yet turns a map into a torch beam. That was exactly the bug
 * in the list this replaces, and it would be no better in a nicer font.
 *
 * <p>Pan by dragging; there is no zoom. At six columns by eleven rows the tree fits a window
 * without one, and a zoom that scaled text and item icons is real work that buys nothing until the
 * tree is several times this size.
 */
public class ResearchScreen extends Screen {

    /** A node is vanilla's advancement frame size, because that is what the eye is trained on. */
    private static final int NODE = 26;
    private static final int COLUMN_STEP = 76;
    private static final int ROW_STEP = 34;

    private static final int PADDING = 6;
    private static final int MARGIN = 16;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_CANVAS = 0xFF1A1A1A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_EDGE = 0xFF4A4A4A;
    private static final int COLOR_EDGE_DONE = 0xFF6FDF8F;

    /** Node fills, one per state. These are the five answers the tree gives at a glance. */
    private static final int COLOR_DONE = 0xFF2E5E3E;
    private static final int COLOR_CURRENT = 0xFF2A5A6A;
    private static final int COLOR_AVAILABLE = 0xFF4A4A4A;
    private static final int COLOR_TRIGGER = 0xFF3E5E46;
    private static final int COLOR_LOCKED = 0xFF262626;

    private static final int COLOR_BORDER_CURRENT = 0xFF6FC3DF;
    private static final int COLOR_BORDER_DONE = 0xFF6FDF8F;

    private int left;
    private int top;
    private int paneWidth;
    private int paneHeight;
    private int headerHeight;

    private int scrollX;
    private int scrollY;
    private boolean dragging;

    private TechnologyLayout.Layout layout = new TechnologyLayout.Layout(List.of(), List.of(), 0, 0);

    public ResearchScreen() {
        super(Component.translatable("screen.nauvis_research.research"));
    }

    @Override
    protected void init() {
        super.init();
        headerHeight = font.lineHeight + PADDING * 2;
        left = MARGIN;
        top = MARGIN;
        paneWidth = width - MARGIN * 2;
        paneHeight = height - MARGIN * 2;

        layout = TechnologyLayout.of(minecraft.level.registryAccess());

        // Open looking at whatever is being researched. A tree that always opened at the origin
        // would make the one thing you came to check something you have to go and find.
        ResourceKey<Technology> current = ClientResearch.current();
        TechnologyLayout.Placed focus = current == null ? null : layout.at(current);
        if (focus != null) {
            scrollX = nodeX(focus.column()) - (paneWidth - NODE) / 2;
            scrollY = nodeY(focus.row()) - (viewHeight() - NODE) / 2;
        }
        clampScroll();
    }

    private int viewHeight() {
        return paneHeight - headerHeight;
    }

    private int canvasWidth() {
        return Math.max(0, (layout.columns() - 1) * COLUMN_STEP + NODE);
    }

    private int canvasHeight() {
        return Math.max(0, (layout.rows() - 1) * ROW_STEP + NODE);
    }

    private static int nodeX(int column) {
        return column * COLUMN_STEP;
    }

    private static int nodeY(int row) {
        return row * ROW_STEP;
    }

    /**
     * Keeps the canvas on screen, and does nothing when it already fits.
     *
     * <p>The bounds are written as min/max of zero and the overflow so that a tree smaller than
     * its pane cannot be scrolled at all - otherwise a six-column tree in a wide window would
     * drift off the left edge and look broken.
     */
    private void clampScroll() {
        int overflowX = canvasWidth() - paneWidth + PADDING * 2;
        int overflowY = canvasHeight() - viewHeight() + PADDING * 2;
        scrollX = Mth.clamp(scrollX, Math.min(0, overflowX), Math.max(0, overflowX));
        scrollY = Mth.clamp(scrollY, Math.min(0, overflowY), Math.max(0, overflowY));
    }

    // ------------------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.fill(left - 1, top - 1, left + paneWidth + 1, top + paneHeight + 1, COLOR_FRAME);
        graphics.fill(left, top, left + paneWidth, top + paneHeight, COLOR_BACKGROUND);
        graphics.text(font, heading(), left + PADDING, top + PADDING, COLOR_TEXT, false);

        int canvasTop = top + headerHeight;
        graphics.fill(left + 1, canvasTop, left + paneWidth - 1, top + paneHeight - 1, COLOR_CANVAS);

        // Everything below is in canvas coordinates and clipped to the pane, so a node at the edge
        // is cut off rather than drawn over the header.
        graphics.enableScissor(left + 1, canvasTop, left + paneWidth - 1, top + paneHeight - 1);

        int originX = left + PADDING - scrollX;
        int originY = canvasTop + PADDING - scrollY;

        // Edges first, so a node always sits on top of the wires rather than under them.
        for (TechnologyLayout.Edge edge : layout.edges()) {
            renderEdge(graphics, edge, originX, originY);
        }

        TechnologyLayout.Placed hovered = null;
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            int x = originX + nodeX(placed.column());
            int y = originY + nodeY(placed.row());
            boolean over = mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE
                    && mouseY >= canvasTop && mouseY < top + paneHeight;
            renderNode(graphics, placed, x, y, over);
            if (over) {
                hovered = placed;
            }
        }

        graphics.disableScissor();

        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    /**
     * One prerequisite arrow, drawn as an elbow rather than a diagonal.
     *
     * <p>Right out of the parent, across, then right into the child - which is how vanilla's
     * advancement screen and Factorio's technology screen both do it, and not only for taste:
     * several diagonals converging on one node are impossible to tell apart, where elbows share
     * their horizontal runs and read as a bus. This tree has a node with three prerequisites and
     * a column of eleven hanging off one parent, so it matters here.
     */
    private void renderEdge(GuiGraphicsExtractor graphics, TechnologyLayout.Edge edge,
            int originX, int originY) {

        TechnologyLayout.Placed from = layout.at(edge.from());
        TechnologyLayout.Placed to = layout.at(edge.to());
        if (from == null || to == null) {
            return;
        }

        int colour = ClientResearch.isCompleted(edge.from()) ? COLOR_EDGE_DONE : COLOR_EDGE;
        int x0 = originX + nodeX(from.column()) + NODE;
        int y0 = originY + nodeY(from.row()) + NODE / 2;
        int x1 = originX + nodeX(to.column());
        int y1 = originY + nodeY(to.row()) + NODE / 2;
        int mid = (x0 + x1) / 2;

        graphics.fill(x0, y0, mid + 1, y0 + 1, colour);
        graphics.fill(mid, Math.min(y0, y1), mid + 1, Math.max(y0, y1) + 1, colour);
        graphics.fill(mid, y1, x1, y1 + 1, colour);
    }

    private void renderNode(GuiGraphicsExtractor graphics, TechnologyLayout.Placed placed,
            int x, int y, boolean hovered) {

        ResourceKey<Technology> key = placed.key();
        Technology technology = placed.technology();

        boolean done = ClientResearch.isCompleted(key);
        boolean current = key.equals(ClientResearch.current());
        boolean available = ClientResearch.isAvailable(minecraft.level.registryAccess(), key);

        int fill = done ? COLOR_DONE
                : current ? COLOR_CURRENT
                : !available ? COLOR_LOCKED
                : technology.isTriggered() ? COLOR_TRIGGER
                : COLOR_AVAILABLE;

        int border = done ? COLOR_BORDER_DONE
                : current ? COLOR_BORDER_CURRENT
                : hovered ? COLOR_TEXT
                : COLOR_FRAME;

        graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
        graphics.fill(x, y, x + NODE, y + NODE, fill);
        graphics.item(new ItemStack(iconOf(technology)), x + (NODE - 16) / 2, y + (NODE - 16) / 2);

        // A bar under whatever is actually moving, so progress is visible without hovering - which
        // for the triggered technologies is the whole of the early game.
        float progress = progressOf(key, technology);
        if (progress > 0.0f) {
            int filled = Math.round((NODE - 2) * Math.clamp(progress, 0.0f, 1.0f));
            graphics.fill(x + 1, y + NODE - 3, x + NODE - 1, y + NODE - 1, COLOR_FRAME);
            graphics.fill(x + 1, y + NODE - 3, x + 1 + filled, y + NODE - 1, COLOR_BORDER_CURRENT);
        }
    }

    /** How far along this technology is, or 0 when it is not the one moving. */
    private float progressOf(ResourceKey<Technology> key, Technology technology) {
        if (ClientResearch.isCompleted(key)) {
            return 0.0f;
        }
        if (key.equals(ClientResearch.current()) && technology.units() > 0) {
            return ClientResearch.units() / (float) technology.units();
        }
        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            return Math.min(ClientResearch.made(trigger.item()), trigger.count())
                    / (float) trigger.count();
        }
        return 0.0f;
    }

    /**
     * What to draw in the frame.
     *
     * <p>The first thing the technology unlocks that this game actually has, because that is how a
     * player looks for one - "the node that gives me the drill". Falls back to a science pack and
     * then to the lab. Unlike the advancement icons this is only ever drawn and never validated
     * while a file loads, so it can try the real item first and quietly move on.
     */
    private Item iconOf(Technology technology) {
        for (ResourceKey<Recipe<?>> recipe : technology.unlocks()) {
            Item item = BuiltInRegistries.ITEM.getOptional(recipe.identifier()).orElse(null);
            if (item != null) {
                return item;
            }
        }
        for (Identifier pack : technology.packs()) {
            Item item = BuiltInRegistries.ITEM.getOptional(pack).orElse(null);
            if (item != null) {
                return item;
            }
        }
        return BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("nauvis_research", "lab"));
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

    // ------------------------------------------------------------------------------- tooltip

    private List<Component> tooltip(TechnologyLayout.Placed placed) {
        Technology technology = placed.technology();
        List<Component> lines = new ArrayList<>();
        lines.add(technology.title(placed.key()));

        if (ClientResearch.isCompleted(placed.key())) {
            lines.add(Component.translatable("screen.nauvis_research.research.done")
                    .withStyle(ChatFormatting.GREEN));
        } else if (ClientResearch.isAvailable(minecraft.level.registryAccess(), placed.key())) {
            lines.add(cost(technology));
        } else {
            lines.add(waitingFor(placed));
        }

        // What it hands over. This is the reason to research it, and the one thing a graph of
        // names cannot tell you.
        List<Component> unlocks = new ArrayList<>();
        for (ResourceKey<Recipe<?>> recipe : technology.unlocks()) {
            BuiltInRegistries.ITEM.getOptional(recipe.identifier())
                    .ifPresent(item -> unlocks.add(new ItemStack(item).getHoverName()));
        }
        if (!unlocks.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("screen.nauvis_research.research.unlocks")
                    .withStyle(ChatFormatting.GRAY));
            for (Component unlock : unlocks) {
                lines.add(Component.literal("  ").append(unlock).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** What a technology that cannot be started yet is waiting for - its unmet prerequisites. */
    private Component waitingFor(TechnologyLayout.Placed placed) {
        Component needs = Component.empty();
        boolean first = true;
        for (ResourceKey<Technology> prerequisite : placed.technology().prerequisites()) {
            if (ClientResearch.isCompleted(prerequisite)) {
                continue;
            }
            Technology value = technologyOf(prerequisite);
            Component name = value == null
                    ? Component.literal(prerequisite.identifier().getPath())
                    : value.title(prerequisite);
            needs = first ? name.copy() : needs.copy().append(", ").append(name);
            first = false;
        }
        return Component.translatable("screen.nauvis_research.research.needs", needs)
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private Component cost(Technology technology) {
        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            return Component.translatable("screen.nauvis_research.research.trigger",
                    trigger.count(),
                    BuiltInRegistries.ITEM.getOptional(trigger.item())
                            .map(item -> new ItemStack(item).getHoverName())
                            .orElse(Component.literal(trigger.item().toString())),
                    Math.min(ClientResearch.made(trigger.item()), trigger.count()))
                    .withStyle(ChatFormatting.AQUA);
        }
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

    private @Nullable Technology technologyOf(ResourceKey<Technology> key) {
        return ModTechnologies.registry(minecraft.level.registryAccess()).get(key)
                .map(Holder.Reference::value).orElse(null);
    }

    // --------------------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        TechnologyLayout.Placed hit = nodeAt(event.x(), event.y());
        if (hit == null) {
            // Empty canvas: this press begins a pan. Swallowed either way, so a stray click on the
            // background never falls through to whatever is behind the screen.
            dragging = true;
            return true;
        }

        // A triggered technology is not something you start, and neither is one whose
        // prerequisites are outstanding. Both are drawn and both are hoverable; neither answers a
        // click, which is why the tooltip has to say which it is.
        if (hit.technology().isTriggered()
                || ClientResearch.isCompleted(hit.key())
                || !hit.technology().isResearchable()
                || !ClientResearch.isAvailable(minecraft.level.registryAccess(), hit.key())) {
            return true;
        }

        // Clicking the current research again stops it, which is the only way to stop.
        Optional<ResourceKey<Technology>> pick = hit.key().equals(ClientResearch.current())
                ? Optional.empty()
                : Optional.of(hit.key());
        ClientPacketDistributor.sendToServer(new StartResearchPayload(pick));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scrollX -= (int) dx;
            scrollY -= (int) dy;
            clampScroll();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    /** Vertical by default, horizontal with shift - the same reflex as any other list. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (minecraft.hasShiftDown()) {
            scrollX -= (int) (scrollDeltaY * ROW_STEP);
        } else {
            scrollY -= (int) (scrollDeltaY * ROW_STEP);
        }
        scrollX -= (int) (scrollDeltaX * COLUMN_STEP);
        clampScroll();
        return true;
    }

    private @Nullable TechnologyLayout.Placed nodeAt(double mouseX, double mouseY) {
        int canvasTop = top + headerHeight;
        if (mouseY < canvasTop || mouseY >= top + paneHeight) {
            return null;
        }
        int originX = left + PADDING - scrollX;
        int originY = canvasTop + PADDING - scrollY;
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            int x = originX + nodeX(placed.column());
            int y = originY + nodeY(placed.row());
            if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE) {
                return placed;
            }
        }
        return null;
    }

    /** The tree keeps working while it is open, which is half the reason to look at it. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
