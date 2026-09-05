package com.jaguarm.nauvisfluids.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A fluid that exists only inside machines and pipes, and never in the world.
 *
 * <p>Steam was the first and crude oil is the second, and the two share everything but their
 * {@code FluidType}. Neither can be placed, scooped, swum in or stood on, so every method below
 * that describes fluid in the world is answered as if there were none - because there never is. A
 * {@code BaseFlowingFluid} would demand a block and a bucket item and give nothing back for them.
 *
 * <p>Being a real {@link Fluid} rather than an int on a machine is what matters: it is what lets
 * a pumpjack, a pipe and a refinery from different jars agree on what is in them through
 * {@code Capabilities.Fluid.BLOCK}, which is NeoForge's. That is non-negotiable #3, and it is
 * the same trick FE plays for the electric network.
 */
public abstract class ContainedFluid extends Fluid {

    /** No bucket. These fluids are moved by pipes or not at all. */
    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    // --- everything below describes fluid in the world, of which there is never any -----------

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos,
            Fluid other, Direction direction) {
        return true;
    }

    @Override
    protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        return Vec3.ZERO;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 5;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return 0.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return false;
    }

    @Override
    public int getAmount(FluidState state) {
        return 0;
    }

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
