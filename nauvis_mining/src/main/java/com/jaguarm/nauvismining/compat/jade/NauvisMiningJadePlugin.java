package com.jaguarm.nauvismining.compat.jade;

import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade about its drills.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it. The redirect from any block of a drill to its block entity is nauvis_lib's
 * {@code MultiblockRedirect}, which covers every machine at once.
 */
@WailaPlugin
public class NauvisMiningJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DrillReadout.INSTANCE, MinerBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DrillReadout.Client.INSTANCE, MinerBlock.class);
    }
}
