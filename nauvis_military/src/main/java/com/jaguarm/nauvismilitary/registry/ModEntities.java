package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.weapon.Grenade;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, NauvisMilitary.MODID);

    /** The thrown grenade: a snowball's size, tracked the way a snowball is. */
    public static final DeferredHolder<EntityType<?>, EntityType<Grenade>> GRENADE = ENTITY_TYPES.register("grenade",
            () -> EntityType.Builder.<Grenade>of(Grenade::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, "grenade"))));

    private ModEntities() {}
}
