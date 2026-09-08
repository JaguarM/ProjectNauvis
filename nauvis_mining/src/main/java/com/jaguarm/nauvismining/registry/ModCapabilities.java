package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/**
 * What automation and the grid see when they look at a drill, from any of the blocks it is made
 * of.
 */
public final class ModCapabilities {

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (var drill : ModBlocks.DRILLS.values()) {
            MinerBlock block = drill.get();
            anywhere(event, Capabilities.Item.BLOCK, MinerBlockEntity::automationView, block);
            if (block.tier().isElectric()) {
                anywhere(event, Capabilities.Energy.BLOCK, MinerBlockEntity::gridView, block);
            }
        }
    }

    private static <T, C extends @Nullable Object> void anywhere(RegisterCapabilitiesEvent event,
            BlockCapability<T, C> capability, Function<MinerBlockEntity, @Nullable T> view, MinerBlock block) {
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never ask for a block entity in an unloaded chunk: asking loads it, and a drill at
            // the edge of the loaded world would drag its neighbour in.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            BlockEntity found = level.getBlockEntity(anchor);
            return found instanceof MinerBlockEntity miner ? view.apply(miner) : null;
        }, block);
    }
}
