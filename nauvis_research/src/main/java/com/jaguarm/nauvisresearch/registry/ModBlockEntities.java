package com.jaguarm.nauvisresearch.registry;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.lab.LabBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisResearch.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> LAB =
            BLOCK_ENTITIES.register("lab",
                    () -> new BlockEntityType<>(LabBlockEntity::new, ModBlocks.LAB.get()));

    private ModBlockEntities() {}
}
