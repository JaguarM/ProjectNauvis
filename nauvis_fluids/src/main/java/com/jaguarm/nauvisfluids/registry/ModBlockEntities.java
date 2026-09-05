package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.pipe.PipeBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlockEntity;

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

    /** How much is left in a well. One number, and no ticker. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrudeOilBlockEntity>> CRUDE_OIL =
            BLOCK_ENTITIES.register("crude_oil",
                    () -> new BlockEntityType<>(CrudeOilBlockEntity::new, ModBlocks.CRUDE_OIL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK =
            BLOCK_ENTITIES.register("pumpjack",
                    () -> new BlockEntityType<>(PumpjackBlockEntity::new, ModBlocks.PUMPJACK.get()));

    private ModBlockEntities() {}
}
