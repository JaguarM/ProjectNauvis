package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * An inserter's fuel slot, so a hopper or another inserter can keep it stocked.
 *
 * <p>Insert-only. A base built on burner inserters feeding burner inserters is a real Factorio
 * pattern; two of them passing the same lump of coal back and forth is not.
 */
@EventBusSubscriber(modid = NauvisLogistics.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.INSERTER.get(),
                (inserter, side) -> inserter.fuelAccess());
    }
}
