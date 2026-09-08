package com.jaguarm.nauvislib.health;

import com.jaguarm.nauvislib.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Hurting and mending the things a base is made of. */
public final class Health {

    private Health() {}

    /** Health per point of hardness, for a block that has no number of its own. */
    public static final float PER_HARDNESS = 100;

    /** The machine at this position that keeps its own health, or null for a block that does not. */
    public static @Nullable Damageable machineAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Multiblock.MachineBlock machine)) {
            return null;
        }
        BlockPos anchor = Multiblock.anchorPos(machine, state, pos);
        return level.isLoaded(anchor) && level.getBlockEntity(anchor) instanceof Damageable damageable
                ? damageable
                : null;
    }

    /** Whether anything here can be hurt at all: something stands there and it is not unbreakable. */
    public static boolean canHurt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && (machineAt(level, pos) != null || state.getDestroySpeed(level, pos) >= 0);
    }

    /** How much the thing here can take in all, or zero for air and the unbreakable. */
    public static float maxHealth(ServerLevel level, BlockPos pos) {
        Damageable machine = machineAt(level, pos);
        if (machine != null) {
            return machine.maxHealth();
        }
        BlockState state = level.getBlockState(pos);
        float hardness = state.isAir() ? -1 : state.getDestroySpeed(level, pos);
        return hardness < 0 ? 0 : hardness * PER_HARDNESS;
    }

    /** How much the thing here has left. */
    public static float health(ServerLevel level, BlockPos pos) {
        Damageable machine = machineAt(level, pos);
        if (machine != null) {
            return machine.health();
        }
        float max = maxHealth(level, pos);
        if (max <= 0) {
            return 0;
        }
        return Math.max(0, max - BlockHealth.get(level).damage(pos, level.getBlockState(pos).getBlock()));
    }

    /**
     * Takes this much off what stands here, and takes it down when nothing is left.
     *
     * @return whether it was destroyed by this
     */
    public static boolean hurt(ServerLevel level, BlockPos pos, float amount) {
        if (amount <= 0 || !canHurt(level, pos)) {
            return false;
        }
        Damageable machine = machineAt(level, pos);
        if (machine != null) {
            machine.setHealth(machine.health() - amount);
            if (machine.health() > 0) {
                markMachineChanged(level, pos);
                return false;
            }
            destroy(level, pos);
            return true;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        BlockHealth wounds = BlockHealth.get(level);
        float damage = wounds.damage(pos, block) + amount;
        if (damage < maxHealth(level, pos)) {
            wounds.setDamage(pos, block, damage);
            return false;
        }
        wounds.clear(pos);
        destroy(level, pos);
        return true;
    }

    /**
     * Mends what stands here by up to this much.
     *
     * @return how much was mended, which is nothing for something already whole
     */
    public static float repair(ServerLevel level, BlockPos pos, float amount) {
        if (amount <= 0) {
            return 0;
        }
        Damageable machine = machineAt(level, pos);
        if (machine != null) {
            float before = machine.health();
            machine.setHealth(before + amount);
            float mended = machine.health() - before;
            if (mended > 0) {
                markMachineChanged(level, pos);
            }
            return mended;
        }
        Block block = level.getBlockState(pos).getBlock();
        BlockHealth wounds = BlockHealth.get(level);
        float damage = wounds.damage(pos, block);
        if (damage <= 0) {
            return 0;
        }
        float mended = Math.min(amount, damage);
        wounds.setDamage(pos, block, damage - mended);
        return mended;
    }

    /**
     * Takes down what stands here with no drops: a machine through its anchor, so the teardown
     * rule takes the rest of it and what it held spills; anything else as itself.
     */
    private static void destroy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos target = state.getBlock() instanceof Multiblock.MachineBlock machine
                ? Multiblock.anchorPos(machine, state, pos)
                : pos;
        level.destroyBlock(target, false);
    }

    private static void markMachineChanged(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof Multiblock.MachineBlock machine) {
            BlockPos anchor = Multiblock.anchorPos(machine, state, pos);
            if (level.getBlockEntity(anchor) != null) {
                level.getBlockEntity(anchor).setChanged();
            }
        }
    }
}
