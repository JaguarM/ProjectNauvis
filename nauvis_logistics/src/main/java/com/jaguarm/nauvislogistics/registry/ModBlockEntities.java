package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlockEntity;
import com.jaguarm.nauvislogistics.belt.SplitterBlockEntity;
import com.jaguarm.nauvislogistics.storage.MetalChestBlockEntity;
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
                            ModBlocks.INSERTER.get(),
                            ModBlocks.LONG_HANDED_INSERTER.get(),
                            ModBlocks.FAST_INSERTER.get(),
                            ModBlocks.BULK_INSERTER.get()));

    /**
     * One type per chest tier, which is the opposite of the inserters above and for a reason: an
     * inserter tier differs by numbers the block already knows, while a chest tier differs by how
     * much it saves. A steel chest loaded against the iron type would read thirty-six of its
     * fifty-four slots and quietly drop the rest.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalChestBlockEntity>> IRON_CHEST =
            BLOCK_ENTITIES.register(
                    "iron_chest",
                    () -> new BlockEntityType<>(
                            MetalChestBlockEntity::new,
                            ModBlocks.IRON_CHEST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalChestBlockEntity>> STEEL_CHEST =
            BLOCK_ENTITIES.register(
                    "steel_chest",
                    () -> new BlockEntityType<>(
                            MetalChestBlockEntity::new,
                            ModBlocks.STEEL_CHEST.get()));

    /** One type for every belt tier there will ever be. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>>
            TRANSPORT_BELT = BLOCK_ENTITIES.register(
                    "transport_belt",
                    () -> new BlockEntityType<>(
                            BeltBlockEntity::new,
                            ModBlocks.TRANSPORT_BELT.get(),
                            ModBlocks.FAST_TRANSPORT_BELT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SplitterBlockEntity>>
            SPLITTER = BLOCK_ENTITIES.register(
                    "splitter",
                    () -> new BlockEntityType<>(
                            SplitterBlockEntity::new,
                            ModBlocks.SPLITTER.get(),
                            ModBlocks.FAST_SPLITTER.get()));

    private ModBlockEntities() {}
}
