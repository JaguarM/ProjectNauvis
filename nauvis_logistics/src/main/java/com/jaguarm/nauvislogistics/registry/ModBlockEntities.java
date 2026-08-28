package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltBlockEntity;
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
     * <p>The long-handed inserter is the first variant to prove that: it differs in speed, draw
     * and reach, all three of which are numbers on the block, so it is registered against the
     * electric type below rather than getting one of its own. The fast and filter arms will
     * arrive the same way, each sharing whichever of these two it is powered like.
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
                            ModBlocks.LONG_HANDED_INSERTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronChestBlockEntity>> IRON_CHEST =
            BLOCK_ENTITIES.register(
                    "iron_chest",
                    () -> new BlockEntityType<>(
                            IronChestBlockEntity::new,
                            ModBlocks.IRON_CHEST.get()));

    /**
     * One type for every belt tier there will ever be.
     *
     * <p>A belt block entity holds no behaviour at all - the run does the work and a belt is a
     * fact about where the run goes - so what differs between tiers is the block, not this.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>>
            TRANSPORT_BELT = BLOCK_ENTITIES.register(
                    "transport_belt",
                    () -> new BlockEntityType<>(
                            BeltBlockEntity::new,
                            ModBlocks.TRANSPORT_BELT.get()));

    private ModBlockEntities() {}
}
