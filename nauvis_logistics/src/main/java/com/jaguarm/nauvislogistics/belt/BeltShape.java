package com.jaguarm.nauvislogistics.belt;

import net.minecraft.util.StringRepresentable;

/** Whether a belt runs straight through its block, turns, or climbs - and which way. */
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
