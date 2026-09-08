package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.SolarPanelBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.grid.ElectricPoleBlock;
import com.jaguarm.nauvispower.storage.AccumulatorBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** What this mod tells Jade to say about the block you are looking at. */
@WailaPlugin
public class NauvisPowerJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BoilerReadout.INSTANCE, BoilerBlock.class);
        registration.registerBlockDataProvider(SteamEngineReadout.INSTANCE, SteamEngineBlock.class);
        registration.registerBlockDataProvider(PoleReadout.INSTANCE, ElectricPoleBlock.class);
        registration.registerBlockDataProvider(SolarPanelReadout.INSTANCE, SolarPanelBlock.class);
        registration.registerBlockDataProvider(AccumulatorReadout.INSTANCE, AccumulatorBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BoilerReadout.Client.INSTANCE, BoilerBlock.class);
        registration.registerBlockComponent(SteamEngineReadout.Client.INSTANCE, SteamEngineBlock.class);
        registration.registerBlockComponent(PoleReadout.Client.INSTANCE, ElectricPoleBlock.class);
        registration.registerBlockComponent(SolarPanelReadout.Client.INSTANCE, SolarPanelBlock.class);
        registration.registerBlockComponent(AccumulatorReadout.Client.INSTANCE, AccumulatorBlock.class);
    }
}
