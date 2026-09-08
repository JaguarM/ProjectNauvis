package com.jaguarm.nauvislib.module;

/**
 * An item that is a module: something a machine's {@link ModuleSlots} will take, with an effect
 * on the machine while it sits there.
 */
public interface Module {

    /** What this module does to a machine. */
    ModuleEffect effect();
}
