package com.jaguarm.nauvislogistics.belt;

import net.minecraft.util.StringRepresentable;

/**
 * Whether a belt runs straight through its block, turns, or climbs - and which way.
 *
 * <p>Purely how it is drawn and what you walk on. {@link BeltRun} already carries items round a
 * corner and up a slope - a run follows the block a belt hands to whether or not that block is
 * level with it, and {@code BeltRun.pointAt} takes items in over one edge, through the middle and
 * out of another. What was missing was any sign of it: a bend read as two straight belts meeting at
 * a right angle, and a climb would read as two lines that do not touch.
 *
 * <h2>Corners: left and right</h2>
 *
 * <p>The side the items arrive from, seen from the direction of travel. A belt facing north whose
 * only feeder is to its west is {@link #FROM_LEFT}: things come in at the west edge and leave at the
 * north one. Every other corner in the game is one of these two turned, which is why there are two
 * and not eight.
 *
 * <h2>Slopes: up and down</h2>
 *
 * <p>The same trick again, and for the same reason there are two rather than eight. {@link #UP}
 * rises the way the belt faces and {@link #DOWN} rises against it, so the model is one ramp and its
 * mirror, turned by the belt's facing like everything else.
 *
 * <p><b>A sloped belt always sits at the low end of the climb</b>, which is vanilla's rule for
 * rails and worth knowing before reading the connection code: a belt at ground level whose next
 * belt is one along and one up is the ramp, and the belt on top is flat. A line coming *down* puts
 * the ramp in the lower block too - it is the same ramp, travelled the other way - so a descending
 * belt hands to something level with it and is fed by something above it. See
 * {@link BeltBlock#successorOf}.
 *
 * <p><b>A slope is never a corner.</b> Both are worked out from the neighbours - see
 * {@link BeltBlock#withShape} - and where a belt could be read as either, the slope wins, because
 * the drawing has to agree with where items actually go: a run that climbs must not be drawn
 * turning.
 */
public enum BeltShape implements StringRepresentable {

    STRAIGHT("straight"),
    FROM_LEFT("left"),
    FROM_RIGHT("right"),
    UP("up"),
    DOWN("down");

    private final String name;

    BeltShape(String name) {
        this.name = name;
    }

    /** Whether this belt climbs rather than lying flat - the two that need a ramp and a stair. */
    public boolean isSlope() {
        return this == UP || this == DOWN;
    }

    /**
     * How far the surface climbs across this block, in blocks, in the direction of travel.
     *
     * <p>+1 going up, -1 going down, 0 flat. It is what {@code BeltRun} needs to know to put an
     * item at the right height, and the only thing about a slope that the simulation cares about.
     */
    public int rise() {
        return switch (this) {
            case UP -> 1;
            case DOWN -> -1;
            default -> 0;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
