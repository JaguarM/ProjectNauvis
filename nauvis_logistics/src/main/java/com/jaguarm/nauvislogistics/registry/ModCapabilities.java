package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * What automation sees when it looks at this mod's blocks.
 *
 * <p>The inserter publishes its fuel slot, so a hopper or another inserter can keep it stocked -
 * insert-only, because a base built on burner inserters feeding burner inserters is a real
 * Factorio pattern but two of them passing the same lump of coal back and forth is not.
 *
 * <p>The iron chest publishes its whole inventory. NeoForge wraps vanilla's containers
 * automatically but only for a hard-coded list of vanilla block entity types, so a modded
 * Container has to say so itself - one line, and without it an inserter aimed at an iron chest
 * would find nothing there at all.
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

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.IRON_CHEST.get(),
                (chest, side) -> VanillaContainerWrapper.of(chest));
    }
}
