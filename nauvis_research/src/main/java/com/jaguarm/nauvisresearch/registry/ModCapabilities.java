package com.jaguarm.nauvisresearch.registry;

import com.jaguarm.nauvislib.transfer.MachineAccess;
import java.util.function.Function;

import com.jaguarm.nauvisresearch.lab.LabBlock;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisresearch.NauvisResearch;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What automation sees when it looks at a lab, from any of the ten blocks it is made of.
 *
 * <p>Science packs go in and nothing comes back out - see {@code MachineAccess}. Without that a hopper
 * under a lab would pull the packs straight back out of it.
 *
 * <p>Registered against the <em>block</em> rather than the block entity, because a lab is ten
 * blocks and one block entity, and that block entity sits in the middle where nothing can stand
 * next to it. Any cell answers, resolving the anchor by arithmetic first. The result is the
 * Factorio behaviour: a lab is fed anywhere along its twelve perimeter faces or on its roof, and a
 * pole supplies it if its area covers any part of it.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        lab(event, Capabilities.Item.BLOCK, LabBlockEntity::automationView);

        // NeoForge's capability rather than ours, so a pole from nauvis_power fills a lab without
        // either mod knowing the other exists - and so would a cable from any other mod.
        lab(event, Capabilities.Energy.BLOCK, LabBlockEntity::gridView);
    }

    private static <T, C extends @Nullable Object> void lab(RegisterCapabilitiesEvent event,
            BlockCapability<T, C> capability, Function<LabBlockEntity, T> view) {
        LabBlock block = ModBlocks.LAB.get();
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never getBlockEntity on an unloaded chunk - asking loads it, and a machine at the
            // edge of the loaded world would drag its neighbour in.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            return level.getBlockEntity(anchor) instanceof LabBlockEntity lab
                    ? view.apply(lab)
                    : null;
        }, block);
    }
}
