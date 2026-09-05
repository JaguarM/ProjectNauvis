package com.jaguarm.nauvisresearch.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.StartResearchPayload;
import com.jaguarm.nauvisresearch.research.Technology;
import com.jaguarm.nauvisresearch.research.TechnologyLayout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The technology screen: a list on the left, one technology's neighbourhood in the middle, and
 * what it costs and hands over on the right.
 *
 * <h2>This file paints; it does not decide</h2>
 *
 * <p>Which technologies are in the picture and where they go is {@link TechnologyLayout}'s, and
 * that split is the whole reason this was worth doing in two pieces. Nothing in this repository
 * can look at a screen - a permanent hole rather than a gap in the suite - so the half that is
 * <em>true or false</em> lives in common code with {@code technology_layout_is_sound} on it, and
 * what is left here is the half that is <em>nice or ugly</em> and can only ever be judged by
 * somebody looking.
 *
 * <h2>Why there is a list, and why the graph is not the whole tree</h2>
 *
 * <p>Two attempts drew all of it and both were unreadable in the same place: a graph with a couple
 * of hub technologies produces bundles of arrows no ordering can separate. Factorio does not draw
 * the whole tree either - it draws what you are pointed at, what leads to it, and a little of what
 * it leads to. <b>The list and the search box are the navigation</b>, the graph is context, and
 * clicking anything in the graph moves the view onto it.
 *
 * <p>That makes the graph a place to look rather than a place to click, so <b>starting a research
 * is a button on the right</b>. Clicking a node used to start it, which was the same gesture as
 * "show me what this needs" and could only ever be one of the two.
 *
 * <p><b>The list is sorted into what it is coloured by</b> - what can be advanced now in
 * yellow-brown, then what cannot in red, then what is done in green at the bottom. That is
 * Factorio's, and the sort is what makes the colours worth having: the top of the list is the
 * answer to "what next", and everything below the first block is there to be read rather than
 * acted on.
 *
 * <h2>What a node says without being hovered</h2>
 *
 * <p>Colour is state, and the states are the questions a player actually has: done, being
 * researched now, startable, waiting on a craft, or not yet reachable. Under the icon are
 * <b>the science packs it costs</b>, at twelve pixels rather than as a line of text, because
 * "everything past here needs green" is a thing you read a tech tree for and cannot read one
 * tooltip at a time. A bar under that is units paid, drawn on <b>every</b> technology that has any
 * rather than only the current one, because progress survives a switch - see {@code ResearchState}.
 *
 * <p>A descendant carries <b>{@code +n} in its corner</b> when it needs technologies the picture
 * does not show, so nothing reads as "research this and you get that" when three other things are
 * wanted too.
 *
 * <p>Pan by dragging, zoom with the wheel over the graph; the wheel scrolls the list over the list.
 */
public class ResearchScreen extends Screen {

    private static final int NODE = 36;
    private static final int COLUMN_STEP = 100;
    private static final int ROW_STEP = 48;

    /** A science pack under the icon, how far apart they sit, and how many fit across a node. */
    private static final int PIP = 12;
    private static final int PIP_STEP = 11;
    private static final int PIPS = 3;

    private static final int PADDING = 6;
    private static final int MARGIN = 12;

    private static final int LIST_WIDTH = 118;
    private static final int DETAIL_WIDTH = 118;
    private static final int ROW_HEIGHT = 13;

    /** How far apart two arrows may turn, and how much of the gap stays clear at either end. */
    private static final int LANE_STEP = 10;
    private static final int LANE_MARGIN = 6;

    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 2.0f;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_CANVAS = 0xFF1A1A1A;
    private static final int COLOR_PANEL = 0xFF202020;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_EDGE = 0xFF5A5A5A;
    private static final int COLOR_EDGE_DONE = 0xFF6FDF8F;
    private static final int COLOR_EDGE_PATH = 0xFFFFD24A;

    /** Node fills, one per state. These are the five answers the picture gives at a glance. */
    private static final int COLOR_DONE = 0xFF2E5E3E;
    private static final int COLOR_CURRENT = 0xFF2A5A6A;
    private static final int COLOR_AVAILABLE = 0xFF4A4A4A;
    private static final int COLOR_TRIGGER = 0xFF3E5E46;
    private static final int COLOR_LOCKED = 0xFF262626;

    private static final int COLOR_BORDER_CURRENT = 0xFF6FC3DF;
    private static final int COLOR_BORDER_DONE = 0xFF6FDF8F;
    private static final int COLOR_BORDER_PATH = 0xFFFFD24A;
    private static final int COLOR_BORDER_SELECTED = 0xFFFFFFFF;

