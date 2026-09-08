package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** One lane of a belt run: the items on it, and where they are. */
public final class BeltLane {

    /** The items, leading item first - "leading" meaning nearest the end of the run. */
    private final List<ItemResource> items = new ArrayList<>();

    /** Free space in front of each item. Entry 0 is the distance from the run's end. */
    private final IntArrayList slack = new IntArrayList();

    /**
     * How many items at the front are packed solid against each other.
     *
     * <p>Always safe to under-state and never safe to over-state: too small only costs a longer
     * loop, which then puts it right again. Anything structural resets it to nothing.
     */
    private int packed;

    /**
     * How far each item moved on the last tick, as a step function - the movement recorded at
     * {@code moveFrom[k]} applies until the next entry, and the last entry runs to the end.
     *
     * <p>It exists for the renderer, which has to draw an item part-way between two ticks and has
     * nothing else to interpolate from. Movement is non-decreasing down the lane, so this is a
     * handful of entries however many items there are.
     */
    private final IntArrayList moveFrom = new IntArrayList();
    private final IntArrayList moveAt = new IntArrayList();

    /** {@link #positions()}, worked out at most once between changes. */
    private final IntArrayList cachedPositions = new IntArrayList();
    private boolean positionsStale = true;

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public ItemResource item(int index) {
        return items.get(index);
    }

    /** How far the leading item still has to go. Zero means it is at the end, waiting to leave. */
    public int lead() {
        return items.isEmpty() ? Integer.MAX_VALUE : slack.getInt(0);
    }

    /**
     * The distance from the end of the run of every item, leading item first.
     *
     * <p><b>The list is this lane's own and is rebuilt the moment anything changes.</b> Read it,
     * do not keep it, and take a copy before removing anything while walking it. It is cached
     * rather than built afresh because the things that ask - an inserter looking at what is on the
     * tile beside it, the renderer drawing a belt - ask several times for the same answer within
     * one tick, and turning positions back out of gaps is the one operation here that is not free.
     */
    public IntArrayList positions() {
        if (!positionsStale) {
            return cachedPositions;
        }
        cachedPositions.clear();
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            at += slack.getInt(i) + (i == 0 ? 0 : Belts.SPACING);
            cachedPositions.add(at);
        }
        positionsStale = false;
        return cachedPositions;
    }

    /** The distance from the end of the run of one item. */
    public int position(int index) {
        int at = 0;
        for (int i = 0; i <= index; i++) {
            at += slack.getInt(i) + (i == 0 ? 0 : Belts.SPACING);
        }
        return at;
    }

    /** How far item {@code index} moved on the last tick, for the renderer to interpolate with. */
    public int lastMove(int index) {
        int move = 0;
        for (int k = 0; k < moveFrom.size(); k++) {
            if (moveFrom.getInt(k) > index) {
                break;
            }
            move = moveAt.getInt(k);
        }
        return move;
    }

    /**
     * Moves everything up by at most {@code speed}, and says whether anything moved.
     *
     * <p>The two early exits are the point of the whole class: the first item to move a full step
     * ends the loop, because everything behind it moves a full step too and no gap between them
     * changes; and the packed prefix is skipped outright, because items with no room move exactly
     * as far as the item in front of them and their gaps do not change either.
     */
    public boolean advance(int speed) {
        clearTrace();
        if (items.isEmpty()) {
            return false;
        }

        positionsStale = true;
        int leadSlack = slack.getInt(0);
        int moved = Math.min(speed, leadSlack);
        if (moved > 0) {
            slack.set(0, leadSlack - moved);
        }
        trace(0, moved);

        if (moved == speed) {
            // Everything behind keeps station with the leader. Nothing else to write.
            return true;
        }

        boolean any = moved > 0;
        int previous = moved;
        // Items 1..packed have no slack, so they move exactly as far as the leader did and their
        // gaps are unchanged. The first item that can behave differently is the one after them.
        trace(1, previous);
        for (int i = packed + 1; i < items.size(); i++) {
            int available = slack.getInt(i) + previous;
            int step = Math.min(speed, available);
            slack.set(i, available - step);
            if (available == step && i == packed + 1) {
                packed = i;
            }
            trace(i, step);
            previous = step;
            any |= step > 0;
            if (step == speed) {
                break;
            }
        }
        return any;
    }

    /** Whether an item could sit at {@code position} without crowding its neighbours. */
    public boolean hasRoomAt(int position) {
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            at += slack.getInt(i) + (i == 0 ? 0 : Belts.SPACING);
            if (Math.abs(at - position) < Belts.SPACING) {
                return false;
            }
            if (at > position) {
                return true;
            }
        }
        return true;
    }

    /** Where {@code position} falls in the ordering: the number of items ahead of it. */
    public int indexFor(int position) {
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            at += slack.getInt(i) + (i == 0 ? 0 : Belts.SPACING);
            if (at > position) {
                return i;
            }
        }
        return items.size();
    }

    /**
     * Puts an item on the lane at an exact distance from the end of the run.
     *
     * @return the index it landed at, which is what undoing the insertion needs.
     */
    public int insertAt(int position, ItemResource item) {
        int index = indexFor(position);
        int ahead = index == 0 ? 0 : position(index - 1);
        int own = index == 0 ? position : position - ahead - Belts.SPACING;

        items.add(index, item);
        slack.add(index, own);
        if (index + 1 < slack.size()) {
            // The item that was here keeps its place, so its gap absorbs the newcomer.
            slack.set(index + 1, slack.getInt(index + 1) - own - Belts.SPACING);
        }
        packed = 0;
        positionsStale = true;
        return index;
    }

    /** Takes an item off, leaving everything behind it exactly where it was. */
    public ItemResource removeAt(int index) {
        ItemResource item = items.remove(index);
        int own = slack.removeInt(index);
        if (index < slack.size()) {
            slack.set(index, slack.getInt(index) + own + Belts.SPACING);
        }
        packed = 0;
        positionsStale = true;
        return item;
    }

    /** The gap an item held, so {@link #restoreAt} can put it back exactly. */
    public int slackAt(int index) {
        return slack.getInt(index);
    }

    /** Puts back an item taken by {@link #removeAt}, undoing it precisely. */
    public void restoreAt(int index, ItemResource item, int own) {
        items.add(index, item);
        slack.add(index, own);
        if (index + 1 < slack.size()) {
            slack.set(index + 1, slack.getInt(index + 1) - own - Belts.SPACING);
        }
        packed = 0;
        positionsStale = true;
    }

    public void clear() {
        items.clear();
        slack.clear();
        packed = 0;
        positionsStale = true;
        clearTrace();
    }

    private void clearTrace() {
        moveFrom.clear();
        moveAt.clear();
    }

    private void trace(int from, int move) {
        if (!moveAt.isEmpty() && moveAt.getInt(moveAt.size() - 1) == move) {
            return;
        }
        moveFrom.add(from);
        moveAt.add(move);
    }
}
