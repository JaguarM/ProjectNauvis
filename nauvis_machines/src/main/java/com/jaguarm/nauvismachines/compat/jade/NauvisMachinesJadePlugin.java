package com.jaguarm.nauvismachines.compat.jade;

import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade about its machines, each of which is several blocks and one block
 * entity.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it.
 *
 * <p>Jade's universal providers already show what is in a machine and how much power it holds.
 * The furnace adds the one line they cannot know - what it is smelting, or why it has stopped -
 * and the assembler adds nothing yet. What both need is the redirect: sending Jade to the block
 * entity when a player points at any of the other blocks the machine is made of. See
 * {@code MultiblockRedirect} in nauvis_lib, which is where that is explained.
 */
@WailaPlugin
public class NauvisMachinesJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FurnaceReadout.INSTANCE, FurnaceBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FurnaceReadout.Client.INSTANCE, FurnaceBlock.class);
    }
}
