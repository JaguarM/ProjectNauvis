package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What the rest of the world sees: the boiler's fuel slot, and the engine's charge.
 *
 * <p>The engine publishes {@code Capabilities.Energy.BLOCK}, which is NeoForge's, not ours - so
 * any cable or machine from any mod can draw from a steam engine without knowing what one is.
 * That is the whole reason PLAN.md chose FE over a first-party grid.
 */
@EventBusSubscriber(modid = NauvisPower.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.BOILER.get(),
                (boiler, side) -> boiler.fuelAccess());

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.STEAM_ENGINE.get(),
                (engine, side) -> engine.cableView());

        // Steam. NeoForge's fluid capability, not ours - so a pipe from nauvis_fluids can carry
        // it without either mod compiling against the other, exactly as FE does for the grid.
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModBlockEntities.BOILER.get(),
                (boiler, side) -> boiler.steamAccess());

        // Only along the engine's own axis, and null everywhere else. This is what makes an
        // engine's facing mean something: a row of them chains end to end, and nothing can feed
        // one from the side or from below.
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModBlockEntities.STEAM_ENGINE.get(),
                (engine, side) -> {
                    if (side == null) {
                        return engine.steamAccess();
                    }
                    Direction axis = engine.getBlockState().getValue(SteamEngineBlock.FACING);
                    return side.getAxis() == axis.getAxis() ? engine.steamAccess() : null;
                });
    }
}
