package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What the rest of the world sees: the boiler's fuel slot, and the engine's charge.
 *
 * <p>The engine publishes {@code Capabilities.Energy.BLOCK}, which is NeoForge's, not ours - so
 * any cable or machine from any mod can draw from a steam engine without knowing what one is.
 * That is the whole reason PLAN.md chose FE over a first-party grid.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.BOILER.get(),
                (boiler, side) -> boiler.fuelAccess());

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.STEAM_ENGINE.get(),
                (engine, side) -> engine.cableView());
    }
}
