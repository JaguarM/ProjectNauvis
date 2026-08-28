package com.jaguarm.nauvislogistics.compat.jade;

import com.jaguarm.nauvislogistics.belt.BeltBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade to say about a belt.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it.
 *
 * <p>The readout is two classes, a data half and a {@code Client} half. Jade throws at
 * registration if one object is both, and it asserts on a provider with no
 * {@code config.jade.plugin_<modid>.<uid>} translation - from {@code ScreenEvent.Init}, so a
 * missing key is a crash the moment any screen opens rather than a blank line in a menu.
 */
@WailaPlugin
public class NauvisLogisticsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BeltReadout.INSTANCE, BeltBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BeltReadout.Client.INSTANCE, BeltBlock.class);
    }
}
