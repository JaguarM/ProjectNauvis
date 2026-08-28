package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.TransportBeltBlock;
import com.jaguarm.nauvislogistics.storage.IronChestBlock;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlock;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisLogistics.MODID);

    /** The one you can build before there is a grid. */
    public static final DeferredBlock<BurnerInserterBlock> BURNER_INSERTER = BLOCKS.registerBlock(
            "burner_inserter",
            BurnerInserterBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * The electric inserter: an electronic circuit, and useless without a pole in range.
     *
     * <p>It was deliberately left unregistered until {@code nauvis_power} could supply it. An
     * electric inserter that ran on nothing would be strictly better than the burner for free,
     * and progression that can be skipped is progression that will be.
     */
    public static final DeferredBlock<ElectricInserterBlock> INSERTER = BLOCKS.registerBlock(
            "inserter",
            ElectricInserterBlock::new,
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

    /**
     * The belt. Half a block high so a player walks across it rather than over it, and soft
     * enough to break with a hand - a belt line is something a player re-routes constantly, and a
     * pickaxe requirement would make laying one out a chore.
     */
    public static final DeferredBlock<TransportBeltBlock> TRANSPORT_BELT = BLOCKS.registerBlock(
            "transport_belt",
            TransportBeltBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    private ModBlocks() {}
}
