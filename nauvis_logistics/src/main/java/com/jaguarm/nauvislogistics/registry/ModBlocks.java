package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.storage.IronChestBlock;
import com.jaguarm.nauvislogistics.transport.InserterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisLogistics.MODID);

    /**
     * The burner inserter, and for now the only one.
     *
     * <p>The electric {@code inserter} costs an electronic circuit and runs on power, so it waits
     * for {@code nauvis_power}. Registering it now would mean an item that works without the grid
     * it is supposed to need, which is the kind of thing that quietly ruins progression.
     */
    public static final DeferredBlock<InserterBlock> BURNER_INSERTER = BLOCKS.registerBlock(
            "burner_inserter",
            InserterBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * A bigger box. Not a vanilla chest subclass: vanilla's is welded to its double-chest
     * pairing and its animated lid renderer, neither of which an iron chest wants.
     */
    public static final DeferredBlock<IronChestBlock> IRON_CHEST = BLOCKS.registerBlock(
            "iron_chest",
            IronChestBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
