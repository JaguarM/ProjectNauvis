package com.jaguarm.nauvisfluids.pipe;

import com.jaguarm.nauvisfluids.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pipe's membership of a run, and nothing else.
 *
 * <p>It stores no data, saves no data and never ticks - the run it belongs to holds the fluid and
 * does the work. This exists for three lifecycle hooks a block alone does not get: {@code onLoad}
 * when its chunk arrives, {@code setRemoved} when it is broken, and {@code onChunkUnloaded} when
 * its chunk leaves. The small electric pole is the same shape for the same reason.
 *
 * <p>Nothing about the graph is written to disk. It falls out of where the pipes are.
 */
public class PipeBlockEntity extends BlockEntity {

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            FluidNetworkManager.of(serverLevel).pipePlaced(worldPosition);
        }
    }

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
            FluidNetworkManager.of(serverLevel).pipeRemoved(worldPosition);
        }
    }
}
