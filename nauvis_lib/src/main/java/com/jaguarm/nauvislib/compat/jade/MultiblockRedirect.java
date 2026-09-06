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

/**
 * Points Jade at the machine rather than at the block of it you happen to be looking at.
 *
 * <h2>The problem, which every readout in the pack had</h2>
 *
 * <p>A machine here is several blocks and <b>one</b> block entity, and everything worth saying
 * about it lives on that block entity: how much power it is holding, what is in it, whether it is
 * running. Jade asks the block you are pointing at, so an electric mining drill showed its energy
 * on the middle block of nine and nothing at all on the other eight - and the middle of a drill is
 * the one block you are least likely to be looking at, because the machine is all round it.
 *
 * <p>This is not only about the readouts the mods write. <b>Jade's own universal providers</b> -
 * the energy bar, the item contents - are registered against {@code BlockEntity}, so they never
 * fired on a block that has none. Fixing this one provider at a time would have fixed our lines
 * and left Jade's own showing on one block in nine.
 *
 * <h2>So the accessor is replaced, not the providers</h2>
 *
 * <p>Jade runs its ray-trace callbacks and only then decides what it is looking at: the accessor
 * returned here is the one every provider sees, the one whose position is sent to the server for
 * {@code appendServerData}, and the one the header names. Handing back an accessor rebuilt at the
 * anchor makes every block of a machine say exactly what its anchor says, including the parts of
 * the tooltip nobody here wrote.
 *
 * <p>The block and its state are the anchor's, and the hit face is kept as it was - a provider
 * that asks a capability which side it was approached from should still get the side the player is
 * actually looking at.
 *
 * <p>Registered once, by {@link NauvisLibJadePlugin}, for every {@code MachineBlock} in every mod.
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
