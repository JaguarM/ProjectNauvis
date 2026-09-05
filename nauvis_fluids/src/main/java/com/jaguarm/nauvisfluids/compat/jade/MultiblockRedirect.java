package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.multiblock.Multiblock;

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

/**
 * Points Jade at the machine rather than at the block of it you happen to be looking at.
 *
 * <p>A machine here is several blocks and <b>one</b> block entity, and everything worth saying
 * about it lives on that block entity. Jade asks the block you are pointing at, so without this a
 * pumpjack would show its energy and its oil on the middle block of ten and nothing on the other
 * nine - and the middle of a pumpjack is under the pump.
 *
 * <p>Jade runs its ray-trace callbacks and only then decides what it is looking at: the accessor
 * returned here is the one every provider sees, including Jade's own energy bar, and the one whose
 * position is sent to the server for {@code appendServerData}. Handing back an accessor rebuilt at
 * the anchor makes every block of a machine say exactly what its anchor says.
 *
 * <p>Duplicated into every mod with machines in it, like the {@code multiblock} package it leans
 * on. A subsystem mod may not depend on another, and this is thirty lines.
 */
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

        // Never ask for a block entity in an unloaded chunk: asking loads it.
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
