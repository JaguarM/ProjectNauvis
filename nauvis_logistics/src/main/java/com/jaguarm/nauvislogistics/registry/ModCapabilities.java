package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlockEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** What automation sees when it looks at this mod's blocks. */
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

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.STEEL_CHEST.get(),
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
