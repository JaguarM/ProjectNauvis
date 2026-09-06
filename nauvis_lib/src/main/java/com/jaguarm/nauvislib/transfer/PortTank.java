package com.jaguarm.nauvislib.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * The tank behind one port of a processing machine: Factorio's fluid box.
 *
 * <p>A fluid box belongs to a recipe. Choose advanced oil processing and the refinery's two input
 * boxes are water and crude oil, in that order, and a pipe of steam against either is refused;
 * choose nothing and they take nothing. That is what {@link #assign} sets and what
 * {@link #isValid} enforces, and it is what keeps a run of the wrong fluid from filling a machine
 * that could never use it.
 *
 * <p>Changing the recipe throws away what does not belong to the new one, as Factorio does when a
 * machine's recipe is changed: a box of heavy oil left in a refinery switched to basic oil
 * processing has no port to leave by and no craft to be spent on.
 */
public class PortTank extends FluidStacksResourceHandler {

    private static final String ASSIGNED = "Assigned";

    private final Runnable onChanged;
    private @Nullable Fluid assigned;

    public PortTank(int capacity, Runnable onChanged) {
        super(1, capacity);
        this.onChanged = onChanged;
    }

    /** The fluid this tank is for, or null while no recipe gives it one. */
    public @Nullable Fluid assigned() {
        return assigned;
    }

    /** What the tank is showing: its contents, or the fluid it is waiting for, or nothing. */
    public Fluid shownFluid() {
        FluidResource held = getResource(0);
        if (!held.isEmpty()) {
            return held.getFluid();
        }
        return assigned == null ? Fluids.EMPTY : assigned;
    }

    /** Points the tank at a fluid, and empties it of anything else. */
    public void assign(@Nullable Fluid fluid) {
        if (fluid == assigned) {
            return;
        }
        assigned = fluid;
        FluidResource held = getResource(0);
        if (!held.isEmpty() && held.getFluid() != fluid) {
            set(0, FluidResource.EMPTY, 0);
        }
    }

    /** The assigned fluid's resource, for the machine to fill or spend. Empty when unassigned. */
    public FluidResource resource() {
        return assigned == null ? FluidResource.EMPTY : FluidResource.of(assigned);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return assigned != null && resource.getFluid() == assigned;
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }

    @Override
    public void serialize(ValueOutput output) {
        super.serialize(output);
        if (assigned != null) {
            output.putString(ASSIGNED, BuiltInRegistries.FLUID.getKey(assigned).toString());
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        super.deserialize(input);
        assigned = input.getString(ASSIGNED)
                .map(Identifier::tryParse)
                .map(BuiltInRegistries.FLUID::getValue)
                .filter(fluid -> fluid != Fluids.EMPTY)
                .orElse(null);
    }
}
