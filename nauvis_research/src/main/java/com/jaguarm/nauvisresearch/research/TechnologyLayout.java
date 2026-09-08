package com.jaguarm.nauvisresearch.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

/** The piece of the technology tree worth drawing around one technology, and where it goes. */
public final class TechnologyLayout {

    private TechnologyLayout() {}

    /** How far past the selection the picture goes. Factorio shows about this much. */
    public static final int DESCENDANT_DEPTH = 2;

    /** How many times the ordering sweeps back and forth. Past this it stops improving. */
    private static final int ORDER_SWEEPS = 8;

    /** How many times each column is pulled towards its neighbours' rows. */
    private static final int POSITION_SWEEPS = 10;

    /** Which side of the selection a technology is on, which is all the screen needs to colour it. */
    public enum Kind {
        SELECTED,
        ANCESTOR,
        DESCENDANT
    }

    /**
     * One technology's place: {@code column} across, {@code row} down, both zero-based.
     *
     * @param outside how many of its prerequisites are not in the picture at all. Zero for the
     *                selection and its ancestors, by construction; the number that matters is on a
     *                descendant, where it is the difference between "this unlocks that" and "this
     *                and two other things unlock that".
     */
    public record Placed(ResourceKey<Technology> key, Technology technology, int column, int row,
            Kind kind, int outside) {}

    /**
     * One prerequisite arrow, from {@code from} to {@code to}.
     *
     * @param lane  which vertical channel of the gap right of {@code from}'s column this arrow
     *              turns in, counted from the top. Every arrow out of one parent shares it, and
     *              two parents share one only when their runs cannot touch.
     * @param lanes how many channels that gap needs, so a screen can space them.
     */
    public record Edge(ResourceKey<Technology> from, ResourceKey<Technology> to,
            int lane, int lanes) {}

    /** The whole picture, in grid cells. A screen decides what a cell is worth in pixels. */
    public record Layout(List<Placed> nodes, List<Edge> edges, int columns, int rows) {

        public static final Layout EMPTY = new Layout(List.of(), List.of(), 0, 0);

        public @Nullable Placed at(ResourceKey<Technology> key) {
            for (Placed placed : nodes) {
                if (placed.key().equals(key)) {
                    return placed;
                }
            }
            return null;
        }
    }

    /**
     * The picture around one technology.
     *
     * @param all       every technology, in the tree's own order - {@code ModTechnologies.all}.
     * @param selected  what the view is centred on. Null, or a technology this list does not hold,
     * @param completed what the world has researched, for {@code hideResearched}.
     * @param hideResearched drops researched ancestors, keeping the arrows that ran through them.
     */
    public static Layout around(List<Holder.Reference<Technology>> all,
            @Nullable ResourceKey<Technology> selected,
            Set<ResourceKey<Technology>> completed, boolean hideResearched) {

        Map<ResourceKey<Technology>, Technology> byKey = new LinkedHashMap<>();
        for (Holder.Reference<Technology> holder : all) {
            byKey.put(holder.key(), holder.value());
        }
        if (selected == null || !byKey.containsKey(selected)) {
            return Layout.EMPTY;
        }

        Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children = children(byKey);

        Set<ResourceKey<Technology>> ancestors = ancestors(selected, byKey);
        Map<ResourceKey<Technology>, Integer> descendants =
                descendants(selected, children, ancestors);

        Set<ResourceKey<Technology>> inView = new LinkedHashSet<>();
        inView.add(selected);
        inView.addAll(ancestors);
        inView.addAll(descendants.keySet());

        Set<ResourceKey<Technology>> hidden = new HashSet<>();
        if (hideResearched) {
            for (ResourceKey<Technology> ancestor : ancestors) {
                if (completed.contains(ancestor)) {
                    hidden.add(ancestor);
                }
            }
        }

        // Order matters for the tiebreak, so walk `all` rather than the sets.
        List<ResourceKey<Technology>> visible = new ArrayList<>();
        for (Holder.Reference<Technology> holder : all) {
            if (inView.contains(holder.key()) && !hidden.contains(holder.key())) {
                visible.add(holder.key());
            }
        }

        Map<ResourceKey<Technology>, Integer> column =
                columns(selected, ancestors, descendants, children, visible);
        List<int[]> drawn = arrows(visible, children, hidden, column);

        Map<ResourceKey<Technology>, Integer> index = new HashMap<>();
        for (int i = 0; i < visible.size(); i++) {
            index.put(visible.get(i), i);
        }
        Graph graph = Graph.of(visible.size(), drawn, at -> column.get(visible.get(at)));
        graph.order();

        int[] row = graph.rows();
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < visible.size(); i++) {
            lowest = Math.min(lowest, row[i]);
        }

