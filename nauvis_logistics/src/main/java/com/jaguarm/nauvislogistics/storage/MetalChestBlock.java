package com.jaguarm.nauvislogistics.storage;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block half of a chest: vanilla's {@link ChestBlock}, with a bigger box inside it.
 *
 * <p>This used to be a {@code BaseEntityBlock} drawing a cube, on the grounds that vanilla's chest
 * is welded to its double-chest pairing and its animated lid. That was true when it was written
 * and is not true now - 26.2's copper chests are {@code ChestBlock} subclasses, so the class takes
 * a block entity type and a pair of sounds as constructor arguments and everything a chest is
 * comes with it: the facing, the model, <em>the lid that opens</em>, the openers counter, the sound
 * on both edges of it, and the cat that sits on top and stops you. All of that was previously
 * absent and none of it was worth writing again.
 *
 * <h2>No double chests</h2>
 *
 * <p>{@link #chestCanConnectTo} answers false, so both tiers are always {@code ChestType.SINGLE}.
 * That is not squeamishness about the model - it is that pairing combines the two containers, and
 * two steel chests would want a hundred-and-eight-slot screen that does not exist. Factorio has no
 * double chest either, so nothing is lost but a Minecraft reflex, and the reflex is answered by
 * the chests simply being bigger.
 *
 * <p>A tier is two numbers - how many rows, and which block entity type saves them - so the tiers
 * are subclasses of this rather than copies of it. The rows have to be a constant on a subclass
 * and not a constructor argument, because {@code createBlockStateDefinition} runs inside
 * {@link net.minecraft.world.level.block.Block}'s constructor and would read a field that does not
 * exist yet; see PITFALLS.md.
 *
 * <p>Vanilla registers a {@code BlockEntityTicker} for these, which the rest of this pack never
 * does. It is client-only - {@code ChestBlock#getTicker} returns null on a server - and all it
 * does is advance the lid. Nothing on the server ticks.
 */
public abstract class MetalChestBlock extends ChestBlock {

    protected MetalChestBlock(Supplier<BlockEntityType<? extends ChestBlockEntity>> type,
            Properties properties) {
        // The copper chest's sounds rather than the wooden chest's, because these are metal. Both
        // are vanilla sound events; a mod that shipped its own would be shipping audio to say the
        // same thing.
        super(type, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, properties);
    }

    /** Rows of nine on the screen, which is also the container size divided by nine. */
    public abstract int rows();

    /** Never. See the class comment: a pair would want a screen that does not exist. */
    @Override
    public boolean chestCanConnectTo(BlockState state) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalChestBlockEntity(pos, state);
    }
}
