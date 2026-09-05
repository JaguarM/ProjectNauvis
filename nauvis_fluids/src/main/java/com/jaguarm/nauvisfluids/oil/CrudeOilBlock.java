package com.jaguarm.nauvisfluids.oil;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An oil well: the block a pumpjack stands on.
 *
 * <p>Factorio's {@code crude-oil} is a <em>resource entity</em>, not a tile of liquid - you cannot
 * pick it up, walk into it or move it, and the only thing that does anything with it is a pumpjack
 * placed over it. This block is the same idea in Minecraft's terms. It is a plain solid block that
 * happens to be the ground, so it needs no fluid physics to be unmovable: it is unbreakable like
 * bedrock, refuses pistons, drops nothing, and has no survival item. Buckets, water, lava and
 * endermen do nothing to a stone-like block, so nothing has to be written to stop them.
 *
 * <p>The oil that flows in pipes is {@code nauvis_fluids:crude_oil} the <em>fluid</em>, which this
 * block never contains. What it holds is a number - how much is left - on
 * {@link CrudeOilBlockEntity}, which is Factorio's resource amount exactly.
 *
 * <p>Placed by worldgen ({@link CrudeOilFieldFeature}) in fields of a few wells, and by nobody
 * else outside creative. The creative item exists for the same reason Factorio's map editor can
 * place one: to build a test world.
 */
public class CrudeOilBlock extends BaseEntityBlock {

    public static final MapCodec<CrudeOilBlock> CODEC = simpleCodec(CrudeOilBlock::new);

    public CrudeOilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrudeOilBlockEntity(pos, state);
    }

    // No ticker, deliberately. A well does nothing until a pumpjack asks it to.
}
