package com.jaguarm.nauvisfluids.pipe;

import com.jaguarm.nauvisfluids.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A pipe's membership of a run, and nothing else. */
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
