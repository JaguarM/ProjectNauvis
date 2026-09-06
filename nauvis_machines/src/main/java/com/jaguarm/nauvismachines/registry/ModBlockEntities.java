package com.jaguarm.nauvismachines.registry;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisMachines.MODID);

    /**
     * One type for every assembler tier. Tiers 2 and 3 differ in speed and module slots, not
     * in what the entity does, so they will join this list rather than bring their own.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER =
            BLOCK_ENTITIES.register(
                    "assembler",
                    () -> new BlockEntityType<>(
                            AssemblerBlockEntity::new,
                            ModBlocks.ASSEMBLING_MACHINE_1.get(),
                            ModBlocks.ASSEMBLING_MACHINE_2.get()));

    /** One type for every furnace: the two burners and the electric one differ by numbers on the block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnaceBlockEntity>> FURNACE =
            BLOCK_ENTITIES.register(
                    "furnace",
                    () -> new BlockEntityType<>(
                            FurnaceBlockEntity::new,
                            ModBlocks.STONE_FURNACE.get(),
                            ModBlocks.STEEL_FURNACE.get(),
                            ModBlocks.ELECTRIC_FURNACE.get()));

    private ModBlockEntities() {}
}