    /** Laid over a researched ancestor, which is context rather than work anybody has left. */
    private static final int COLOR_DIMMED = 0x66141414;
    private static final int COLOR_SELECTED_ROW = 0xFF3A4A5A;

    /**
     * The list's three colours, which are its three groups - see {@link #rank}.
     *
     * <p>Factorio's, and the reason they work is that they are not decoration: the list is sorted
     * into them, so the colour tells you which block of the list you are looking at and the block
     * tells you what to do about it.
     */
    private static final int COLOR_LIST_READY = 0xFFD8A33F;
    private static final int COLOR_LIST_LOCKED = 0xFFC05B5B;
    private static final int COLOR_LIST_DONE = 0xFF6FDF8F;

    private int left;
    private int top;
    private int paneWidth;
    private int paneHeight;
    private int headerHeight;

    private int graphLeft;
    private int graphWidth;

    private int scrollX;
    private int scrollY;
    private float zoom = 1.0f;
    private boolean dragging;

    private int listScroll;

    private @Nullable EditBox search;
    private boolean hideResearched;

    private @Nullable ResourceKey<Technology> selected;
    private TechnologyLayout.Layout layout = TechnologyLayout.Layout.EMPTY;

    /** What the list is showing, rebuilt only when the search text or the research state moves. */
    private List<Holder.Reference<Technology>> listed = List.of();
    private int listedRevision = -1;
    private String listedQuery = "";

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
        graphLeft = left + LIST_WIDTH;
        graphWidth = Math.max(NODE, paneWidth - LIST_WIDTH - DETAIL_WIDTH);

        // Kept across a resize, because the screen is rebuilt on one and a search box that emptied
        // itself when the window changed would be its own small bug.
        EditBox previous = search;
        search = new EditBox(font, left + 3, top + headerHeight + 3, LIST_WIDTH - 8, 12, previous,
                Component.translatable("screen.nauvis_research.research.search"));
        search.setHint(Component.translatable("screen.nauvis_research.research.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(search);

        if (selected == null) {
            selected = openingChoice();
        }
        listedRevision = -1;
        rebuild(true);
    }

    /**
     * What to look at when the screen opens.
     *
     * <p>Whatever is being researched, then whatever could be started, then the first technology
     * there is. Opening on nothing would make the one thing a player came to check a thing they
     * have to go and find.
     */
    private @Nullable ResourceKey<Technology> openingChoice() {
        ResourceKey<Technology> current = ClientResearch.current();
        if (current != null) {
            return current;
        }
        ResourceKey<Technology> first = null;
        for (Holder.Reference<Technology> holder : all()) {
            if (first == null) {
                first = holder.key();
            }
            if (!holder.value().isTriggered()
                    && ClientResearch.isAvailable(minecraft.level.registryAccess(), holder.key())) {
                return holder.key();
            }
        }
        return first;
    }

    private List<Holder.Reference<Technology>> all() {
        return ModTechnologies.all(minecraft.level.registryAccess());
    }

    /** Recomputes the picture. Cheap - it is one technology's neighbourhood, not the tree. */
    private void rebuild(boolean recentre) {
        layout = TechnologyLayout.around(all(), selected, ClientResearch.completed(),
                hideResearched);
        if (recentre) {
            TechnologyLayout.Placed focus = selected == null ? null : layout.at(selected);
            if (focus != null) {
                lookAt(focus);
            }
        }
        clampScroll();
    }

    private void select(@Nullable ResourceKey<Technology> key) {
        if (key == null || key.equals(selected)) {
            return;
        }
        selected = key;
        rebuild(true);
    }

    // -------------------------------------------------------------------------------- geometry

    private int contentTop() {
        return top + headerHeight;
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

    private static int nodeY(TechnologyLayout.Placed placed) {
        return placed.row() * ROW_STEP;
    }

    private int originX() {
        return graphLeft + PADDING - scrollX;
    }

    private int originY() {
        return contentTop() + PADDING - scrollY;
    }

    private void lookAt(TechnologyLayout.Placed placed) {
        scrollX = Math.round(nodeX(placed.column()) * zoom)
                - (graphWidth - Math.round(NODE * zoom)) / 2;
        scrollY = Math.round(nodeY(placed) * zoom) - (viewHeight() - Math.round(NODE * zoom)) / 2;
        clampScroll();
    }

    /**
     * Keeps the canvas on screen, and does nothing when it already fits.
     *
     * <p>The bounds are written as min/max of zero and the overflow so that a picture smaller than
     * its pane cannot be scrolled at all - otherwise a three-column view in a wide window would
     * drift off the left edge and look broken.
     */
    private void clampScroll() {
        int overflowX = Math.round(canvasWidth() * zoom) - graphWidth + PADDING * 2;
        int overflowY = Math.round(canvasHeight() * zoom) - viewHeight() + PADDING * 2;
        scrollX = Mth.clamp(scrollX, Math.min(0, overflowX), Math.max(0, overflowX));
        scrollY = Mth.clamp(scrollY, Math.min(0, overflowY), Math.max(0, overflowY));
    }

    // ------------------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left - 1, top - 1, left + paneWidth + 1, top + paneHeight + 1, COLOR_FRAME);
        graphics.fill(left, top, left + paneWidth, top + paneHeight, COLOR_BACKGROUND);
        graphics.text(font, heading(), left + PADDING, top + PADDING, COLOR_TEXT, false);

        TechnologyLayout.Placed hovered = renderGraph(graphics, mouseX, mouseY);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);

        // The widgets - the search box - go on top of the panels rather than under them, which is
        // why this is last and not first.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private @Nullable TechnologyLayout.Placed renderGraph(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY) {

        int canvasTop = contentTop();
        graphics.fill(graphLeft, canvasTop, graphLeft + graphWidth, top + paneHeight - 1,
                COLOR_CANVAS);

        TechnologyLayout.Placed hovered = nodeAt(mouseX, mouseY);
        Set<ResourceKey<Technology>> path = hovered == null ? Set.of() : pathTo(hovered.key());

        graphics.enableScissor(graphLeft, canvasTop, graphLeft + graphWidth, top + paneHeight - 1);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(), originY());
        graphics.pose().scale(zoom, zoom);

