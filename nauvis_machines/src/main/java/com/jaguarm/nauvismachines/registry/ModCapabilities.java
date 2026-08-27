package com.jaguarm.nauvismachines.registry;

import java.util.function.Function;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What automation sees when it looks at a machine, from any of the blocks it is made of.
 *
 * <p>The view published here is not the machine's own inventory: it accepts insertions into
 * the input slots only and allows extraction from the output slots only. Without that, a hopper
 * under an assembler would drain the ingredients it was just fed.
 *
 * <h2>Why this is {@code registerBlock} and not {@code registerBlockEntity}</h2>
 *
 * <p>An assembler is ten blocks and one block entity. {@code registerBlockEntity} would publish
 * the inventory at the middle of the machine only - a block a player can barely reach and an
 * inserter can never stand next to, since it is surrounded by the machine's own deck. Registering
 * against the <em>block</em> lets any cell answer, resolving the anchor by arithmetic first.
 *
 * <p>The result is the Factorio behaviour and the reason the footprint was worth having: an
 * assembler is fed or emptied anywhere along its twelve perimeter faces, and on top of its deck,
 * rather than at one privileged spot.
 *
 * <p>Nothing invalidates caches by hand here. NeoForge invalidates a position when its block
 * changes, and every cell of a machine is placed and removed as its own block change, so each
 * cell's entry goes stale exactly when it stops being true.
 */
@EventBusSubscriber(modid = NauvisMachines.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        assembler(event, Capabilities.Item.BLOCK, AssemblerBlockEntity::automationView);

        // Insert only, and NeoForge's capability rather than ours - so a power pole from
        // nauvis_power fills an assembler without either mod knowing the other exists, and so
        // would a cable from any other mod. That is the whole reason PLAN.md chose FE.
        assembler(event, Capabilities.Energy.BLOCK, AssemblerBlockEntity::gridView);
    }

    private static <T, C extends @Nullable Object> void assembler(RegisterCapabilitiesEvent event,
            BlockCapability<T, C> capability,
            Function<AssemblerBlockEntity, T> view) {
        AssemblerBlock block = ModBlocks.ASSEMBLING_MACHINE_1.get();
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never getBlockEntity on an unloaded chunk - asking loads it, and a machine at the
            // edge of the loaded world would drag its neighbour in. See docs/NEXT.md.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            return level.getBlockEntity(anchor) instanceof AssemblerBlockEntity assembler
                    ? view.apply(assembler)
                    : null;
        }, block);
    }
}
