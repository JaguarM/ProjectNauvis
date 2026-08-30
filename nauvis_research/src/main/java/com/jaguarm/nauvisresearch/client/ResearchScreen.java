package com.jaguarm.nauvisresearch.client;

import java.util.ArrayList;
import java.util.ArrayDeque;
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
 * <p>So this file has no idea what a prerequisite is. It is handed cells, edges and lanes and
 * turns them into pixels.
 *
 * <h2>What a node says without being clicked</h2>
 *
 * <p>Colour is state, and the states are the questions a player actually has: done, being
 * researched now, startable, waiting on a craft, or not yet reachable. <b>A locked node is drawn
 * rather than hidden</b>, dimmed and still hoverable, because a tree is mostly a thing you read
 * ahead in - hiding what you cannot do yet turns a map into a torch beam. That was exactly the bug
 * in the list this replaces, and it would be no better in a nicer font.
 *
 * <p>A bar under a node is units paid towards it, and it is drawn on <b>every</b> technology that
 * has any rather than only on the current one, because progress survives a switch - see
 * {@code ResearchState}. A player who moved the labs somewhere else can see what they left behind
 * without hunting for it.
 *
 * <h2>Reading the wires</h2>
 *
 * <p>Three things are here for the same reason, which is that the eye loses an arrow long before
 * the layout does:
 *
 * <ul>
 *   <li><b>lanes.</b> Every elbow used to turn down the middle of the gap between two columns, so
 *       every arrow leaving a column drew itself on top of every other one and eleven children
 *       hanging off five parents came out as a single vertical bar. {@code Edge.lane} says which
 *       channel of that gap an arrow belongs in, and arrows share one exactly when they share a
 *       parent;</li>
 *   <li><b>the hovered path.</b> Point at a node and everything it needs, all the way back to a
 *       root, lights up - which is the question "what do I have to do first" answered in one
 *       gesture rather than by following a grey line with a finger;</li>
 *   <li><b>search.</b> Type and the tree dims to what matches, and jumps to the first of them.
 *       Twenty-eight technologies fit on a screen; two hundred will not.</li>
 * </ul>
 *
 * <p>Pan by dragging, zoom with the wheel - Factorio's two, and there is nothing else to learn.
 * Columns are centred against the tallest one rather than hanging from the top, so a column of
 * three beside a column of eleven reads as a fan rather than as a list that ran out.
 */
public class ResearchScreen extends Screen {

    /** A node is vanilla's advancement frame size, because that is what the eye is trained on. */
    private static final int NODE = 26;
    private static final int COLUMN_STEP = 76;
    private static final int ROW_STEP = 34;

    private static final int PADDING = 6;
    private static final int MARGIN = 16;

    /** How far apart two arrows may turn, and how much of the gap stays clear at either end. */
    private static final int LANE_STEP = 8;
    private static final int LANE_MARGIN = 5;

    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 2.0f;

    private static final int SEARCH_WIDTH = 96;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xF0141414;
    private static final int COLOR_CANVAS = 0xFF1A1A1A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_EDGE = 0xFF4A4A4A;
    private static final int COLOR_EDGE_DONE = 0xFF6FDF8F;
    private static final int COLOR_EDGE_PATH = 0xFFFFD24A;

    /** Node fills, one per state. These are the five answers the tree gives at a glance. */
    private static final int COLOR_DONE = 0xFF2E5E3E;
    private static final int COLOR_CURRENT = 0xFF2A5A6A;
    private static final int COLOR_AVAILABLE = 0xFF4A4A4A;
    private static final int COLOR_TRIGGER = 0xFF3E5E46;
    private static final int COLOR_LOCKED = 0xFF262626;

    private static final int COLOR_BORDER_CURRENT = 0xFF6FC3DF;
    private static final int COLOR_BORDER_DONE = 0xFF6FDF8F;
    private static final int COLOR_BORDER_PATH = 0xFFFFD24A;

    /** Laid over everything the search does not match, rather than hiding it. */
    private static final int COLOR_DIMMED = 0xC01A1A1A;

    private int left;
    private int top;
    private int paneWidth;
    private int paneHeight;
    private int headerHeight;

    private int scrollX;
    private int scrollY;
    private float zoom = 1.0f;
    private boolean dragging;

    private @Nullable EditBox search;

    private TechnologyLayout.Layout layout = new TechnologyLayout.Layout(List.of(), List.of(), 0, 0);

