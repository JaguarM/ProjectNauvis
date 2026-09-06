package com.jaguarm.nauvislib.module;

/**
 * An item that is a module: something a machine's {@link ModuleSlots} will take, with an effect
 * on the machine while it sits there.
 *
 * <p>An interface rather than an item class, because the library registers no items - the
 * modules themselves are the machines mod's, per {@code PLAN.md} - and every other machine mod
 * has slots for them without naming that mod. A refinery in the fluids mod asks {@code instanceof
 * Module} of whatever is offered to its slot, and reads the effect off it; which mod made the
 * item is nothing it needs to know.
 */
public interface Module {

    /** What this module does to a machine. */
    ModuleEffect effect();
}
