package com.jaguarm.nauvismining.machine.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sound and particle feedback that makes a running miner legible from outside.
 *
 * <p>A machine that changes nothing visible until a block silently disappears reads as
 * broken. Everything here is derived from the block being worked rather than from fixed
 * assets, so a miner in deepslate sounds and looks different from one in gravel without
 * shipping a single sound file, and modded blocks are covered for free.
 *
 * <p>Pitch is dropped well below normal throughout. The same samples a player hears when
 * swinging a pickaxe read as heavy machinery an octave down, which is the intended
 * character: a big slow drill rather than an invisible person mining very fast.
 */
public final class MinerFeedback {

    /** Ticks between chug sounds while working a block. */
    private static final int CHUG_INTERVAL_TICKS = 10;

    /** Well below vanilla, to read as machinery rather than a hand tool. */
    private static final float CHUG_PITCH = 0.45F;
    private static final float BREAK_PITCH = 0.55F;

    private static final float CHUG_VOLUME = 0.22F;
    private static final float BREAK_VOLUME = 0.5F;

    private MinerFeedback() {}

    /**
     * The rhythmic knock of the drill working. Called every tick while mining; rate limited
     * here rather than by the caller so the cadence stays constant regardless of how fast
     * the machine is set to run.
     */
    public static void chug(ServerLevel level, BlockPos machinePos, BlockPos target, BlockState state, int elapsedTicks) {
        if (elapsedTicks % CHUG_INTERVAL_TICKS != 0) {
            return;
        }
        SoundType sound = state.getSoundType();
        level.playSound(null, machinePos, sound.getHitSound(), SoundSource.BLOCKS, CHUG_VOLUME, CHUG_PITCH);
        spitDust(level, target, state, 3);
    }

    /** The heavier thud of a block giving way, plus a burst of its debris. */
    public static void broke(ServerLevel level, BlockPos machinePos, BlockPos target, BlockState state) {
        SoundType sound = state.getSoundType();
        level.playSound(null, machinePos, sound.getBreakSound(), SoundSource.BLOCKS, BREAK_VOLUME, BREAK_PITCH);
        spitDust(level, target, state, 12);
    }

    /**
     * Debris from the block being worked, thrown upward out of the shaft.
     *
     * <p>Sent from the server so it appears for everyone watching, and because the client
     * has no idea which block the machine is currently on.
     */
    private static void spitDust(ServerLevel level, BlockPos target, BlockState state, int count) {
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                target.getX() + 0.5,
                target.getY() + 0.9,
                target.getZ() + 0.5,
                count,
                0.25, 0.1, 0.25,
                0.02);
    }
}
