package com.jaguarm.nauvismachines.registry;

import com.jaguarm.nauvismachines.NauvisMachines;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What automation sees when it looks at a machine.
 *
 * <p>The view published here is not the machine's own inventory: it accepts insertions into
 * the input slots only and allows extraction from the output slots only. Without that, a hopper
 * under an assembler would drain the ingredients it was just fed.
 */
@EventBusSubscriber(modid = NauvisMachines.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.ASSEMBLER.get(),
                (assembler, side) -> assembler.automationView());

        // Insert only, and NeoForge's capability rather than ours - so a power pole from
        // nauvis_power fills an assembler without either mod knowing the other exists, and so
        // would a cable from any other mod. That is the whole reason PLAN.md chose FE.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.ASSEMBLER.get(),
                (assembler, side) -> assembler.gridView());
    }
}
