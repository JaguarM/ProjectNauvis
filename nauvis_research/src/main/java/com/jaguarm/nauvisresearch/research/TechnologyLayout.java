package com.jaguarm.nauvisresearch.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/**
 * Where each technology sits when the tree is drawn as a tree.
 *
 * <h2>Why this is here and not in the screen</h2>
 *
 * <p>Nothing in this repository can look at a screen - that is a standing hole and not a gap in
 * the suite, and three of this pack's bugs have lived in it. So the part of "draw a tech tree"
 * that <em>can</em> be checked is separated from the part that cannot: this computes positions and
 * edges and is asserted by {@code technology_layout_is_sound}, and the screen does nothing but
 * paint what it is handed. It is also common code rather than client code, which is what lets a
 * gametest reach it at all.
 *
 * <h2>The layout, which is Factorio's</h2>
 *
 * <p><b>Columns are the longest path from a root.</b> Not the shortest - a technology sits to the
 * right of <em>every</em> prerequisite, so an edge never points backwards and the eye can read
 * left to right as "what comes first". `automation_2` needs Automation, Steel processing and
 * green science, and it lands one column right of the last of them rather than one right of the
 * first.
 *
 * <p><b>Rows are settled by barycentre.</b> Each node wants to sit level with the average of its
 * prerequisites; sweeping left to right and sorting each column by that average is the standard
 * cheap answer to edge crossings, and at this size it is enough. Ties break on the tree's own
 * `order` string, so the result is <b>deterministic</b> - the same tree lays out the same way
 * every time, which matters because a layout that shuffled between openings would be unusable
 * whatever it looked like.
 *
 * <p><b>Nothing is authored.</b> There are no coordinates in the data files and there must not be:
 * the tree is twenty-five technologies today and Factorio's is two hundred, and hand-placing them
 * is a job that has to be redone every time one is added.
 */
public final class TechnologyLayout {

    private TechnologyLayout() {}

    /** One technology's place: {@code column} across, {@code row} down, both zero-based. */
    public record Placed(ResourceKey<Technology> key, Technology technology, int column, int row) {}

    /** One prerequisite arrow, from {@code from} to {@code to}. */
    public record Edge(ResourceKey<Technology> from, ResourceKey<Technology> to) {}

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

        // Group into columns, keeping the tree's order within each until barycentre moves them.
        Map<Integer, List<ResourceKey<Technology>>> columns = new LinkedHashMap<>();
        for (Holder.Reference<Technology> holder : all) {
            columns.computeIfAbsent(column.get(holder.key()), ignored -> new ArrayList<>())
                    .add(holder.key());
        }

        Map<ResourceKey<Technology>, Integer> row = new HashMap<>();
        Map<ResourceKey<Technology>, Integer> order = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            order.put(all.get(i).key(), i);
        }

        int widest = 0;
        for (int index = 0; index <= columns.keySet().stream().mapToInt(Integer::intValue).max().orElse(0); index++) {
            List<ResourceKey<Technology>> here = columns.getOrDefault(index, List.of());
            if (here.isEmpty()) {
                continue;
            }

            // Sort by where this node's prerequisites already sit. A node with none keeps its
            // place in the tree's order, which is what puts the two triggered roots at the top.
            List<ResourceKey<Technology>> sorted = new ArrayList<>(here);
            sorted.sort(Comparator
                    .<ResourceKey<Technology>>comparingDouble(key -> barycentre(key, byKey, row, order))
                    .thenComparingInt(order::get));

            for (int r = 0; r < sorted.size(); r++) {
                row.put(sorted.get(r), r);
            }
            columns.put(index, sorted);
            widest = Math.max(widest, sorted.size());
        }

        List<Placed> nodes = new ArrayList<>(byKey.size());
        for (Map.Entry<Integer, List<ResourceKey<Technology>>> entry : columns.entrySet()) {
            for (ResourceKey<Technology> key : entry.getValue()) {
                nodes.add(new Placed(key, byKey.get(key), entry.getKey(), row.get(key)));
            }
        }
        nodes.sort(Comparator.comparingInt(Placed::column).thenComparingInt(Placed::row));

        List<Edge> edges = new ArrayList<>();
        for (Placed placed : nodes) {
            for (ResourceKey<Technology> prerequisite : placed.technology().prerequisites()) {
                if (byKey.containsKey(prerequisite)) {
                    edges.add(new Edge(prerequisite, placed.key()));
                }
            }
        }

        return new Layout(List.copyOf(nodes), List.copyOf(edges),
                columns.isEmpty() ? 0 : columns.size(), widest);
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

    /** The average row of the prerequisites already placed, or this node's own order if none. */
    private static double barycentre(ResourceKey<Technology> key,
            Map<ResourceKey<Technology>, Technology> byKey,
            Map<ResourceKey<Technology>, Integer> row,
            Map<ResourceKey<Technology>, Integer> order) {

        Technology technology = byKey.get(key);
        int total = 0;
        int count = 0;
        for (ResourceKey<Technology> prerequisite : technology.prerequisites()) {
            Integer placed = row.get(prerequisite);
            if (placed != null) {
                total += placed;
                count++;
            }
        }
        return count == 0 ? order.get(key) : (double) total / count;
    }
}
