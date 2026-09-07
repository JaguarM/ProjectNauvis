package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.turret.GunTurretBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisMilitary.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GunTurretBlockEntity>> GUN_TURRET =
            BLOCK_ENTITIES.register("gun_turret",
                    () -> new BlockEntityType<>(GunTurretBlockEntity::new, ModBlocks.GUN_TURRET.get()));

    private ModBlockEntities() {}
}
