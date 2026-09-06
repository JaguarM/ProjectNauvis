package com.jaguarm.nauvisfluids.registry;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlockEntity;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpShape;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackShape;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * What the rest of the world sees of a pumpjack and an offshore pump: an electricity buffer, and
 * the fluid each one makes.
 *
 * <p>Electricity through {@code Capabilities.Energy.BLOCK}, so a pole from {@code nauvis_power}
 * fills it without either mod compiling against the other; fluid through
 * {@code Capabilities.Fluid.BLOCK}, so a pipe carries it away on the same terms. Both are
 * NeoForge's, which is the whole reason PLAN.md chose FE over a first-party grid.
 *
 * <h2>Registered against the block, not the block entity</h2>
 *
 * <p>A pumpjack is ten blocks with one block entity, in the middle, where nothing can stand next
 * to it. Registering against the block lets any cell answer, resolving the anchor by arithmetic.
 *
 * <h2>Fluid has a place, and the rest of the machine does not offer it</h2>
 *
 * <p>The outlet is one face of one cell, and it turns with the machine - see
 * {@link PumpjackShape} and {@link OffshorePumpShape}. A pipe anywhere else gets nothing, sees
 * where the outlet is, and moves. <b>Electricity is not sided.</b> A pole may meet the machine
 * anywhere along it. The offshore pump has none to offer: Factorio's needs no power.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        PumpjackBlock pumpjack = ModBlocks.PUMPJACK.get();

        anywhere(event, Capabilities.Energy.BLOCK, pumpjack,
                PumpjackBlockEntity.class, (be, side) -> be.gridView());

        atPort(event, pumpjack, PumpjackShape.OUTPUT, PumpjackBlockEntity.class,
                PumpjackBlockEntity::output);

        OffshorePumpBlock offshorePump = ModBlocks.OFFSHORE_PUMP.get();

        atPort(event, offshorePump, OffshorePumpShape.OUTPUT, OffshorePumpBlockEntity.class,
                OffshorePumpBlockEntity::output);
    }

    /** Offered by every block of the machine, on every face. */
    private static <T, C extends @Nullable Object, B extends Block & Multiblock.MachineBlock,
            E extends BlockEntity> void anywhere(
            RegisterCapabilitiesEvent event, BlockCapability<T, C> capability, B block,
            Class<E> type, BiFunction<E, C, @Nullable T> view) {
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            E machine = anchor(level, pos, state, block, type);
            return machine == null ? null : view.apply(machine, context);
        }, block);
    }

    /** Offered only at the faces the shape names, which turn with the machine. */
    private static <B extends Block & Multiblock.MachineBlock, E extends BlockEntity> void atPort(
            RegisterCapabilitiesEvent event, B block, String port, Class<E> type,
            Function<E, ResourceHandler<FluidResource>> view) {
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (!block.shape().hasPort(port, Multiblock.part(block, state), side,
                    block.facing(state))) {
                return null;
            }
            E machine = anchor(level, pos, state, block, type);
            return machine == null ? null : view.apply(machine);
        }, block);
    }

    /**
     * The block entity this cell belongs to, or null.
     *
     * <p>Never asks for a block entity in an unloaded chunk - asking loads it, and a machine at
     * the edge of the loaded world would drag its neighbour in. See {@code docs/PITFALLS.md}.
     */
    private static <E extends BlockEntity> @Nullable E anchor(Level level, BlockPos pos,
            BlockState state, Multiblock.MachineBlock block, Class<E> type) {
        BlockPos anchor = Multiblock.anchorPos(block, state, pos);
        if (!level.isLoaded(anchor)) {
            return null;
        }
        BlockEntity found = level.getBlockEntity(anchor);
        return type.isInstance(found) ? type.cast(found) : null;
    }
}