        // A line is one screen pixel however far out the view is zoomed. Below 1x a one-unit fill
        // inside the scaled transform rounds away to nothing, which is arrows silently vanishing.
        int thickness = Math.max(1, Mth.ceil(1.0f / zoom));

        // Edges first, so a node always sits on top of the wires rather than under them, and the
        // lit path after the rest, so it is not half buried under the wires it crosses.
        for (TechnologyLayout.Edge edge : layout.edges()) {
            if (!isOnPath(edge, path)) {
                renderEdge(graphics, edge, path, thickness);
            }
        }
        for (TechnologyLayout.Edge edge : layout.edges()) {
            if (isOnPath(edge, path)) {
                renderEdge(graphics, edge, path, thickness);
            }
        }
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            renderNode(graphics, placed, placed.equals(hovered), path);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
        return hovered;
    }

    /**
     * One prerequisite arrow, drawn as an elbow rather than a diagonal.
     *
     * <p>Right out of the parent, across, then right into the child - which is how vanilla's
     * advancement screen and Factorio's technology screen both do it, and not only for taste:
     * several diagonals converging on one node are impossible to tell apart, where elbows share
     * their horizontal runs and read as a bus.
     *
     * <p><b>Where the across happens is the lane</b> - see {@link TechnologyLayout}. Two arrows out
     * of one parent share their whole elbow, which is what a fan is; two out of different parents
     * share a channel only where their runs cannot overlap.
     */
    private void renderEdge(GuiGraphicsExtractor graphics, TechnologyLayout.Edge edge,
            Set<ResourceKey<Technology>> path, int thickness) {

        TechnologyLayout.Placed from = layout.at(edge.from());
        TechnologyLayout.Placed to = layout.at(edge.to());
        if (from == null || to == null) {
            return;
        }

        int colour = isOnPath(edge, path) ? COLOR_EDGE_PATH
                : ClientResearch.isCompleted(edge.from()) ? COLOR_EDGE_DONE
                : COLOR_EDGE;
        int x0 = nodeX(from.column()) + NODE;
        int y0 = nodeY(from) + NODE / 2;
        int x1 = nodeX(to.column());
        int y1 = nodeY(to) + NODE / 2;
        int mid = lane(x0, x1, edge.lane(), edge.lanes());

        graphics.fill(x0, y0, mid + thickness, y0 + thickness, colour);
        graphics.fill(mid, Math.min(y0, y1), mid + thickness, Math.max(y0, y1) + thickness, colour);
        graphics.fill(mid, y1, x1, y1 + thickness, colour);
    }

    /**
     * Where an arrow turns: the middle of the gap, shifted by its lane.
     *
     * <p>The spread is capped at {@link #LANE_STEP} so a gap with two lanes does not throw them to
     * opposite ends of it, and squeezed to fit when there are many.
     */
    private static int lane(int x0, int x1, int index, int count) {
        int centre = (x0 + x1) / 2;
        if (count <= 1) {
            return centre;
        }
        int room = COLUMN_STEP - NODE - LANE_MARGIN * 2;
        int step = Math.max(1, Math.min(LANE_STEP, room / (count - 1)));
        return centre + Math.round((index - (count - 1) / 2.0f) * step);
    }

    private void renderNode(GuiGraphicsExtractor graphics, TechnologyLayout.Placed placed,
            boolean hovered, Set<ResourceKey<Technology>> path) {

        ResourceKey<Technology> key = placed.key();
        Technology technology = placed.technology();
        int x = nodeX(placed.column());
        int y = nodeY(placed);

        boolean done = ClientResearch.isCompleted(key);
        boolean current = key.equals(ClientResearch.current());
        boolean available = ClientResearch.isAvailable(minecraft.level.registryAccess(), key);

        int fill = done ? COLOR_DONE
                : current ? COLOR_CURRENT
                : !available ? COLOR_LOCKED
                : technology.isTriggered() ? COLOR_TRIGGER
                : COLOR_AVAILABLE;

        int border = placed.kind() == TechnologyLayout.Kind.SELECTED ? COLOR_BORDER_SELECTED
                : hovered || path.contains(key) ? COLOR_BORDER_PATH
                : done ? COLOR_BORDER_DONE
                : current ? COLOR_BORDER_CURRENT
                : COLOR_FRAME;

        graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
        graphics.fill(x, y, x + NODE, y + NODE, fill);
        graphics.item(new ItemStack(iconOf(technology)), x + (NODE - 16) / 2, y + 3);
        renderPacks(graphics, technology, x, y);

        float progress = progressOf(key, technology);
        if (progress > 0.0f) {
            int filled = Math.round((NODE - 2) * Math.clamp(progress, 0.0f, 1.0f));
            graphics.fill(x + 1, y + NODE - 3, x + NODE - 1, y + NODE - 1, COLOR_FRAME);
            graphics.fill(x + 1, y + NODE - 3, x + 1 + filled, y + NODE - 1, COLOR_BORDER_CURRENT);
        }

        // What the picture does not show. Without it a descendant reads as "research the selected
        // one and this is yours", which is wrong whenever it wants anything else as well.
        if (placed.outside() > 0) {
            String badge = "+" + placed.outside();
            int width = font.width(badge);
            graphics.fill(x + NODE - width - 3, y - 1, x + NODE + 1, y + font.lineHeight,
                    COLOR_FRAME);
            graphics.text(font, badge, x + NODE - width - 1, y, COLOR_TEXT, false);
        }

        // A researched ancestor is context. It is drawn, because a chain with holes in it is not a
        // chain, and dimmed, because it is not work anybody has left to do.
        if (done && placed.kind() == TechnologyLayout.Kind.ANCESTOR) {
            graphics.fill(x, y, x + NODE, y + NODE, COLOR_DIMMED);
        }
    }

    /**
     * The science packs this technology costs, under its icon.
     *
     * <p>Twelve pixels, on the node, because "everything past here needs green science" is a thing
     * you read a tech tree for and a thing you cannot read one tooltip at a time.
     */
    private void renderPacks(GuiGraphicsExtractor graphics, Technology technology, int x, int y) {
        int at = x + 2;
        int drawn = 0;
        for (Identifier pack : technology.packs()) {
            if (drawn >= PIPS) {
                return;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(pack).orElse(null);
            if (item == null) {
                continue;
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(at, y + 20);
            graphics.pose().scale(PIP / 16.0f, PIP / 16.0f);
            graphics.item(new ItemStack(item), 0, 0);
            graphics.pose().popMatrix();
            at += PIP_STEP;
            drawn++;
        }
    }

    /** How far along this technology is - units paid, or a trigger's tally. */
    private float progressOf(ResourceKey<Technology> key, Technology technology) {
        if (ClientResearch.isCompleted(key)) {
            return 0.0f;
        }
        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            return Math.min(ClientResearch.tally(trigger), trigger.count())
                    / (float) trigger.count();
        }
        if (technology.units() > 0) {
            return ClientResearch.units(key) / (float) technology.units();
        }
        return 0.0f;
    }

    // ----------------------------------------------------------------------------------- list

    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelTop = contentTop();
        graphics.fill(left, panelTop, left + LIST_WIDTH - 1, top + paneHeight - 1, COLOR_PANEL);

        int toggleTop = panelTop + 18;
        boolean overToggle = mouseX >= left + 3 && mouseX < left + LIST_WIDTH - 4
                && mouseY >= toggleTop && mouseY < toggleTop + 11;
        graphics.fill(left + 3, toggleTop + 1, left + 12, toggleTop + 10, COLOR_FRAME);
        if (hideResearched) {
            graphics.fill(left + 5, toggleTop + 3, left + 10, toggleTop + 8, COLOR_BORDER_DONE);
        }
        graphics.text(font, Component.translatable("screen.nauvis_research.research.hide_done"),
                left + 15, toggleTop + 2, overToggle ? COLOR_TEXT : COLOR_MUTED, false);

        refreshList();
        int rowsTop = panelTop + 32;
        int rowsBottom = top + paneHeight - 2;
        graphics.enableScissor(left, rowsTop, left + LIST_WIDTH - 1, rowsBottom);
        int y = rowsTop - listScroll;
        for (Holder.Reference<Technology> holder : listed) {
            if (y + ROW_HEIGHT > rowsTop && y < rowsBottom) {
                renderRow(graphics, holder, y, mouseX, mouseY, rowsTop, rowsBottom);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderRow(GuiGraphicsExtractor graphics, Holder.Reference<Technology> holder,
            int y, int mouseX, int mouseY, int rowsTop, int rowsBottom) {

        boolean over = mouseX >= left && mouseX < left + LIST_WIDTH - 1
                && mouseY >= Math.max(y, rowsTop) && mouseY < Math.min(y + ROW_HEIGHT, rowsBottom);
        if (holder.key().equals(selected)) {
            graphics.fill(left, y, left + LIST_WIDTH - 1, y + ROW_HEIGHT, COLOR_SELECTED_ROW);
        } else if (over) {
            graphics.fill(left, y, left + LIST_WIDTH - 1, y + ROW_HEIGHT, COLOR_AVAILABLE);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(left + 2, y + 1);
        graphics.pose().scale(0.625f, 0.625f);
        graphics.item(new ItemStack(iconOf(holder.value())), 0, 0);
        graphics.pose().popMatrix();

        String name = holder.value().title(holder.key()).getString();
        graphics.text(font, font.plainSubstrByWidth(name, LIST_WIDTH - 18),
                left + 14, y + 3, colourOf(holder), false);
    }

    /**
     * Which block of the list a technology belongs in: what can be advanced now, then what cannot,
     * then what is done.
     *
     * <p>Factorio's order, and it is the order because it is a list of what to do next rather than
     * an index of everything there is. A researched technology is still listed - you go back to
     * them to read what they gave you - but it is listed last, because it is not work.
     *
     * <p>A triggered technology counts as ready: <em>craft fifty iron plates</em> is a thing to go
     * and do, even though no lab does it, and burying it with what cannot be reached at all would
     * hide the whole of the early game.
     *
     * <p>The one being researched sorts above the rest of its block and keeps its own colour. That
     * is a fourth thing on a list of three, and it earns it: "which one am I on" is the question
     * the screen is opened for most often.
     */
    private int rank(Holder.Reference<Technology> holder) {
        if (ClientResearch.isCompleted(holder.key())) {
            return 3;
        }
        if (holder.key().equals(ClientResearch.current())) {
            return 0;
        }
        return ready(holder) ? 1 : 2;
    }

    /** Whether anything can be done about this technology today. */
    private boolean ready(Holder.Reference<Technology> holder) {
        return holder.value().isResearchable()
                && ClientResearch.isAvailable(minecraft.level.registryAccess(), holder.key());
    }

    private int colourOf(Holder.Reference<Technology> holder) {
        return switch (rank(holder)) {
            case 0 -> COLOR_BORDER_CURRENT;
            case 1 -> COLOR_LIST_READY;
            case 2 -> COLOR_LIST_LOCKED;
            default -> COLOR_LIST_DONE;
        };
    }

    /**
     * The list, filtered by the search box.
     *
     * <p>Rebuilt only when the text or the research state moves - it is asked for every frame, and
     * matching two hundred technologies against every item they unlock is not free.
     */
    private void refreshList() {
        String query = query();
        if (query.equals(listedQuery) && listedRevision == ClientResearch.revision()) {
            return;
        }
        listedQuery = query;
        listedRevision = ClientResearch.revision();
        List<Holder.Reference<Technology>> found = new ArrayList<>();
        for (Holder.Reference<Technology> holder : all()) {
            if (query.isEmpty() || matches(holder, query)) {
                found.add(holder);
            }
        }
        // Stable, so the tree's own order survives inside each block - which is what keeps two
        // technologies that are equally ready in the order Factorio names them.
        found.sort(java.util.Comparator.comparingInt(this::rank));
        listed = List.copyOf(found);
        listScroll = Mth.clamp(listScroll, 0, listOverflow());
    }

    private int listOverflow() {
        return Math.max(0, listed.size() * ROW_HEIGHT - (paneHeight - headerHeight - 34));
    }

    /**
     * What the search matches: the name, the id, and <b>everything the technology unlocks</b>.
     *
     * <p>The last of those is the one that matters. Somebody looking for the assembling machine is
     * not looking for "Automation 2"; they are looking for the machine, and the technology's name
     * is a fact about Factorio they may never have learned. Players think in items.
     */
    private boolean matches(Holder.Reference<Technology> holder, String query) {
        String path = holder.key().identifier().getPath();
        if (path.contains(query) || path.replace('_', ' ').contains(query)) {
            return true;
        }
        if (holder.value().title(holder.key()).getString().toLowerCase(Locale.ROOT)
                .contains(query)) {
            return true;
        }
        for (ResourceKey<Recipe<?>> unlock : holder.value().unlocks()) {
            String unlocked = unlock.identifier().getPath();
            if (unlocked.contains(query) || unlocked.replace('_', ' ').contains(query)) {
                return true;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(unlock.identifier()).orElse(null);
            if (item != null && new ItemStack(item).getHoverName().getString()
                    .toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private String query() {
        return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
    }

    // --------------------------------------------------------------------------------- detail

    /** What the selected technology costs and hands over, and the button that starts it. */
    private void renderDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int panelLeft = left + paneWidth - DETAIL_WIDTH + 1;
        graphics.fill(panelLeft, contentTop(), left + paneWidth, top + paneHeight - 1, COLOR_PANEL);

        Technology technology = selected == null ? null : technologyOf(selected);
        if (technology == null) {
            return;
        }

        int y = contentTop() + 4;
        int wrap = DETAIL_WIDTH - 8;
        y = write(graphics, technology.title(selected), panelLeft + 4, y, wrap, COLOR_TEXT) + 3;
        y = write(graphics, state(selected, technology), panelLeft + 4, y, wrap, COLOR_TEXT) + 3;

        if (!technology.isTriggered()) {
            int at = panelLeft + 4;
            for (Identifier pack : technology.packs()) {
                Item item = BuiltInRegistries.ITEM.getOptional(pack).orElse(null);
                if (item != null) {
                    graphics.item(new ItemStack(item), at, y);
                    at += 18;
                }
            }
            if (at > panelLeft + 4) {
                y += 20;
            }
        }

        List<Component> unlocks = unlocksOf(technology);
        y = write(graphics, unlocks.isEmpty()
                        ? Component.translatable("screen.nauvis_research.research.no_effect")
                                .withStyle(ChatFormatting.YELLOW)
                        : Component.translatable("screen.nauvis_research.research.unlocks")
                                .withStyle(ChatFormatting.GRAY),
                panelLeft + 4, y, wrap, COLOR_MUTED);
        for (Component unlock : unlocks) {
            if (y > top + paneHeight - 30) {
                break;
            }
            graphics.text(font, font.plainSubstrByWidth(unlock.getString(), wrap - 2),
                    panelLeft + 6, y, COLOR_TEXT, false);
            y += font.lineHeight;
        }

        renderStartButton(graphics, technology, mouseX, mouseY);
    }

    /** Draws a component wrapped to a width, and answers where the next line starts. */
    private int write(GuiGraphicsExtractor graphics, Component text, int x, int y, int wrap,
            int colour) {
        for (FormattedCharSequence line : font.split(text, wrap)) {
            graphics.text(font, line, x, y, colour, false);
            y += font.lineHeight;
        }
        return y;
    }

    private void renderStartButton(GuiGraphicsExtractor graphics, Technology technology,
            int mouseX, int mouseY) {

        int[] box = startButton();
        boolean over = mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
        boolean stops = selected.equals(ClientResearch.current());
        boolean can = stops || canStart(technology);

        graphics.fill(box[0], box[1], box[2], box[3], COLOR_FRAME);
        graphics.fill(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1,
                !can ? COLOR_LOCKED : over ? COLOR_AVAILABLE : COLOR_PANEL);
        graphics.centeredText(font, stops
                        ? Component.translatable("screen.nauvis_research.research.stop")
                        : Component.translatable("screen.nauvis_research.research.start"),
                (box[0] + box[2]) / 2, box[1] + 5, can ? COLOR_TEXT : COLOR_MUTED);
    }

    private int[] startButton() {
        int panelLeft = left + paneWidth - DETAIL_WIDTH + 1;
        int bottom = top + paneHeight - 4;
        return new int[] {panelLeft + 4, bottom - 16, left + paneWidth - 4, bottom};
    }

    private boolean canStart(Technology technology) {
        return !technology.isTriggered()
                && technology.isResearchable()
                && !ClientResearch.isCompleted(selected)
                && ClientResearch.isAvailable(minecraft.level.registryAccess(), selected);
    }

    private Component state(ResourceKey<Technology> key, Technology technology) {
        if (ClientResearch.isCompleted(key)) {
            return Component.translatable("screen.nauvis_research.research.done")
                    .withStyle(ChatFormatting.GREEN);
        }
        if (!ClientResearch.isAvailable(minecraft.level.registryAccess(), key)) {
            return waitingFor(technology);
        }
        return cost(technology);
    }

    private List<Component> unlocksOf(Technology technology) {
        List<Component> unlocks = new ArrayList<>();
        for (ResourceKey<Recipe<?>> recipe : technology.unlocks()) {
            BuiltInRegistries.ITEM.getOptional(recipe.identifier())
                    .ifPresent(item -> unlocks.add(new ItemStack(item).getHoverName()));
        }
        return unlocks;
    }

    // -------------------------------------------------------------------------- path and text

    /** A technology and everything it needs, transitively - what the hover lights up. */
    private Set<ResourceKey<Technology>> pathTo(ResourceKey<Technology> key) {
        Set<ResourceKey<Technology>> found = new HashSet<>(Set.of(key));
        Deque<ResourceKey<Technology>> frontier = new ArrayDeque<>(List.of(key));
        while (!frontier.isEmpty()) {
            Technology technology = technologyOf(frontier.removeFirst());
            if (technology == null) {
                continue;
            }
            for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
                if (found.add(prerequisite)) {
                    frontier.add(prerequisite);
                }
            }
        }
        return found;
    }

    private static boolean isOnPath(TechnologyLayout.Edge edge, Set<ResourceKey<Technology>> path) {
        return path.contains(edge.from()) && path.contains(edge.to());
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

    private List<Component> tooltip(TechnologyLayout.Placed placed) {
        Technology technology = placed.technology();
        List<Component> lines = new ArrayList<>();
        lines.add(technology.title(placed.key()));
        lines.add(state(placed.key(), technology));

        int paid = ClientResearch.units(placed.key());
        if (paid > 0 && !placed.key().equals(ClientResearch.current())) {
            lines.add(Component.translatable("screen.nauvis_research.research.part_done",
                    paid, technology.units()).withStyle(ChatFormatting.AQUA));
        }
        if (placed.outside() > 0) {
            lines.add(Component.translatable("screen.nauvis_research.research.also_needs",
                    placed.outside()).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** What a technology that cannot be started yet is waiting for - its unmet prerequisites. */
    private Component waitingFor(Technology technology) {
        Component needs = Component.empty();
        boolean first = true;
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
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

    /**
     * What a trigger names, as the player knows it: an item's name for a craft, a block's for a
     * mine, and the bare id if neither is registered here - which most of the tree's are not yet.
     */
    private static Component targetName(Technology.Trigger trigger) {
        return switch (trigger.kind()) {
            case CRAFT -> BuiltInRegistries.ITEM.getOptional(trigger.target())
                    .map(item -> new ItemStack(item).getHoverName())
                    .orElse(Component.literal(trigger.target().toString()));
            case MINE -> BuiltInRegistries.BLOCK.getOptional(trigger.target())
                    .map(block -> (Component) block.getName())
                    .orElse(Component.literal(trigger.target().toString()));
        };
    }

    /** The cost, without the packs - they are drawn as items underneath rather than named. */
    private Component cost(Technology technology) {
        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            return Component.translatable(switch (trigger.kind()) {
                        case CRAFT -> "screen.nauvis_research.research.trigger";
                        case MINE -> "screen.nauvis_research.research.trigger.mine";
                    },
                    trigger.count(),
                    targetName(trigger),
                    Math.min(ClientResearch.tally(trigger), trigger.count()))
                    .withStyle(ChatFormatting.AQUA);
        }
        if (!technology.isResearchable()) {
            return Component.translatable("screen.nauvis_research.research.unavailable_packs")
                    .withStyle(ChatFormatting.DARK_RED);
        }
        return Component.translatable("screen.nauvis_research.research.cost_units",
                technology.units(), technology.ticksPerUnit() / 20.0f)
                .withStyle(ChatFormatting.AQUA);
    }

    private @Nullable Technology technologyOf(ResourceKey<Technology> key) {
        return ModTechnologies.registry(minecraft.level.registryAccess()).get(key)
                .map(Holder.Reference::value).orElse(null);
    }

    // --------------------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (mouseY < contentTop()) {
            return super.mouseClicked(event, doubleClick);
        }

        int[] button = startButton();
        if (mouseX >= button[0] && mouseX < button[2] && mouseY >= button[1] && mouseY < button[3]) {
            setFocused(null);
            startOrStop();
            return true;
        }

        if (mouseX < left + LIST_WIDTH) {
            return clickedList(event, doubleClick, mouseY);
        }
        setFocused(null);

        if (mouseX < graphLeft + graphWidth) {
            TechnologyLayout.Placed hit = nodeAt(mouseX, mouseY);
            if (hit != null) {
                // Re-centre rather than start. The graph is where you look; the button on the
                // right is where you commit.
                select(hit.key());
            } else {
                // Empty canvas: this press begins a pan. Swallowed either way, so a stray click on
                // the background never falls through to whatever is behind the screen.
                dragging = true;
            }
        }
        return true;
    }

    private boolean clickedList(MouseButtonEvent event, boolean doubleClick, double mouseY) {
        int toggleTop = contentTop() + 18;
        if (mouseY >= toggleTop && mouseY < toggleTop + 11) {
            setFocused(null);
            hideResearched = !hideResearched;
            rebuild(false);
            return true;
        }
        if (mouseY < toggleTop) {
            // The search box's row, which is a widget and answers for itself.
            return super.mouseClicked(event, doubleClick);
        }
        setFocused(null);
        int index = (int) ((mouseY - (contentTop() + 32) + listScroll) / ROW_HEIGHT);
        if (index >= 0 && index < listed.size()) {
            select(listed.get(index).key());
        }
        return true;
    }

    private void startOrStop() {
        if (selected == null) {
            return;
        }
        Technology technology = technologyOf(selected);
        if (technology == null) {
            return;
        }
        boolean stops = selected.equals(ClientResearch.current());
        if (!stops && !canStart(technology)) {
            return;
        }
        ClientPacketDistributor.sendToServer(new StartResearchPayload(
                stops ? Optional.empty() : Optional.of(selected)));
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

    /**
     * The wheel scrolls the list over the list, and zooms about the cursor over the graph.
     *
     * <p>Zooming about the cursor is one line and it is the only line that matters: whatever the
     * cursor was over stays under the cursor.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (mouseX < left + LIST_WIDTH) {
            listScroll = Mth.clamp(listScroll - (int) (scrollDeltaY * ROW_HEIGHT * 2),
                    0, listOverflow());
            return true;
        }
        float wanted = Mth.clamp(zoom * (float) Math.pow(1.15, scrollDeltaY), MIN_ZOOM, MAX_ZOOM);
        if (wanted != zoom) {
            double canvasX = (mouseX - originX()) / zoom;
            double canvasY = (mouseY - originY()) / zoom;
            zoom = wanted;
            scrollX = (int) Math.round(graphLeft + PADDING - mouseX + canvasX * zoom);
            scrollY = (int) Math.round(contentTop() + PADDING - mouseY + canvasY * zoom);
            clampScroll();
        }
        return true;
    }

    private @Nullable TechnologyLayout.Placed nodeAt(double mouseX, double mouseY) {
        if (mouseY < contentTop() || mouseY >= top + paneHeight
                || mouseX < graphLeft || mouseX >= graphLeft + graphWidth) {
            return null;
        }
        double canvasX = (mouseX - originX()) / zoom;
        double canvasY = (mouseY - originY()) / zoom;
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            int x = nodeX(placed.column());
            int y = nodeY(placed);
            if (canvasX >= x && canvasX < x + NODE && canvasY >= y && canvasY < y + NODE) {
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