    /**
     * How far down each column is pushed to centre it against the tallest one, indexed by column.
     *
     * <p>Cached rather than counted per node per frame, and rebuilt in {@link #init} with the
     * layout it belongs to.
     */
    private int[] columnOffset = new int[0];

    public ResearchScreen() {
        super(Component.translatable("screen.nauvis_research.research"));
    }

    @Override
    protected void init() {
        super.init();
        headerHeight = Math.max(font.lineHeight, 12) + PADDING * 2;
        left = MARGIN;
        top = MARGIN;
        paneWidth = width - MARGIN * 2;
        paneHeight = height - MARGIN * 2;

        layout = TechnologyLayout.of(minecraft.level.registryAccess());
        columnOffset = centreColumns(layout);

        // Kept across a resize, because the screen is rebuilt on one and a search box that emptied
        // itself when the window changed would be its own small bug.
        EditBox previous = search;
        search = new EditBox(font, left + paneWidth - SEARCH_WIDTH - PADDING, top + PADDING,
                SEARCH_WIDTH, 12, previous,
                Component.translatable("screen.nauvis_research.research.search"));
        search.setHint(Component.translatable("screen.nauvis_research.research.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> lookAtFirstMatch());
        addRenderableWidget(search);

        // Open looking at whatever is being researched. A tree that always opened at the origin
        // would make the one thing you came to check something you have to go and find.
        ResourceKey<Technology> current = ClientResearch.current();
        TechnologyLayout.Placed focus = current == null ? null : layout.at(current);
        if (focus != null) {
            lookAt(focus);
        }
        clampScroll();
    }

    /**
     * The vertical offset of each column, so a short column sits level with a tall one's middle.
     *
     * <p>Purely a drawing decision, which is why it is here and not in the layout: the grid says
     * row three of column one, and what that is worth in pixels is this file's business.
     */
    private static int[] centreColumns(TechnologyLayout.Layout layout) {
        int[] rows = new int[Math.max(layout.columns(), 1)];
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            if (placed.column() < rows.length) {
                rows[placed.column()] = Math.max(rows[placed.column()], placed.row() + 1);
            }
        }
        int[] offsets = new int[rows.length];
        for (int i = 0; i < rows.length; i++) {
            offsets[i] = (layout.rows() - rows[i]) * ROW_STEP / 2;
        }
        return offsets;
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

    private int nodeY(TechnologyLayout.Placed placed) {
        int offset = placed.column() < columnOffset.length ? columnOffset[placed.column()] : 0;
        return offset + placed.row() * ROW_STEP;
    }

    /** Canvas coordinate zero, in screen pixels. Everything drawn hangs off these two. */
    private int originX() {
        return left + PADDING - scrollX;
    }

    private int originY() {
        return top + headerHeight + PADDING - scrollY;
    }

    /** Puts a node in the middle of the pane. Used on opening, and by search. */
    private void lookAt(TechnologyLayout.Placed placed) {
        scrollX = Math.round(nodeX(placed.column()) * zoom) - (paneWidth - Math.round(NODE * zoom)) / 2;
        scrollY = Math.round(nodeY(placed) * zoom) - (viewHeight() - Math.round(NODE * zoom)) / 2;
        clampScroll();
    }

    /**
     * Keeps the canvas on screen, and does nothing when it already fits.
     *
     * <p>The bounds are written as min/max of zero and the overflow so that a tree smaller than
     * its pane cannot be scrolled at all - otherwise a six-column tree in a wide window would
     * drift off the left edge and look broken.
     */
    private void clampScroll() {
        int overflowX = Math.round(canvasWidth() * zoom) - paneWidth + PADDING * 2;
        int overflowY = Math.round(canvasHeight() * zoom) - viewHeight() + PADDING * 2;
        scrollX = Mth.clamp(scrollX, Math.min(0, overflowX), Math.max(0, overflowX));
        scrollY = Mth.clamp(scrollY, Math.min(0, overflowY), Math.max(0, overflowY));
    }

    // ------------------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left - 1, top - 1, left + paneWidth + 1, top + paneHeight + 1, COLOR_FRAME);
        graphics.fill(left, top, left + paneWidth, top + paneHeight, COLOR_BACKGROUND);
        graphics.text(font, heading(), left + PADDING, top + PADDING + 2, COLOR_TEXT, false);

        int canvasTop = top + headerHeight;
        graphics.fill(left + 1, canvasTop, left + paneWidth - 1, top + paneHeight - 1, COLOR_CANVAS);

        TechnologyLayout.Placed hovered = nodeAt(mouseX, mouseY);
        Set<ResourceKey<Technology>> path = hovered == null ? Set.of() : pathTo(hovered);
        String query = query();

        // Everything below is in canvas coordinates and clipped to the pane, so a node at the edge
        // is cut off rather than drawn over the header. The scissor is set before the zoom because
        // it is in screen pixels either way.
        graphics.enableScissor(left + 1, canvasTop, left + paneWidth - 1, top + paneHeight - 1);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(), originY());
        graphics.pose().scale(zoom, zoom);

        // Edges first, so a node always sits on top of the wires rather than under them, and the
        // lit path after the rest of them, so it is not half buried under the wires it crosses.
        for (TechnologyLayout.Edge edge : layout.edges()) {
            if (!isOnPath(edge, path)) {
                renderEdge(graphics, edge, path);
            }
        }
        for (TechnologyLayout.Edge edge : layout.edges()) {
            if (isOnPath(edge, path)) {
                renderEdge(graphics, edge, path);
            }
        }

        for (TechnologyLayout.Placed placed : layout.nodes()) {
            renderNode(graphics, placed, placed.equals(hovered), path, query);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // The widgets - the search box - go on top of the pane rather than under it, which is why
        // this is last and not first.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

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
     *
     * <p><b>Where the across happens is the lane</b>, and it is the difference between a fan and a
     * single bar - see {@link TechnologyLayout}. Two arrows out of one parent share their whole
     * elbow, which is what a fan is; two out of different parents never touch.
     */
    private void renderEdge(GuiGraphicsExtractor graphics, TechnologyLayout.Edge edge,
            Set<ResourceKey<Technology>> path) {

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

        graphics.fill(x0, y0, mid + 1, y0 + 1, colour);
        graphics.fill(mid, Math.min(y0, y1), mid + 1, Math.max(y0, y1) + 1, colour);
        graphics.fill(mid, y1, x1, y1 + 1, colour);
    }

    /**
     * Where an arrow turns: the middle of the gap, shifted by its lane.
     *
     * <p>The spread is capped at {@link #LANE_STEP} so a column with two parents does not throw
     * its two arrows to opposite ends of the gap, and squeezed to fit when there are many - a
     * column of eleven parents gets four pixels each, which is still eleven distinguishable lines
     * where one channel is one.
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
            boolean hovered, Set<ResourceKey<Technology>> path, String query) {

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

        int border = hovered || path.contains(key) ? COLOR_BORDER_PATH
                : done ? COLOR_BORDER_DONE
                : current ? COLOR_BORDER_CURRENT
                : COLOR_FRAME;

        graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
        graphics.fill(x, y, x + NODE, y + NODE, fill);
        graphics.item(new ItemStack(iconOf(technology)), x + (NODE - 16) / 2, y + (NODE - 16) / 2);

        // A bar under whatever has been paid for, so progress is visible without hovering - which
        // for the triggered technologies is the whole of the early game, and for a research that
        // was switched away from is the only place it is said at all.
        float progress = progressOf(key, technology);
        if (progress > 0.0f) {
            int filled = Math.round((NODE - 2) * Math.clamp(progress, 0.0f, 1.0f));
            graphics.fill(x + 1, y + NODE - 3, x + NODE - 1, y + NODE - 1, COLOR_FRAME);
            graphics.fill(x + 1, y + NODE - 3, x + 1 + filled, y + NODE - 1, COLOR_BORDER_CURRENT);
        }

        // Searching dims rather than hides, for the same reason a locked node is drawn: a tree you
        // cannot see the shape of is no longer a tree.
        if (!query.isEmpty() && !matches(placed, query)) {
            graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, COLOR_DIMMED);
        }
    }

    /** How far along this technology is - units paid, or a trigger's tally. */
    private float progressOf(ResourceKey<Technology> key, Technology technology) {
        if (ClientResearch.isCompleted(key)) {
            return 0.0f;
        }
        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            return Math.min(ClientResearch.made(trigger.item()), trigger.count())
                    / (float) trigger.count();
        }
        if (technology.units() > 0) {
            return ClientResearch.units(key) / (float) technology.units();
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

    // -------------------------------------------------------------------------- path and search

    /** A technology and everything it needs, transitively - what the hover lights up. */
    private Set<ResourceKey<Technology>> pathTo(TechnologyLayout.Placed placed) {
        Set<ResourceKey<Technology>> found = new HashSet<>();
        Deque<ResourceKey<Technology>> frontier = new ArrayDeque<>();
        found.add(placed.key());
        frontier.add(placed.key());
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

    private String query() {
        return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The id as well as the name, and underscores either way round.
     *
     * <p>"steel processing", "steel_processing" and "steel" all find the same node, which matters
     * because half of what a player remembers is the Factorio id and the other half is the English
     * the screen shows them.
     */
    private boolean matches(TechnologyLayout.Placed placed, String query) {
        String path = placed.key().identifier().getPath();
        if (path.contains(query) || path.replace('_', ' ').contains(query)) {
            return true;
        }
        return placed.technology().title(placed.key()).getString()
                .toLowerCase(Locale.ROOT).contains(query);
    }

    /** Types a letter, and the tree goes to what you meant rather than waiting to be dragged. */
    private void lookAtFirstMatch() {
        String query = query();
        if (query.isEmpty()) {
            return;
        }
        for (TechnologyLayout.Placed placed : layout.nodes()) {
            if (matches(placed, query)) {
                lookAt(placed);
                return;
            }
        }
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

        // Units already paid for something the labs are not pointed at. Said out loud because the
        // whole point of keeping it is that a player can rely on it being there.
        int paid = ClientResearch.units(placed.key());
        if (paid > 0 && !placed.key().equals(ClientResearch.current())) {
            lines.add(Component.translatable("screen.nauvis_research.research.part_done",
                    paid, technology.units()).withStyle(ChatFormatting.AQUA));
        }

        // What it hands over. This is the reason to research it, and the one thing a graph of
        // names cannot tell you.
        List<Component> unlocks = new ArrayList<>();
        for (ResourceKey<Recipe<?>> recipe : technology.unlocks()) {
            BuiltInRegistries.ITEM.getOptional(recipe.identifier())
                    .ifPresent(item -> unlocks.add(new ItemStack(item).getHoverName()));
        }
        if (unlocks.isEmpty()) {
            // The one real trap in the tree, said out loud. A hundred and twenty-eight of the two
            // hundred and sixteen technologies unlock nothing here - their Factorio effects are
            // mechanics this pack has not built - and they are kept because a technology's cost
            // and its place in the graph are identity and go into world saves. Four of them are in
            // the tree today. Nothing else on the node distinguishes them from a technology worth
            // a hundred science packs, so a player pays for one and looks for what changed.
            //
            // Empty is the whole test, and it is the honest one: `unlocks` is what this pack could
            // find an item for, so a technology whose recipes exist in Factorio and not here reads
            // the same as one with no recipe effects at all - which, to the player, it is.
            lines.add(Component.empty());
            lines.add(Component.translatable("screen.nauvis_research.research.no_effect")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
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
        // The header is the search box's, and nothing else is a widget, so this is the whole of
        // the split: above the canvas the screen behaves like an ordinary one.
        if (event.y() < top + headerHeight) {
            return super.mouseClicked(event, doubleClick);
        }
        setFocused(null);

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

        // Clicking the current research again stops it, which is the only way to stop. Whatever it
        // had paid stays paid - see `ResearchState` - so this is no longer a thing to be careful
        // about.
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

    /**
     * The wheel zooms, about the cursor, which is Factorio's tech tree and every map ever drawn.
     *
     * <p>The arithmetic is one line and it is the only line that matters: whatever the cursor was
     * over stays under the cursor. A zoom that pulled towards the middle of the pane makes reading
     * a corner of a wide tree a matter of zoom, drag, zoom, drag.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        float wanted = Mth.clamp(zoom * (float) Math.pow(1.15, scrollDeltaY), MIN_ZOOM, MAX_ZOOM);
        if (wanted != zoom) {
            double canvasX = (mouseX - originX()) / zoom;
            double canvasY = (mouseY - originY()) / zoom;
            zoom = wanted;
            scrollX = (int) Math.round(left + PADDING - mouseX + canvasX * zoom);
            scrollY = (int) Math.round(top + headerHeight + PADDING - mouseY + canvasY * zoom);
            clampScroll();
        }
        return true;
    }

    private @Nullable TechnologyLayout.Placed nodeAt(double mouseX, double mouseY) {
        int canvasTop = top + headerHeight;
        if (mouseY < canvasTop || mouseY >= top + paneHeight
                || mouseX < left || mouseX >= left + paneWidth) {
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
