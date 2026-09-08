package com.jaguarm.nauvismachines.compat.jade;

import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlock;
import com.jaguarm.nauvismachines.machine.radar.RadarBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade about its machines, each of which is several blocks and one block
 * entity.
 */
@WailaPlugin
public class NauvisMachinesJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FurnaceReadout.INSTANCE, FurnaceBlock.class);
        registration.registerBlockDataProvider(RadarReadout.INSTANCE, RadarBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FurnaceReadout.Client.INSTANCE, FurnaceBlock.class);
        registration.registerBlockComponent(RadarReadout.Client.INSTANCE, RadarBlock.class);
    }
}
