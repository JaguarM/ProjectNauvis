package com.jaguarm.nauvispower.registry;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.BoilerShape;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineShape;
import com.jaguarm.nauvispower.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * What the rest of the world sees: the boiler's fuel slot and its steam, and the engine's charge.
 *
 * <p>The engine publishes {@code Capabilities.Energy.BLOCK}, which is NeoForge's, not ours - so
 * any cable or machine from any mod can draw from a steam engine without knowing what one is.
 * That is the whole reason PLAN.md chose FE over a first-party grid. Steam moves through
 * {@code Capabilities.Fluid.BLOCK} for the same reason: a pipe from {@code nauvis_fluids} carries
 * it without either mod compiling against the other.
 *
 * <h2>Everything here is registered against the block, not the block entity</h2>
 *
 * <p>A boiler is seven blocks and an engine is seventeen, with one block entity each.
 * {@code registerBlockEntity} would publish at that one block, and for the engine that block is
 * the middle of a five-tile machine - somewhere nothing can stand next to. Registering against
 * the block lets any cell answer, resolving the anchor by arithmetic first.
 *
 * <h2>Steam has a place, and the rest of the machine does not offer it</h2>
 *
 * <p>This is what the footprints were for. A one-block boiler had to offer steam on all six sides
 * because it had one block to offer it from. Now:
 *
 * <ul>
 *   <li>a <b>boiler</b> gives steam at the back face of the block under its chimney, and nowhere
 *       else;
 *   <li>an <b>engine</b> takes and gives steam at the open ends of its spine - the two blocks the
 *       wall deliberately does not cover - and nowhere along its flanks.
 * </ul>
 *
 * <p>The ports live in the shapes, in the machine's own frame, and turn with the machine. A player
 * who puts the pipe in the wrong place gets nothing, sees where the opening is, and moves it; that
 * is a better game than a machine which accepts a pipe anywhere.
 *
 * <p><b>Fuel and energy are not sided.</b> A hopper, a cable or a pole may meet a boiler or an
 * engine anywhere along it, because there is nothing to learn from being fussy about coal.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        BoilerBlock boiler = ModBlocks.BOILER.get();
        SteamEngineBlock engine = ModBlocks.STEAM_ENGINE.get();

        anywhere(event, Capabilities.Item.BLOCK, boiler,
                BoilerBlockEntity.class, (be, side) -> be.fuelAccess());
        anywhere(event, Capabilities.Energy.BLOCK, engine,
                SteamEngineBlockEntity.class, (be, side) -> be.cableView());

        atPort(event, boiler, BoilerShape.STEAM, BoilerBlockEntity.class,
                BoilerBlockEntity::steamAccess);
        atPort(event, engine, SteamEngineShape.STEAM, SteamEngineBlockEntity.class,
                SteamEngineBlockEntity::steamAccess);
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
     * the edge of the loaded world would drag its neighbour in. See {@code docs/NEXT.md}.
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
