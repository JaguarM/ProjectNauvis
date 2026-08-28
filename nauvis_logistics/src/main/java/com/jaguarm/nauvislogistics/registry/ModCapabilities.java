package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlockEntity;

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
                ModBlockEntities.BURNER_INSERTER.get(),
                (inserter, side) -> inserter.fuelAccess());

        // The electric ones have no slot to fill and no charge to give back - only somewhere for
        // a pole to put energy. NeoForge's capability, not ours, so any grid can drive them. One
        // registration covers the basic arm and the long-handed one, because a capability is
        // registered against the block entity type and they share it.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.ELECTRIC_INSERTER.get(),
                (inserter, side) -> inserter.gridView());

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.IRON_CHEST.get(),
                (chest, side) -> VanillaContainerWrapper.of(chest));

        // A belt, from whichever side is asking. The side is not decoration: it decides which of
        // the two lanes the asker meets, because a Factorio inserter reaches across to the far
        // lane. See BeltAccess.
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.TRANSPORT_BELT.get(),
                BeltBlockEntity::access);
    }
}
