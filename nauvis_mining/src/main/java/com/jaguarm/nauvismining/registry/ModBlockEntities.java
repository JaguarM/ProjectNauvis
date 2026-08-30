package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisMining.MODID);

    /** One type backs both drills; the entity reads its tier from the block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MinerBlockEntity>> MINER =
            BLOCK_ENTITIES.register(
                    "miner",
                    () -> new BlockEntityType<>(
                            MinerBlockEntity::new,
                            ModBlocks.DRILLS.values().stream()
                                    .map(net.neoforged.neoforge.registries.DeferredBlock::get)
                                    .toArray(net.minecraft.world.level.block.Block[]::new)));

    private ModBlockEntities() {}
}
