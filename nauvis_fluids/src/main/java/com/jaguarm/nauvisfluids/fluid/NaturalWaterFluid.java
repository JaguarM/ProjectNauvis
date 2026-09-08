package com.jaguarm.nauvisfluids.fluid;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** The water the world was made with: Factorio's water tile, as a Minecraft fluid. */
public abstract class NaturalWaterFluid extends BaseFlowingFluid {

    protected NaturalWaterFluid(Properties properties) {
        super(properties);
    }

    /**
     * Vanilla water's rule, by the tag rather than by identity: a fluid landing on this one from
     * above replaces it unless it is water of some kind.
     *
     * <p>The base class answers "unless it is <em>this</em> fluid", which would let a bucket's
     * water poured off a cliff bore a column through a lake, source by source, on its way to the
     * bottom. Vanilla water falling on vanilla water simply stops, and so does anything in the
     * water tag falling on this.
     */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos,
            Fluid other, Direction direction) {
        return direction == Direction.DOWN && !other.is(FluidTags.WATER);
    }

    /** Water's own bubbles under the surface, and its ambient sound where it flows. */
    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (!state.isSource() && !state.getValue(FALLING)) {
            if (random.nextInt(64) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                        random.nextFloat() * 0.25F + 0.75F, random.nextFloat() + 0.5F, false);
            }
        } else if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.UNDERWATER,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public @Nullable ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_WATER;
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier) {
        effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
    }

    /** The still water of a lake or the sea. The only kind a bucket lifts or a pump draws. */
    public static class Source extends NaturalWaterFluid {

        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    /** Water on the move, off the edge of a lake. Never a source, whatever is around it. */
    public static class Flowing extends NaturalWaterFluid {

        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
