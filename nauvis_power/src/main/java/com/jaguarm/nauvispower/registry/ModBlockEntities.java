package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.SolarPanelBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;
import com.jaguarm.nauvispower.grid.ElectricPoleBlockEntity;
import com.jaguarm.nauvispower.storage.AccumulatorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisPower.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER =
            BLOCK_ENTITIES.register("boiler",
                    () -> new BlockEntityType<>(BoilerBlockEntity::new, ModBlocks.BOILER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE =
            BLOCK_ENTITIES.register("steam_engine",
                    () -> new BlockEntityType<>(SteamEngineBlockEntity::new, ModBlocks.STEAM_ENGINE.get()));

    /**
     * One type for every pole tier, the way the two electric inserters share one: a tier differs
     * by how far it reaches, which is a number on the block, and nothing saved here changes.
     *
     * <p>The registry name stays {@code small_electric_pole} because it is in world saves. It
     * names the type rather than the block, and the type is now both of them.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricPoleBlockEntity>>
            ELECTRIC_POLE = BLOCK_ENTITIES.register("small_electric_pole",
                    () -> new BlockEntityType<>(
                            ElectricPoleBlockEntity::new,
                            ModBlocks.SMALL_ELECTRIC_POLE.get(),
                            ModBlocks.MEDIUM_ELECTRIC_POLE.get(),
                            ModBlocks.BIG_ELECTRIC_POLE.get(),
                            ModBlocks.SUBSTATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel",
                    () -> new BlockEntityType<>(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AccumulatorBlockEntity>> ACCUMULATOR =
            BLOCK_ENTITIES.register("accumulator",
                    () -> new BlockEntityType<>(AccumulatorBlockEntity::new, ModBlocks.ACCUMULATOR.get()));

    private ModBlockEntities() {}
}
