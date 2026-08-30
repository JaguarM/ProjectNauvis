package com.jaguarm.nauvismachines.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade about the assembling machine, which is nine blocks and one block entity.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it.
 *
 * <p><b>No readout of its own yet.</b> Jade's universal providers already show what is in a
 * machine and how much power it holds, and until there is something to add that a generic provider
 * cannot know - what it is making, why it has stopped - this plugin exists for one reason: to send
 * Jade to the block entity when a player points at any of the other blocks the machine is made of.
 * See {@link MultiblockRedirect}, which is where that is explained.
 */
@WailaPlugin
public class NauvisMachinesJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(new MultiblockRedirect(registration));
    }
}