        List<Placed> nodes = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            ResourceKey<Technology> key = visible.get(i);
            Kind kind = key.equals(selected) ? Kind.SELECTED
                    : ancestors.contains(key) ? Kind.ANCESTOR
                    : Kind.DESCENDANT;
            int outside = 0;
            for (ResourceKey<Technology> prerequisite : byKey.get(key).prerequisites()) {
                if (byKey.containsKey(prerequisite) && !inView.contains(prerequisite)) {
                    outside++;
                }
            }
            nodes.add(new Placed(key, byKey.get(key), column.get(key), row[i] - lowest,
                    kind, outside));
        }
        nodes.sort(Comparator.comparingInt(Placed::column).thenComparingInt(Placed::row));

        List<Edge> edges = lanes(visible, drawn, column, row);

        int columns = 0;
        int rows = 0;
        for (Placed placed : nodes) {
            columns = Math.max(columns, placed.column() + 1);
            rows = Math.max(rows, placed.row() + 1);
        }
        return new Layout(List.copyOf(nodes), List.copyOf(edges), columns, rows);
    }

    /** Forward edges, which the prerequisite lists only give backwards. */
    private static Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children(
            Map<ResourceKey<Technology>, Technology> byKey) {

        Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children = new HashMap<>();
        for (Map.Entry<ResourceKey<Technology>, Technology> entry : byKey.entrySet()) {
            for (ResourceKey<Technology> prerequisite : entry.getValue().prerequisites()) {
                if (byKey.containsKey(prerequisite)) {
                    children.computeIfAbsent(prerequisite, ignored -> new ArrayList<>())
                            .add(entry.getKey());
                }
            }
        }
        return children;
    }

    /**
     * Everything the selected technology needs, however far back.
     *
     * <p>Breadth-first with a seen set rather than recursion, so a cycle written into a datapack is
     * a strange picture rather than a stack overflow while a screen opens.
     */
    private static Set<ResourceKey<Technology>> ancestors(ResourceKey<Technology> selected,
            Map<ResourceKey<Technology>, Technology> byKey) {

        Set<ResourceKey<Technology>> found = new LinkedHashSet<>();
        Deque<ResourceKey<Technology>> frontier = new ArrayDeque<>(List.of(selected));
        while (!frontier.isEmpty()) {
            Technology technology = byKey.get(frontier.removeFirst());
            if (technology == null) {
                continue;
            }
            for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
                if (byKey.containsKey(prerequisite) && !prerequisite.equals(selected)
                        && found.add(prerequisite)) {
                    frontier.add(prerequisite);
                }
            }
        }
        return found;
    }

    /**
     * What the selection leads to, and how far - the <b>longest</b> way round, capped at {@link
     * #DESCENDANT_DEPTH}.
     */
    private static Map<ResourceKey<Technology>, Integer> descendants(
            ResourceKey<Technology> selected,
            Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children,
            Set<ResourceKey<Technology>> ancestors) {

        Map<ResourceKey<Technology>, Integer> depth = new LinkedHashMap<>();
        List<ResourceKey<Technology>> frontier = List.of(selected);
        for (int step = 1; step <= DESCENDANT_DEPTH; step++) {
            List<ResourceKey<Technology>> next = new ArrayList<>();
            for (ResourceKey<Technology> at : frontier) {
                for (ResourceKey<Technology> child : children.getOrDefault(at, List.of())) {
                    if (child.equals(selected) || ancestors.contains(child)) {
                        continue;
                    }
                    depth.put(child, step);
                    next.add(child);
                }
            }
            frontier = next;
        }

        // Relax to the longest path through what was found. A DAG, so this settles; the bound is
        // there for a tree that is not one, which the generator refuses anyway.
        for (int pass = 0; pass <= depth.size(); pass++) {
            boolean changed = false;
            for (ResourceKey<Technology> parent : List.copyOf(depth.keySet())) {
                int below = depth.get(parent) + 1;
                for (ResourceKey<Technology> child : children.getOrDefault(parent, List.of())) {
                    if (depth.containsKey(child) && depth.get(child) < below) {
                        depth.put(child, below);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        depth.values().removeIf(steps -> steps > DESCENDANT_DEPTH);
        return depth;
    }

    /**
     * How far each visible technology is from the selection, in columns, counted from zero.
     *
     * <p>Ancestors negative, the selection zero, descendants positive, and then everything shifted
     * and squeezed so the columns actually used run 0, 1, 2 with no gaps - hiding the researched
     * ancestors can empty a column, and an empty column drawn as a column is a hole in the picture.
     */
    private static Map<ResourceKey<Technology>, Integer> columns(ResourceKey<Technology> selected,
            Set<ResourceKey<Technology>> ancestors,
            Map<ResourceKey<Technology>, Integer> descendants,
            Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children,
            List<ResourceKey<Technology>> visible) {

        Map<ResourceKey<Technology>, Integer> distance = new HashMap<>();
        distance.put(selected, 0);
        for (ResourceKey<Technology> ancestor : ancestors) {
            toSelection(ancestor, selected, ancestors, children, distance, 0);
        }

        Map<ResourceKey<Technology>, Integer> raw = new HashMap<>();
        raw.put(selected, 0);
        for (ResourceKey<Technology> ancestor : ancestors) {
            raw.put(ancestor, -distance.getOrDefault(ancestor, 1));
        }
        raw.putAll(descendants);

        List<Integer> used = new ArrayList<>(new java.util.TreeSet<>(
                visible.stream().map(raw::get).toList()));
        Map<ResourceKey<Technology>, Integer> column = new HashMap<>();
        for (ResourceKey<Technology> key : visible) {
            column.put(key, used.indexOf(raw.get(key)));
        }
        return column;
    }

    /** The longest path from an ancestor forward to the selection, in steps. */
    private static int toSelection(ResourceKey<Technology> key, ResourceKey<Technology> selected,
            Set<ResourceKey<Technology>> ancestors,
            Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children,
            Map<ResourceKey<Technology>, Integer> known, int depth) {

        Integer cached = known.get(key);
        if (cached != null) {
            return cached;
        }
        if (depth > ancestors.size() + 1) {
            return 1;
        }
        int furthest = 0;
        for (ResourceKey<Technology> child : children.getOrDefault(key, List.of())) {
            if (child.equals(selected) || ancestors.contains(child)) {
                furthest = Math.max(furthest,
                        toSelection(child, selected, ancestors, children, known, depth + 1));
            }
        }
        int result = furthest + 1;
        known.put(key, result);
        return result;
    }

    /**
     * Which arrows to draw, as {@code {from index, to index}} into {@code visible}.
     *
     * <p>Two technologies are joined when a path runs from one to the other through nothing but
     * hidden ones - so hiding the researched ancestors thins the picture without breaking it. An
     * arrow implied by two others is then dropped, because a graph that says the same thing twice
     * is the thing that made the first version of this unreadable.
     */
    private static List<int[]> arrows(List<ResourceKey<Technology>> visible,
            Map<ResourceKey<Technology>, List<ResourceKey<Technology>>> children,
            Set<ResourceKey<Technology>> hidden,
            Map<ResourceKey<Technology>, Integer> column) {

        Map<ResourceKey<Technology>, Integer> index = new HashMap<>();
        for (int i = 0; i < visible.size(); i++) {
            index.put(visible.get(i), i);
        }

        // Straight joins first: walk forward from each visible node, stepping only through hidden
        // ones, and take every visible node reached.
        List<Set<Integer>> direct = new ArrayList<>();
        for (ResourceKey<Technology> from : visible) {
            Set<Integer> reached = new LinkedHashSet<>();
            Set<ResourceKey<Technology>> seen = new HashSet<>();
            Deque<ResourceKey<Technology>> frontier = new ArrayDeque<>(List.of(from));
            while (!frontier.isEmpty()) {
                for (ResourceKey<Technology> child : children.getOrDefault(
                        frontier.removeFirst(), List.of())) {
                    if (!seen.add(child)) {
                        continue;
                    }
                    if (hidden.contains(child)) {
                        frontier.add(child);
                    } else if (index.containsKey(child)) {
                        reached.add(index.get(child));
                    }
                }
            }
            direct.add(reached);
        }

        // Everything each node reaches through the joins above, for the reduction below.
        List<Set<Integer>> beyond = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            beyond.add(new HashSet<>());
        }
        for (int i = visible.size() - 1; i >= 0; i--) {
            for (int child : direct.get(i)) {
                beyond.get(i).add(child);
                beyond.get(i).addAll(beyond.get(child));
            }
        }

        List<int[]> drawn = new ArrayList<>();
        for (int from = 0; from < visible.size(); from++) {
            for (int to : direct.get(from)) {
                boolean implied = false;
                for (int through : direct.get(from)) {
                    if (through != to && beyond.get(through).contains(to)) {
                        implied = true;
                        break;
                    }
                }
                if (!implied && column.get(visible.get(from)) < column.get(visible.get(to))) {
                    drawn.add(new int[] {from, to});
                }
            }
        }
        return drawn;
    }

    // ------------------------------------------------------------------------------------ lanes

    /**
     * Which channel each arrow turns in, by greedy interval colouring.
     *
     * <p>One bundle per parent - every arrow out of a technology turns together - and a bundle
     * covers the rows between the parent and its highest and lowest child. Two bundles need
     * different channels exactly when those spans overlap, which is an interval graph, which is
     * the one case where colouring greedily in order of where each span starts is optimal.
     */
    private static List<Edge> lanes(List<ResourceKey<Technology>> visible, List<int[]> drawn,
            Map<ResourceKey<Technology>, Integer> column, int[] row) {

        Map<Integer, List<Integer>> children = new LinkedHashMap<>();
        for (int[] edge : drawn) {
            children.computeIfAbsent(edge[0], ignored -> new ArrayList<>()).add(edge[1]);
        }

        Map<Integer, List<int[]>> byColumn = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : children.entrySet()) {
            int parent = entry.getKey();
            int low = row[parent];
            int high = row[parent];
            for (int child : entry.getValue()) {
                low = Math.min(low, row[child]);
                high = Math.max(high, row[child]);
            }
            byColumn.computeIfAbsent(column.get(visible.get(parent)), ignored -> new ArrayList<>())
                    .add(new int[] {low, high, parent});
        }

        Map<Integer, Integer> lane = new HashMap<>();
        Map<Integer, Integer> count = new HashMap<>();
        for (Map.Entry<Integer, List<int[]>> entry : byColumn.entrySet()) {
            List<int[]> spans = entry.getValue();
            spans.sort(Comparator.<int[]>comparingInt(span -> span[0])
                    .thenComparingInt(span -> span[2]));
            List<Integer> ends = new ArrayList<>();
            for (int[] span : spans) {
                int chosen = -1;
                for (int i = 0; i < ends.size(); i++) {
                    if (ends.get(i) < span[0]) {
                        ends.set(i, span[1]);
                        chosen = i;
                        break;
                    }
                }
                if (chosen < 0) {
                    chosen = ends.size();
                    ends.add(span[1]);
                }
                lane.put(span[2], chosen);
            }
            count.put(entry.getKey(), ends.size());
        }

        List<Edge> edges = new ArrayList<>(drawn.size());
        for (int[] edge : drawn) {
            edges.add(new Edge(visible.get(edge[0]), visible.get(edge[1]),
                    lane.get(edge[0]), count.get(column.get(visible.get(edge[0])))));
        }
        edges.sort(Comparator.comparing((Edge edge) -> edge.from().identifier().toString())
                .thenComparing(edge -> edge.to().identifier().toString()));
        return edges;
    }

    // ------------------------------------------------------------------------- the layered graph

    private static final class Graph {

        private final int[] column;
        private final List<List<Integer>> layers = new ArrayList<>();
        private final List<List<Integer>> out = new ArrayList<>();
        private final List<List<Integer>> in = new ArrayList<>();
        private final int[] pos;

        private Graph(int vertices, int[] column) {
            this.column = column;
            this.pos = new int[vertices];
            for (int i = 0; i < vertices; i++) {
                out.add(new ArrayList<>());
                in.add(new ArrayList<>());
            }
        }

        private interface Column {
            int of(int technology);
        }

        static Graph of(int technologies, List<int[]> edges, Column columnOf) {
            int extra = 0;
            for (int[] edge : edges) {
                extra += Math.max(0, columnOf.of(edge[1]) - columnOf.of(edge[0]) - 1);
            }
            int[] column = new int[technologies + extra];
            Graph graph = new Graph(technologies + extra, column);
            for (int i = 0; i < technologies; i++) {
                column[i] = columnOf.of(i);
            }

            int next = technologies;
            for (int[] edge : edges) {
                int from = edge[0];
                for (int c = column[edge[0]] + 1; c < columnOf.of(edge[1]); c++) {
                    column[next] = c;
                    graph.link(from, next);
                    from = next;
                    next++;
                }
                graph.link(from, edge[1]);
            }

            int widest = 0;
            for (int c : column) {
                widest = Math.max(widest, c + 1);
            }
            for (int c = 0; c < widest; c++) {
                graph.layers.add(new ArrayList<>());
            }
            for (int v = 0; v < column.length; v++) {
                graph.layers.get(column[v]).add(v);
            }
            for (List<Integer> layer : graph.layers) {
                for (int i = 0; i < layer.size(); i++) {
                    graph.pos[layer.get(i)] = i;
                }
            }
            return graph;
        }

        private void link(int from, int to) {
            out.get(from).add(to);
            in.get(to).add(from);
        }

        int column(int vertex) {
            return column[vertex];
        }

        /** Sweeps and transpositions, keeping whichever pass crossed least. */
        void order() {
            List<List<Integer>> best = snapshot();
            int fewest = crossings();
            for (int sweep = 0; sweep < ORDER_SWEEPS; sweep++) {
                boolean forward = sweep % 2 == 0;
                for (int i = 0; i < layers.size(); i++) {
                    sort(forward ? i : layers.size() - 1 - i, forward);
                }
                transpose();
                int crossings = crossings();
                if (crossings < fewest) {
                    fewest = crossings;
                    best = snapshot();
                }
            }
            restore(best);
        }

        /** Sorts one layer by the median position of its neighbours in the layer beside it. */
        private void sort(int index, boolean forward) {
            List<Integer> layer = layers.get(index);
            Map<Integer, Double> want = new HashMap<>();
            for (int vertex : layer) {
                List<Integer> neighbours = forward ? in.get(vertex) : out.get(vertex);
                if (neighbours.isEmpty()) {
                    // Nothing to be level with, so it stays where it is rather than drifting to
                    // one end - which is what keeps a childless technology beside its siblings.
                    want.put(vertex, (double) pos[vertex]);
                    continue;
                }
                int[] rows = neighbours.stream().mapToInt(n -> pos[n]).sorted().toArray();
                int middle = rows.length / 2;
                want.put(vertex, rows.length % 2 == 1 ? rows[middle]
                        : (rows[middle - 1] + rows[middle]) / 2.0);
            }
            layer.sort(Comparator.<Integer>comparingDouble(want::get)
                    .thenComparingInt(vertex -> vertex));
            for (int i = 0; i < layer.size(); i++) {
                pos[layer.get(i)] = i;
            }
        }

        /**
         * Swaps neighbouring pairs while a swap crosses fewer arrows.
         *
         * <p>Only the two vertices' own arrows can change, so the comparison is over their edge
         * lists rather than the whole gap - which is what makes this cheap enough to run after
         * every sweep.
         */
        private void transpose() {
            boolean improved = true;
            int rounds = 0;
            while (improved && rounds++ < ORDER_SWEEPS) {
                improved = false;
                for (List<Integer> layer : layers) {
                    for (int i = 0; i + 1 < layer.size(); i++) {
                        int a = layer.get(i);
                        int b = layer.get(i + 1);
                        if (between(a, b) > between(b, a)) {
                            layer.set(i, b);
                            layer.set(i + 1, a);
                            pos[b] = i;
                            pos[a] = i + 1;
                            improved = true;
                        }
                    }
                }
            }
        }

        /** Arrows that cross when {@code a} is drawn above {@code b}. */
        private int between(int a, int b) {
            return between(out.get(a), out.get(b)) + between(in.get(a), in.get(b));
        }

        private int between(List<Integer> above, List<Integer> below) {
            int crossings = 0;
            for (int high : above) {
                for (int low : below) {
                    if (pos[low] < pos[high]) {
                        crossings++;
                    }
                }
            }
            return crossings;
        }

        private int crossings() {
            int total = 0;
            for (List<Integer> layer : layers) {
                List<int[]> arrows = new ArrayList<>();
                for (int vertex : layer) {
                    for (int child : out.get(vertex)) {
                        arrows.add(new int[] {pos[vertex], pos[child]});
                    }
                }
                for (int i = 0; i < arrows.size(); i++) {
                    for (int j = i + 1; j < arrows.size(); j++) {
                        int[] one = arrows.get(i);
                        int[] two = arrows.get(j);
                        if ((one[0] - two[0]) * (one[1] - two[1]) < 0) {
                            total++;
                        }
                    }
                }
            }
            return total;
        }

        private List<List<Integer>> snapshot() {
            List<List<Integer>> copy = new ArrayList<>(layers.size());
            for (List<Integer> layer : layers) {
                copy.add(List.copyOf(layer));
            }
            return copy;
        }

        private void restore(List<List<Integer>> saved) {
            for (int i = 0; i < layers.size(); i++) {
                layers.set(i, new ArrayList<>(saved.get(i)));
                for (int j = 0; j < layers.get(i).size(); j++) {
                    pos[layers.get(i).get(j)] = j;
                }
            }
        }

        /**
         * A row for every vertex, order kept, pulled towards what each is joined to.
         *
         * @return one row per technology, the dummies dropped.
         */
        int[] rows() {
            double[] row = new double[pos.length];
            for (int v = 0; v < row.length; v++) {
                row[v] = pos[v];
            }
            for (int sweep = 0; sweep < POSITION_SWEEPS; sweep++) {
                boolean forward = sweep % 2 == 0;
                for (int i = 0; i < layers.size(); i++) {
                    List<Integer> layer = layers.get(forward ? i : layers.size() - 1 - i);
                    double[] want = new double[layer.size()];
                    for (int j = 0; j < layer.size(); j++) {
                        int vertex = layer.get(j);
                        List<Integer> neighbours = forward ? in.get(vertex) : out.get(vertex);
                        double total = 0;
                        for (int neighbour : neighbours) {
                            total += row[neighbour];
                        }
                        want[j] = neighbours.isEmpty() ? row[vertex] : total / neighbours.size();
                        want[j] -= j;
                    }
                    double[] fitted = isotonic(want);
                    for (int j = 0; j < layer.size(); j++) {
                        row[layer.get(j)] = fitted[j] + j;
                    }
                }
            }

            int[] settled = new int[pos.length];
            int lowest = Integer.MAX_VALUE;
            for (List<Integer> layer : layers) {
                int last = Integer.MIN_VALUE;
                for (int vertex : layer) {
                    int at = (int) Math.round(row[vertex]);
                    if (last != Integer.MIN_VALUE && at <= last) {
                        at = last + 1;
                    }
                    settled[vertex] = at;
                    last = at;
                    lowest = Math.min(lowest, at);
                }
            }
            for (int v = 0; v < settled.length; v++) {
                settled[v] -= lowest;
            }
            return settled;
        }

        /**
         * The nearest non-decreasing sequence, by pool adjacent violators.
         *
         * <p>Feed it what each node wanted minus its index and add the index back, and the result
         * is the closest set of rows that keeps every node strictly below the one before it. Doing
         * it any other way - clamping down the column, say - drags a whole column after whichever
         * node happened to be first.
         */
        private static double[] isotonic(double[] wanted) {
            double[] value = new double[wanted.length];
            int[] weight = new int[wanted.length];
            int blocks = 0;
            for (double want : wanted) {
                value[blocks] = want;
                weight[blocks] = 1;
                blocks++;
                while (blocks > 1 && value[blocks - 2] > value[blocks - 1]) {
                    int merged = weight[blocks - 2] + weight[blocks - 1];
                    value[blocks - 2] = (value[blocks - 2] * weight[blocks - 2]
                            + value[blocks - 1] * weight[blocks - 1]) / merged;
                    weight[blocks - 2] = merged;
                    blocks--;
                }
            }
            double[] out = new double[wanted.length];
            int at = 0;
            for (int block = 0; block < blocks; block++) {
                for (int i = 0; i < weight[block]; i++) {
                    out[at++] = value[block];
                }
            }
            return out;
        }
    }
}
