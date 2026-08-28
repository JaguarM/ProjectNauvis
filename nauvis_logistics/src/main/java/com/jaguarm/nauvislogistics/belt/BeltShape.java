package com.jaguarm.nauvislogistics.belt;

import net.minecraft.util.StringRepresentable;

/**
 * Whether a belt runs straight through its block or turns, and which way.
 *
 * <p>Purely how it is drawn. {@link BeltRun} already carries items round a corner - a run follows
 * the block a belt faces whether or not that block faces the same way, and {@code BeltRun.pointAt}
 * takes them in over one edge, through the middle and out of another. What was missing was any
 * sign of it: a bend read as two straight belts meeting at a right angle, so a corner looked like
 * a mistake even while it worked.
 *
 * <p>Left and right are the side the items arrive from, seen from the direction of travel. A belt
 * facing north whose only feeder is to its west is {@link #FROM_LEFT}: things come in at the west
 * edge and leave at the north one. Every other corner in the game is one of these two turned, which
 * is why there are two and not eight.
 *
 * <p>It is worked out from the neighbours rather than stored, the same way a pipe works out its
 * connections - see {@link BeltBlock#withShape}. A belt with no feeder, or with more than one, is
 * {@link #STRAIGHT}: two feeders is a side-load, and Factorio draws that as a straight belt with
 * something joining it rather than as a bend.
 */
public enum BeltShape implements StringRepresentable {

    STRAIGHT("straight"),
    FROM_LEFT("left"),
    FROM_RIGHT("right");

    private final String name;

    BeltShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
