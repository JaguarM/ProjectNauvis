package com.jaguarm.nauvisfluids;

import com.jaguarm.nauvisfluids.pumpjack.PumpjackScreen;
import com.jaguarm.nauvisfluids.client.CrudeOilRenderer;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantScreen;
import com.jaguarm.nauvisfluids.refinery.OilRefineryScreen;
import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvisfluids.registry.ModMenus;
import com.jaguarm.nauvisfluids.registry.ModFluids;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** The client half: what steam, crude oil and natural water look like, and the oil x-ray. */
@Mod(value = NauvisFluids.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NauvisFluids.MODID, value = Dist.CLIENT)
public class NauvisFluidsClient {

    /** Pale, faintly warm white. Steam is water that has stopped looking like water. */
    private static final int STEAM_TINT = 0xFFD8E8F0;

    /** Not quite black, so it still reads as a liquid with a surface rather than as a hole. */
    private static final int CRUDE_OIL_TINT = 0xFF1A1418;

    /**
     * Factorio's base colours for the rest, out of its fluid prototypes: heavy oil
     * {@code (0.5, 0.13, 0)}, light oil {@code (0.57, 0.33, 0)}, petroleum gas
     * {@code (0.3, 0.1, 0.3)}, lubricant {@code (0.15, 0.32, 0.03)}, sulfuric acid
     * {@code (0.75, 0.65, 0.1)}. A tank of each reads as the thing it is in Factorio.
     */
    private static final int HEAVY_OIL_TINT = 0xFF802100;
    private static final int LIGHT_OIL_TINT = 0xFF915400;
    private static final int PETROLEUM_GAS_TINT = 0xFF4D1A4D;
    private static final int LUBRICANT_TINT = 0xFF265208;
    private static final int SULFURIC_ACID_TINT = 0xFFBFA61A;

    public NauvisFluidsClient() {}

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(water(STEAM_TINT), ModFluids.STEAM.get());
        event.register(water(CRUDE_OIL_TINT), ModFluids.CRUDE_OIL.get());
        event.register(water(HEAVY_OIL_TINT), ModFluids.HEAVY_OIL.get());
        event.register(water(LIGHT_OIL_TINT), ModFluids.LIGHT_OIL.get());
        event.register(water(PETROLEUM_GAS_TINT), ModFluids.PETROLEUM_GAS.get());
        event.register(water(LUBRICANT_TINT), ModFluids.LUBRICANT.get());
        event.register(water(SULFURIC_ACID_TINT), ModFluids.SULFURIC_ACID.get());
        // Vanilla's own water model, sprite for sprite: still, flowing, the overlay drawn against
        // glass and leaves, and the biome tint. One model for both halves of the fluid, as
        // vanilla registers its own.
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                FluidTintSources.water()),
                ModFluids.WATER.get(), ModFluids.FLOWING_WATER.get());
    }

    private static FluidModel.Unbaked water(int tint) {
        return new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                null,
                FluidTintSources.constant(tint));
    }

    /** The two machine screens. The recipe list beside them is Facrafting's panel. */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.OIL_REFINERY.get(), OilRefineryScreen::new);
        event.register(ModMenus.CHEMICAL_PLANT.get(), ChemicalPlantScreen::new);
        event.register(ModMenus.PUMPJACK.get(), PumpjackScreen::new);
    }

    /** The x-ray: an outline drawn on every oil well while a pumpjack is in hand. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRUDE_OIL.get(), context -> new CrudeOilRenderer());
    }
}
