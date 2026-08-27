package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.storage.IronChestBlockEntity;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlockEntity;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NauvisLogistics.MODID);

    /**
     * Two types, because the two tiers save different things: the burner has a fuel slot and a
     * burn timer, the electric one has a charge. Everything else about an inserter - the swing,
     * the neighbour caches, the moving - is in the shared base class rather than duplicated.
     *
     * <p>The fast, filter and long-handed variants will differ in speed, reach and what they will
     * pick up, none of which is a different entity; they will share whichever of these two they
     * are powered like.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerInserterBlockEntity>>
            BURNER_INSERTER = BLOCK_ENTITIES.register(
                    "burner_inserter",
                    () -> new BlockEntityType<>(
                            BurnerInserterBlockEntity::new,
                            ModBlocks.BURNER_INSERTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricInserterBlockEntity>>
            ELECTRIC_INSERTER = BLOCK_ENTITIES.register(
                    "inserter",
                    () -> new BlockEntityType<>(
                            ElectricInserterBlockEntity::new,
                            ModBlocks.INSERTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronChestBlockEntity>> IRON_CHEST =
            BLOCK_ENTITIES.register(
                    "iron_chest",
                    () -> new BlockEntityType<>(
                            IronChestBlockEntity::new,
                            ModBlocks.IRON_CHEST.get()));

    private ModBlockEntities() {}
}
