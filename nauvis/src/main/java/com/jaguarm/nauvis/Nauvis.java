package com.jaguarm.nauvis;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The pack mod: policy, not machinery.
 *
 * <p>Everything that belongs to Project Nauvis as a whole rather than to one subsystem lives
 * here — replacing vanilla progression so the Factorio tree is the only road forward, the raw
 * resources the other mods build on, and terrain. The subsystem mods stay usable on their own,
 * which is why none of this can live in them.
 */
@Mod(Nauvis.MODID)
public class Nauvis {

    public static final String MODID = "nauvis";

    public Nauvis(IEventBus modEventBus, ModContainer modContainer) {
        ModContent.register(modEventBus);
        modEventBus.addListener(StandInStacks::modify);
        modEventBus.addListener(ModPacks::addPackFinders);
        NauvisGameTests.register(modEventBus);
    }
}
