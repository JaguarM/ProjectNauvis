package com.jaguarm.nauvisresearch.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Where each technology sits when the tree is drawn as a tree, and which arrows are worth drawing.
 *
 * <h2>Why this is here and not in the screen</h2>
 *
 * <p>Nothing in this repository can look at a screen - that is a standing hole and not a gap in
 * the suite, and three of this pack's bugs have lived in it. So the part of "draw a tech tree"
 * that <em>can</em> be checked is separated from the part that cannot: this computes positions,
 * edges and lanes and is asserted by {@code technology_layout_is_sound}, and the screen does
 * nothing but paint what it is handed. It is also common code rather than client code, which is
 * what lets a gametest reach it at all.
 *
 * <h2>Columns are Factorio's, and they are not negotiable</h2>
 *
 * <p><b>A column is the longest path from a root.</b> Not the shortest - a technology sits to the
 * right of <em>every</em> prerequisite, so an edge never points backwards and the eye can read
 * left to right as "what comes first". `automation_2` needs Automation, Steel processing and
 * green science, and it lands one column right of the last of them rather than one right of the
 * first. <b>Columns are computed from every prerequisite, including the ones not drawn below</b>,
 * so what is drawn can change without a technology moving.
 *
 * <h2>Which arrows are drawn, which is where the tangle was</h2>
 *
 * <p>The first version drew every prerequisite, and the third gap came out as several full-height
 * vertical bars side by side with no way to tell which parent fed which child. Measuring it said
 * something useful: <b>the ordering was already optimal</b> - twenty-two crossings, and sixty
 * randomised restarts of barycentre sweeps with adjacent transposition never beat it - and no
 * arrangement could have helped, because two technologies each fed most of the next column. The
 * problem was the number of arrows, not their order.
 *
 * <p>Two rules cut it from thirty-five arrows to eighteen and from twenty-two crossings to one:
 *
 * <ul>
 *   <li><b>An implied prerequisite is not drawn.</b> If A is a prerequisite of B and B of C, the
 *       arrow from A to C says nothing the other two did not - a transitive reduction. It removes
 *       nothing from today's tree and is here because it is the cheap half of the problem and the
 *       tree is going to grow to two hundred;</li>
 *   <li><b>A science pack is a gate, not a parent.</b> Everything past red science needs red
 *       science, and drawing eleven identical arrows out of one node to say so is the same
 *       sentence eleven times. Factorio does not draw them either: a technology's node carries the
 *       pack icons it costs, and this one does now. It is the rule that actually untangled the
 *       tree - the two hubs feeding most of a column were both science packs.</li>
 * </ul>
 *
 * <p><b>Both are about drawing and neither touches research.</b> {@code Research.isAvailable}
 * still wants every prerequisite finished; a gate is still a technology you have to research. All
 * that changes is what the picture repeats.
 *
 * <h2>Rows, in the two steps a layered layout takes</h2>
 *
 * <p><b>Order first.</b> Sweeping a column and sorting it by the median row of its neighbours in
 * the column before is the standard cheap answer to edge crossings; sweeping back the other way
 * against the column after, several times, and keeping whichever pass crossed least is the rest of
 * it. Adjacent pairs are then swapped wherever a swap crosses fewer arrows. Ties break on the
 * tree's own `order` string throughout, so the result is <b>deterministic</b> - the same tree lays
 * out the same way every time, which matters because a layout that shuffled between openings would
 * be unusable whatever it looked like.
 *
 * <p><b>Then position.</b> Order says who is above whom; it does not say a child should be level
 * with its parent. Each column is pulled towards the average row of its neighbours and squeezed
 * back into distinct rows by isotonic regression, which is the least-squares way to keep an order
 * while honouring what everything wanted. <b>Rows are sparse</b> - a column of two beside a column
 * of eleven sits where its arrows point rather than at the top, and there is no centring left for
 * the screen to do.
 *
 * <p><b>An edge that skips a column</b> gets an invisible node in each column it skips, so every
 * arrow the ordering sees joins neighbours. Today's tree has none; a tree of two hundred will.
 *
 * <h2>Lanes</h2>
 *
 * <p>An arrow is drawn as an elbow - out of the parent, across, then into the child - and the
 * across is a vertical run in the gap between two columns. Every arrow out of one parent shares
 * one, which is what makes a fan read as a fan. Two <em>different</em> parents may share one only
 * when their runs do not overlap vertically, which is greedy interval colouring and is optimal
 * here; the third gap needs two lanes rather than the five it has parents.
 *
 * <p><b>Nothing is authored.</b> There are no coordinates in the data files and there must not be:
 * the tree is twenty-six technologies today and Factorio's is two hundred, and hand-placing them
 * is a job that has to be redone every time one is added.
 */
public final class TechnologyLayout {

    private TechnologyLayout() {}

