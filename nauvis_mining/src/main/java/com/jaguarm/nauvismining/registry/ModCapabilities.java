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
 *
 * <p>The item view is never the drill's own inventory: it accepts fuel and a pickaxe and lets the
 * ore out, so a hopper under a drill takes the ore rather than the pickaxe. The energy view is
 * insert only, published by the electric drill and not by the burner, which no pole should think
 * it supplies.
 *
 * <h2>Registered against the block, not the block entity</h2>
 *
 * <p>An electric drill is nine blocks with one block entity, sitting in the middle of the nine.
 * {@code registerBlockEntity} would publish at that middle block only - and the middle of a drill
 * is the one place nothing can be built next to. So this resolves the anchor from whichever block
 * was asked and answers for all of them: a cable run along the edge of a drill field powers the
 * drills it touches, and an inserter beside any edge of a drill can feed it coal.
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
