package com.jaguarm.nauvisrocket.registry;

import java.util.function.Function;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlock;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What automation sees when it looks at a silo, from any of the hundred and thirty-five blocks
 * it is made of: rocket part ingredients and a satellite in, space science out, and power in.
 *
 * <p>Registered against the block rather than the block entity, as every machine here is: the
 * block entity sits in the middle of a nine-by-nine pad where nothing can stand next to it, and
 * any cell answers by finding the anchor with arithmetic first. The silo is fed and emptied
 * anywhere along its thirty-six perimeter faces, which is Factorio's arrangement.
 */
@EventBusSubscriber(modid = NauvisRocket.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        silo(event, Capabilities.Item.BLOCK, RocketSiloBlockEntity::automationView);
        // NeoForge's capability rather than ours, so a pole from nauvis_power fills a silo
        // without either mod knowing the other exists.
        silo(event, Capabilities.Energy.BLOCK, RocketSiloBlockEntity::gridView);
    }

    private static <T, C extends @Nullable Object> void silo(RegisterCapabilitiesEvent event,
            BlockCapability<T, C> capability, Function<RocketSiloBlockEntity, T> view) {
        RocketSiloBlock block = ModBlocks.ROCKET_SILO.get();
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never getBlockEntity on an unloaded chunk - asking loads it, and a machine at the
            // edge of the loaded world would drag its neighbour in.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            return level.getBlockEntity(anchor) instanceof RocketSiloBlockEntity silo
                    ? view.apply(silo)
                    : null;
        }, block);
    }
}
