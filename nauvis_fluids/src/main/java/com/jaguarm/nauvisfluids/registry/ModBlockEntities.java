package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.pipe.PipeBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisFluids.MODID);

    /** A pipe holds no data of its own - this exists so a pipe can join and leave its run. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE =
            BLOCK_ENTITIES.register("pipe",
                    () -> new BlockEntityType<>(PipeBlockEntity::new, ModBlocks.PIPE.get()));

    private ModBlockEntities() {}
}
