package com.jaguarm.nauvisfluids.offshorepump;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** The offshore pump in hand: a click on a lake puts the pump on the lake, not under it. */
public class OffshorePumpItem extends BlockItem {

    public OffshorePumpItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockPos at = context.getClickedPos();
        BlockPos lifted = OffshorePumpBlock.afloat(context.getLevel(), at);
        return lifted.equals(at) ? context : BlockPlaceContext.at(context, lifted, Direction.UP);
    }
}
