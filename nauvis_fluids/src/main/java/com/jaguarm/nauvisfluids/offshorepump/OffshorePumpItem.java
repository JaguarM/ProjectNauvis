package com.jaguarm.nauvisfluids.offshorepump;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The offshore pump in hand: a click on a lake puts the pump on the lake, not under it.
 *
 * <p>A block in hand looks through water, so pointing at a lake and clicking lands on the lake
 * bed, and vanilla would place the block in the water just above it - on the bottom, for a pump.
 * {@link #updatePlacementContext} is the hook a block item has for moving where it will place,
 * and this one lifts the click to the air just over the water's surface, where
 * {@link OffshorePumpBlock#getStateForPlacement} turns the pump to the water beside it. A click
 * on the shore is left exactly where it was.
 */
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
