package com.jaguarm.nauvislogistics.compat.jade;

import com.jaguarm.nauvislogistics.belt.BeltBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** What this mod tells Jade to say about a belt. */
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
