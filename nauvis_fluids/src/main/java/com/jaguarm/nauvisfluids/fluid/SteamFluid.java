package com.jaguarm.nauvisfluids.fluid;

import com.jaguarm.nauvisfluids.registry.ModFluids;

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
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Steam: a fluid that exists only inside machines and pipes, and never in the world.
 *
 * <p>PLAN.md puts fluids in milestone 4 and takes barrels rather than pipes even then. Steam is
 * the exception because it is not really a fluid subsystem - it is the one substance a boiler
 * makes and a steam engine drinks, and {@code data/mapping.json} has always called it
 * {@code nauvis_fluids:steam}. Making it a real {@link Fluid} rather than an int on the boiler
 * costs almost nothing and buys the thing that matters: a boiler, a pipe and an engine can talk
 * through {@code Capabilities.Fluid.BLOCK}, which is NeoForge's, so {@code nauvis_power} and
 * {@code nauvis_fluids} never have to know about each other. That is non-negotiable #3, and it is
 * the same trick FE plays for the electric network.
 *
 * <p><b>It has no block, no bucket and no source.</b> Every method below that describes fluid in
 * the world is answered as if there were none, because there never is: you cannot place steam,
 * scoop it, swim in it or stand on it. A {@code BaseFlowingFluid} would demand a block and a
 * bucket item and give nothing back for them.
 */
public class SteamFluid extends Fluid {

    @Override
    public FluidType getFluidType() {
        return ModFluids.STEAM_TYPE.get();
    }

    /** No bucket. Steam is moved by pipes or not at all. */
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
