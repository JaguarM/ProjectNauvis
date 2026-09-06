package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvismining.machine.miner.MinerBlock;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;
import com.jaguarm.nauvislib.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the miner's energy buffer to cables, from any of the blocks it is made of.
 *
 * <p>Only the insert-only view is published, and only on electric tiers: a burner miner
 * returns null and so looks like a plain block to an energy network, which is what stops
 * cables trying to feed a machine that cannot use power.
 *
 * <h2>Registered against the block, not the block entity</h2>
 *
 * <p>An electric drill is nine blocks with one block entity, sitting in the middle of the nine.
 * {@code registerBlockEntity} would publish the buffer at that middle block only - and the middle
 * of a drill is the one place nothing can be built next to, because the drill is all round it. So
 * this resolves the anchor from whichever block was asked and answers for all of them.
 *
 * <p>That is also the behaviour a player expects: a cable run along the edge of a drill field
 * powers the drills it touches, rather than the drills whose exact middles it happens to reach.
 */
public final class ModCapabilities {

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (var drill : ModBlocks.DRILLS.values()) {
            MinerBlock block = drill.get();
            event.registerBlock(Capabilities.Energy.BLOCK,
                    (level, pos, state, blockEntity, side) -> {
                        BlockPos anchor = Multiblock.anchorPos(block, state, pos);
                        // Never ask for a block entity in an unloaded chunk: asking loads it, and
                        // a drill at the edge of the loaded world would drag its neighbour in.
                        if (!level.isLoaded(anchor)) {
                            return null;
                        }
                        return level.getBlockEntity(anchor) instanceof MinerBlockEntity miner
                                ? view(miner)
                                : null;
                    }, block);
        }
    }

    private static net.neoforged.neoforge.transfer.energy.@Nullable EnergyHandler view(
            MinerBlockEntity miner) {
        return miner.cableView();
    }
}
