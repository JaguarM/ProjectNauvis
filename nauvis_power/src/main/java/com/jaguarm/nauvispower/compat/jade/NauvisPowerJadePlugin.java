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

/**
 * What this mod tells Jade to say about the block you are looking at.
 *
 * <p><b>Nothing here loads unless Jade is installed.</b> Jade finds this class by its annotation
 * and only then touches it, so the dependency is {@code compileOnly}, the mod still runs standalone
 * without it, and {@code neoforge.mods.toml} declares nothing.
 *
 * <p>Jade's own universal providers already show a block's items and a generic energy bar with no
 * help from anyone. What is added here is the half a generic provider cannot know: whether the
 * machine is actually doing anything, and why not when it is not.
 *
 * <h2>Why every one of these needs server data</h2>
 *
 * <p>A boiler's steam and an engine's charge change every tick. Their block entities do sync, but
 * only when something calls {@code sendBlockUpdated}, and a machine that pushed a block update
 * every tick would be sending packets to everyone in render distance to animate a bar nobody is
 * looking at. Jade asks the server for data only while a player is actually looking at the block,
 * which is exactly the right amount - so the numbers come through {@link snownee.jade.api
 * .IServerDataProvider} rather than off the client's copy of the block entity.
 *
 * <p>The pole needs it for a stronger reason: its network is a server-side object and the client
 * has no {@code PowerNetworkManager} at all.
 *
 * <p>Each readout is two classes, a data half and a {@code Client} half. Jade throws at
 * registration if one object is both, and has since 1.21.6 - one class straddling the server and
 * the client is one class that can accidentally reach across.
 */
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
