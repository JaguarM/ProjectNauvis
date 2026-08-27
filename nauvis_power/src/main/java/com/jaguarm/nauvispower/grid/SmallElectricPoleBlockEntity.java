package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pole's membership of a network, and nothing else.
 *
 * <p>It stores no data, saves no data and never ticks. It exists for three lifecycle hooks that a
 * block alone does not get: {@code onLoad} when its chunk arrives, {@code setRemoved} when it is
 * broken, and {@code onChunkUnloaded} when its chunk leaves. Those three are the whole of a
 * pole's behaviour.
 *
 * <p>Nothing about the graph is written to disk. It falls out of where the poles are, so saving
 * it would be a cache, and a cache spanning chunk loads is where the bugs would live.
 */
public class SmallElectricPoleBlockEntity extends BlockEntity {

    public SmallElectricPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_ELECTRIC_POLE.get(), pos, state);
    }

    /** Placement and chunk load arrive here the same way, and both mean the same thing. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            PowerNetworkManager.of(serverLevel).polePlaced(worldPosition);
        }
    }

    /**
     * Broken, replaced, or the chunk being cleared.
     *
     * <p>Leaving is the expensive half of a pole's life - it can split one network into two - but
     * it only happens when a pole actually goes, which is rare next to twenty ticks a second.
     */
    @Override
    public void setRemoved() {
        leave();
        super.setRemoved();
    }

    /** Fires just before the chunk goes. Deregistering twice is harmless; missing it is not. */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        leave();
    }

    private void leave() {
        if (level instanceof ServerLevel serverLevel) {
            PowerNetworkManager.of(serverLevel).poleRemoved(worldPosition);
        }
    }
}
