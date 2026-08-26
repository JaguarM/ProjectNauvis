package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;

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

    private ModBlockEntities() {}
}