    /** How many times the ordering sweeps back and forth. Past this it stops improving. */
    private static final int ORDER_SWEEPS = 8;

    /** How many times each column is pulled towards its neighbours' rows. */
    private static final int POSITION_SWEEPS = 10;

    /** One technology's place: {@code column} across, {@code row} down, both zero-based. */
    public record Placed(ResourceKey<Technology> key, Technology technology, int column, int row) {}

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

        public Placed at(ResourceKey<Technology> key) {
            for (Placed placed : nodes) {
                if (placed.key().equals(key)) {
                    return placed;
                }
            }
            return null;
        }
    }

    public static Layout of(RegistryAccess access) {
        return of(ModTechnologies.all(access));
    }

    /**
     * @param all every technology, already in the tree's own order - {@code ModTechnologies.all}.
     *            That order is the tiebreak throughout, so passing an unsorted list would still
     *            produce a valid layout and not the same one twice.
     */
    public static Layout of(List<Holder.Reference<Technology>> all) {
        Map<ResourceKey<Technology>, Technology> byKey = new LinkedHashMap<>();
        for (Holder.Reference<Technology> holder : all) {
            byKey.put(holder.key(), holder.value());
        }

        Map<ResourceKey<Technology>, Integer> column = new HashMap<>();
        for (ResourceKey<Technology> key : byKey.keySet()) {
            column(key, byKey, column, 0);
        }

        List<ResourceKey<Technology>> keys = List.copyOf(byKey.keySet());
        List<int[]> drawn = drawnEdges(keys, byKey);

        Graph graph = Graph.of(keys.size(), drawn, index -> column.get(keys.get(index)));
        graph.order();

        // Normalised over the technologies rather than over every vertex, or an invisible node
        // above the first real one would leave an empty row at the top of the screen.
        int[] row = graph.rows();
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            lowest = Math.min(lowest, row[i]);
        }
        for (int i = 0; i < keys.size(); i++) {
            row[i] -= lowest;
        }

        List<Placed> nodes = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            nodes.add(new Placed(keys.get(i), byKey.get(keys.get(i)),
                    column.get(keys.get(i)), row[i]));
        }
        nodes.sort(Comparator.comparingInt(Placed::column).thenComparingInt(Placed::row));

        List<Edge> edges = lanes(keys, drawn, graph, row);

        int columns = 0;
        int rows = 0;
        for (Placed placed : nodes) {
            columns = Math.max(columns, placed.column() + 1);
            rows = Math.max(rows, placed.row() + 1);
        }
        return new Layout(List.copyOf(nodes), List.copyOf(edges), columns, rows);
    }

    // ---------------------------------------------------------------------- which arrows to draw

    /**
     * Every prerequisite worth an arrow, as {@code {parent index, child index}} pairs.
     *
     * <p>The two rules are in the class comment. Both are about the picture: a prerequisite that
     * is not drawn is still a prerequisite, and {@code Research} never sees this list.
     */
    private static List<int[]> drawnEdges(List<ResourceKey<Technology>> keys,
            Map<ResourceKey<Technology>, Technology> byKey) {

        Map<ResourceKey<Technology>, Integer> index = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            index.put(keys.get(i), i);
        }

        Set<ResourceKey<Technology>> gates = gates(byKey);
        Map<ResourceKey<Technology>, Set<ResourceKey<Technology>>> behind = new HashMap<>();
        for (ResourceKey<Technology> key : keys) {
            behind(key, byKey, behind, 0);
        }

        List<int[]> edges = new ArrayList<>();
        for (ResourceKey<Technology> key : keys) {
            List<ResourceKey<Technology>> prerequisites = byKey.get(key).prerequisites();
            for (ResourceKey<Technology> prerequisite : prerequisites) {
                if (!index.containsKey(prerequisite) || gates.contains(prerequisite)) {
                    continue;
                }
                boolean implied = false;
                for (ResourceKey<Technology> other : prerequisites) {
                    if (!other.equals(prerequisite)
                            && behind.getOrDefault(other, Set.of()).contains(prerequisite)) {
                        implied = true;
                        break;
                    }
                }
                if (!implied) {
                    edges.add(new int[] {index.get(prerequisite), index.get(key)});
                }
            }
        }
        return edges;
    }

    /**
     * The technologies that hand over a science pack.
     *
     * <p>Found from the data rather than named here, so blue science gates the same way red does
     * the day somebody adds it: a technology is a gate when it unlocks a recipe for an item that
     * some technology names among the packs it costs.
     */
    public static Set<ResourceKey<Technology>> gates(List<Holder.Reference<Technology>> all) {
        Map<ResourceKey<Technology>, Technology> byKey = new LinkedHashMap<>();
        for (Holder.Reference<Technology> holder : all) {
            byKey.put(holder.key(), holder.value());
        }
        return gates(byKey);
    }

    private static Set<ResourceKey<Technology>> gates(
            Map<ResourceKey<Technology>, Technology> byKey) {

        Set<Identifier> packs = new HashSet<>();
        for (Technology technology : byKey.values()) {
            packs.addAll(technology.packs());
        }
        Set<ResourceKey<Technology>> gates = new HashSet<>();
        for (Map.Entry<ResourceKey<Technology>, Technology> entry : byKey.entrySet()) {
            for (ResourceKey<Recipe<?>> unlock : entry.getValue().unlocks()) {
                if (packs.contains(unlock.identifier())) {
                    gates.add(entry.getKey());
                    break;
                }
            }
        }
        return gates;
    }

    /**
     * Everything a technology needs, transitively - what makes a prerequisite implied.
     *
     * <p>Depth-limited for the same reason {@link #column} is: this is fed by a datapack registry
     * and a cycle in one must not be a stack overflow while opening a screen.
     */
    private static Set<ResourceKey<Technology>> behind(ResourceKey<Technology> key,
            Map<ResourceKey<Technology>, Technology> byKey,
            Map<ResourceKey<Technology>, Set<ResourceKey<Technology>>> known, int depth) {

        Set<ResourceKey<Technology>> cached = known.get(key);
        if (cached != null) {
            return cached;
        }
        Technology technology = byKey.get(key);
        if (technology == null || depth > byKey.size()) {
            return Set.of();
        }
        // Seeded before recursing, so a cycle sees a partial answer rather than looping.
        Set<ResourceKey<Technology>> found = new HashSet<>();
        known.put(key, found);
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            if (byKey.containsKey(prerequisite)) {
                found.add(prerequisite);
                found.addAll(behind(prerequisite, byKey, known, depth + 1));
            }
        }
        return found;
    }

    /**
     * The longest path from a technology with no prerequisites.
     *
     * <p>Depth-limited rather than cycle-checked: the generator already refuses a tree it cannot
     * bootstrap, so a cycle cannot reach here from generated data - but this is fed by a datapack
     * registry, which anybody can write, and a stack overflow while opening a screen is a worse
     * way to find that out than a technology drawn in the wrong column.
     */
    private static int column(ResourceKey<Technology> key,
            Map<ResourceKey<Technology>, Technology> byKey,
            Map<ResourceKey<Technology>, Integer> known, int depth) {

        Integer cached = known.get(key);
        if (cached != null) {
            return cached;
        }
        Technology technology = byKey.get(key);
        if (technology == null || depth > byKey.size()) {
            return 0;
        }

        int deepest = -1;
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            if (byKey.containsKey(prerequisite)) {
                deepest = Math.max(deepest, column(prerequisite, byKey, known, depth + 1));
            }
        }
        int result = deepest + 1;
        known.put(key, result);
        return result;
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
    private static List<Edge> lanes(List<ResourceKey<Technology>> keys, List<int[]> drawn,
            Graph graph, int[] row) {

        Map<Integer, List<Integer>> children = new LinkedHashMap<>();
        for (int[] edge : drawn) {
            children.computeIfAbsent(edge[0], ignored -> new ArrayList<>()).add(edge[1]);
        }

        // Parents grouped by column, each with the span its arrows cover, top edge first.
        Map<Integer, List<int[]>> byColumn = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : children.entrySet()) {
            int parent = entry.getKey();
            int low = row[parent];
            int high = row[parent];
            for (int child : entry.getValue()) {
                low = Math.min(low, row[child]);
                high = Math.max(high, row[child]);
            }
            byColumn.computeIfAbsent(graph.column(parent), ignored -> new ArrayList<>())
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
            int at = graph.column(edge[0]);
            edges.add(new Edge(keys.get(edge[0]), keys.get(edge[1]),
                    lane.get(edge[0]), count.get(at)));
        }
        edges.sort(Comparator.comparing((Edge e) -> e.from().identifier().toString())
                .thenComparing(e -> e.to().identifier().toString()));
        return edges;
    }

    // ------------------------------------------------------------------------- the layered graph

    /**
     * The graph the ordering and positioning work on: integer vertices in layers.
     *
     * <p>Vertices {@code 0..technologies-1} are the technologies, in the tree's own order, and
     * everything above that is an invisible node standing in for one column of an arrow that skips
     * a column. The dummies exist so that every arrow the ordering counts joins two neighbouring
     * layers, which is what makes crossing counts mean anything; nothing ever draws one.
     */
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
         * <p>Order says who is above whom and nothing about how far; this is the step that puts a
         * child level with its parent. Each column asks for the average row of its neighbours and
         * is then squeezed back into distinct rows by {@link #isotonic}, which is the least-squares
         * way to honour what everything wanted while keeping the order the crossing count was
         * computed for.
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
