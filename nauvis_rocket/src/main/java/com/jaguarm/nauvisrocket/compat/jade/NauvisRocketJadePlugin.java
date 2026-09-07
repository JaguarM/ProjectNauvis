package com.jaguarm.nauvisrocket.compat.jade;

import com.jaguarm.nauvisrocket.silo.RocketSiloBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade about the silo. Nothing here loads unless Jade is installed - Jade
 * finds this class by its annotation and only then touches it, so the dependency stays
 * {@code compileOnly}. Two classes per readout, a data half and a {@code Client} half, because
 * Jade throws at registration if one object is both.
 */
@WailaPlugin
public class NauvisRocketJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(RocketSiloReadout.INSTANCE, RocketSiloBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RocketSiloReadout.Client.INSTANCE, RocketSiloBlock.class);
    }
}
