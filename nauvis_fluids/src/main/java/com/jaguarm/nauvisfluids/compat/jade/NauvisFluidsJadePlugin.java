package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlock;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade to say about a pipe, an oil well, a pumpjack and an offshore pump.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it.
 *
 * <p>Each readout is two classes, a data half and a {@code Client} half. Jade throws at
 * registration if one object is both, and has since 1.21.6.
 */
@WailaPlugin
public class NauvisFluidsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(PipeReadout.INSTANCE, PipeBlock.class);
        registration.registerBlockDataProvider(CrudeOilReadout.INSTANCE, CrudeOilBlock.class);
        registration.registerBlockDataProvider(PumpjackReadout.INSTANCE, PumpjackBlock.class);
        registration.registerBlockDataProvider(OffshorePumpReadout.INSTANCE, OffshorePumpBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PipeReadout.Client.INSTANCE, PipeBlock.class);
        registration.registerBlockComponent(CrudeOilReadout.Client.INSTANCE, CrudeOilBlock.class);
        registration.registerBlockComponent(PumpjackReadout.Client.INSTANCE, PumpjackBlock.class);
        registration.registerBlockComponent(OffshorePumpReadout.Client.INSTANCE, OffshorePumpBlock.class);
    }
}
