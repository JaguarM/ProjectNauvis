package com.jaguarm.nauvismining.machine.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sound and particle feedback that makes a running drill legible from outside.
 *
 * <p>The ore a drill is working is underground and out of sight, so nothing at the ore would be
 * seen. The drill shows its work where a Factorio drill does: at the machine, with a knock every
 * half second and a puff of the ore's dust out of the head - or the chimney - when an ore comes
 * up. Pitch is dropped well below normal throughout, so the same samples a player hears when
 * swinging a pickaxe read as heavy machinery an octave down.
 */
public final class MinerFeedback {

    /** Ticks between knocks while working. */
    private static final int CHUG_INTERVAL_TICKS = 10;

    /** Well below vanilla, to read as machinery rather than a hand tool. */
    private static final float CHUG_PITCH = 0.45F;
    private static final float BREAK_PITCH = 0.55F;

    private static final float CHUG_VOLUME = 0.22F;
    private static final float BREAK_VOLUME = 0.5F;

    private MinerFeedback() {}

    /**
     * The rhythmic knock of the drill working. Called every tick while mining; rate limited here
     * rather than by the caller so the cadence stays constant however fast the drill runs.
     */
    public static void chug(ServerLevel level, BlockPos machinePos, BlockPos plume, int elapsedTicks) {
        if (elapsedTicks % CHUG_INTERVAL_TICKS != 0) {
            return;
        }
        level.playSound(null, machinePos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, CHUG_VOLUME, CHUG_PITCH);
        spitDust(level, plume, Blocks.STONE.defaultBlockState(), 2);
    }

    /** The heavier thud of an ore coming up, and a burst of its dust out of the head. */
    public static void broke(ServerLevel level, BlockPos machinePos, BlockPos plume, BlockState ore) {
        SoundType sound = ore.getSoundType();
        level.playSound(null, machinePos, sound.getBreakSound(), SoundSource.BLOCKS, BREAK_VOLUME, BREAK_PITCH);
        spitDust(level, plume, ore, 12);
    }

    /**
     * Dust thrown up out of the drill.
     *
     * <p>Sent from the server so it appears for everyone watching, and because the client has no
     * idea which ore the machine is on.
     */
    private static void spitDust(ServerLevel level, BlockPos plume, BlockState state, int count) {
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                plume.getX() + 0.5,
                plume.getY() + 0.1,
                plume.getZ() + 0.5,
                count,
                0.2, 0.1, 0.2,
                0.02);
    }
}
