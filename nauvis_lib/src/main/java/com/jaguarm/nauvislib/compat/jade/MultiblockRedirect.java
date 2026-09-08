package com.jaguarm.nauvislib.compat.jade;

import com.jaguarm.nauvislib.multiblock.Multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeRayTraceCallback;

/** Points Jade at the machine rather than at the block of it you happen to be looking at. */
public class MultiblockRedirect implements JadeRayTraceCallback {

    private final IWailaClientRegistration registration;

    public MultiblockRedirect(IWailaClientRegistration registration) {
        this.registration = registration;
    }

    @Override
    public Accessor<?> onRayTrace(HitResult hitResult, Accessor<?> accessor, Accessor<?> original) {
        if (!(accessor instanceof BlockAccessor block)) {
            return accessor;
        }
        BlockState state = block.getBlockState();
        if (!(state.getBlock() instanceof Multiblock.MachineBlock machine)) {
            return accessor;
        }

        BlockPos pos = block.getPosition();
        BlockPos anchor = Multiblock.anchorPos(machine, state, pos);
        if (anchor.equals(pos)) {
            return accessor;
        }

        // Never ask for a block entity in an unloaded chunk: asking loads it. The anchor of a
        // machine you are looking at is a block or two away and all but always loaded, so this is
        // for the moment a chunk boundary falls through the middle of one.
        Level level = block.getLevel();
        if (!level.isLoaded(anchor)) {
            return accessor;
        }

        return registration.blockAccessor()
                .from(block)
                .hit(new BlockHitResult(
                        Vec3.atCenterOf(anchor), block.getSide(), anchor, false))
                .blockState(level.getBlockState(anchor))
                .blockEntity(() -> level.getBlockEntity(anchor))
                .build();
    }
}
