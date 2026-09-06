package com.jaguarm.nauvislib.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * The one thing the library tells Jade: point at the machine, not the block.
 *
 * <p>Every machine in the pack is several blocks with one block entity, and every readout worth
 * having lives on that block entity. {@link MultiblockRedirect} sends Jade to the anchor
 * whichever cell the player is looking at, for every {@code MachineBlock} in every mod - so the
 * subsystem mods register their readouts and nothing else. Nothing here loads unless Jade is
 * installed; Jade finds this class by its annotation and only then touches it.
 */
@WailaPlugin
public class NauvisLibJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(new MultiblockRedirect(registration));
    }
}
