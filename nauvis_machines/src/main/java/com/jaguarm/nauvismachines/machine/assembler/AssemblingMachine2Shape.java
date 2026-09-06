package com.jaguarm.nauvismachines.machine.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.multiblock.MachineParts;
import com.jaguarm.nauvislib.multiblock.MachineShape;

import net.minecraft.core.Direction;

/**
 * Assembling machine 2: the same three by three as the first machine, with a fluid box.
 *
 * <p>Factorio's second and third assembling machines take one fluid ingredient - the electric
 * engine unit's lubricant, the processing unit's sulfuric acid - and give one fluid back for the
 * barrel recipes, through fluid boxes on the machine's front and back. The first machine has no
 * fluid box at all, which is Factorio's rule and the reason there are tiers. So this is
 * {@link AssemblerShape} with its north and south edge cells opened for a pipe, and two named
 * ports: {@link #FLUID_IN} on the north face, {@link #FLUID_OUT} on the south. The machine faces,
 * so the player chooses which way the pipes go by which way they stand when they place it.
 *
 * <p>The cells are the same ten in the same order as the first machine's, so the {@code part}
 * values in a world saved before the second machine had a fluid box still name the same cells.
 */
public final class AssemblingMachine2Shape {

    private AssemblingMachine2Shape() {}

    /** Ties the cells below to the {@code size} in {@code data/mapping.json}. */
    public static final String FACTORIO_ID = "assembling-machine-2";

    /** Where the fluid ingredient comes in: the middle of the north edge. */
    public static final String FLUID_IN = "fluid_in";

    /** Where a fluid result goes out: the middle of the south edge. */
    public static final String FLUID_OUT = "fluid_out";

    /** The cells that carry the two ports, by their index in the list below. */
    public static final int NORTH_EDGE = 4;
    public static final int SOUTH_EDGE = 6;

    public static final MachineShape SHAPE = build();

    private static MachineShape build() {
        List<MachineCell> cells = new ArrayList<>();

        cells.add(new MachineCell(0, 0, 0, MachineParts.CORNER, 0, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 0, MachineParts.CORNER, 1, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(2, 0, 2, MachineParts.CORNER, 2, MachineParts.CORNER_BOXES));
        cells.add(new MachineCell(0, 0, 2, MachineParts.CORNER, 3, MachineParts.CORNER_BOXES));

        // Edge middles, clockwise from north. The north and south ones are opened for a pipe: the
        // port model faces north in its own frame, and the south one is it turned twice.
        cells.add(new MachineCell(1, 0, 0, MachineParts.PORT_EDGE, 0, MachineParts.PORT_EDGE_BOXES));
        cells.add(new MachineCell(2, 0, 1, MachineParts.EDGE, 1, MachineParts.EDGE_BOXES));
        cells.add(new MachineCell(1, 0, 2, MachineParts.PORT_EDGE, 2, MachineParts.PORT_EDGE_BOXES));
        cells.add(new MachineCell(0, 0, 1, MachineParts.EDGE, 3, MachineParts.EDGE_BOXES));

        cells.add(new MachineCell(1, 0, 1, MachineParts.DECK, 0, MachineParts.DECK_BOXES));
        cells.add(new MachineCell(1, 1, 1, AssemblerShape.GEARBOX, 0, AssemblerShape.GEARBOX_BOXES));

        return new MachineShape(cells, AssemblerShape.MIDDLE, AssemblerShape.MIDDLE, Map.of(
                FLUID_IN, Set.of(new MachineShape.Port(NORTH_EDGE, Direction.NORTH)),
                FLUID_OUT, Set.of(new MachineShape.Port(SOUTH_EDGE, Direction.SOUTH))));
    }
}
