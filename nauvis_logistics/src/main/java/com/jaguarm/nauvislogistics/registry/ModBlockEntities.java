package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.storage.IronChestBlockEntity;
import com.jaguarm.nauvislogistics.transport.InserterBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisLogistics.MODID);

    /**
     * One type for every inserter. The electric, fast, filter and long variants differ in speed,
     * reach and what they will pick up, none of which is a different entity.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InserterBlockEntity>> INSERTER =
            BLOCK_ENTITIES.register(
                    "inserter",
                    () -> new BlockEntityType<>(
                            InserterBlockEntity::new,
                            ModBlocks.BURNER_INSERTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronChestBlockEntity>> IRON_CHEST =
            BLOCK_ENTITIES.register(
                    "iron_chest",
                    () -> new BlockEntityType<>(
                            IronChestBlockEntity::new,
                            ModBlocks.IRON_CHEST.get()));

    private ModBlockEntities() {}
}
