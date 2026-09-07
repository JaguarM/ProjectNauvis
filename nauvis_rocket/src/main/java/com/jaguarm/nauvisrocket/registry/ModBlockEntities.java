package com.jaguarm.nauvisrocket.registry;

import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisRocket.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RocketSiloBlockEntity>> ROCKET_SILO =
            BLOCK_ENTITIES.register("rocket_silo",
                    () -> new BlockEntityType<>(RocketSiloBlockEntity::new, ModBlocks.ROCKET_SILO.get()));

    private ModBlockEntities() {}
}
