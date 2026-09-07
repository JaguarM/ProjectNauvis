package com.jaguarm.nauvismilitary.compat.jade;

import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * What this mod tells Jade: what a turret is doing, and how much pollution is over anything.
 *
 * <p>Nothing here loads unless Jade is installed - Jade finds this class by its annotation and
 * only then touches it, so the dependency stays {@code compileOnly} and the mod runs standalone
 * without it.
 */
@WailaPlugin
public class NauvisMilitaryJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(TurretReadout.INSTANCE, GunTurretBlock.class);
        registration.registerBlockDataProvider(PollutionReadout.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TurretReadout.Client.INSTANCE, GunTurretBlock.class);
        registration.registerBlockComponent(PollutionReadout.Client.INSTANCE, Block.class);
    }
}
