package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BasicSplitterBlock;
import com.jaguarm.nauvislogistics.belt.FastSplitterBlock;
import com.jaguarm.nauvislogistics.belt.FastTransportBeltBlock;
import com.jaguarm.nauvislogistics.belt.TransportBeltBlock;
import com.jaguarm.nauvislogistics.storage.IronChestBlock;
import com.jaguarm.nauvislogistics.storage.SteelChestBlock;
import com.jaguarm.nauvislogistics.transport.BurnerInserterBlock;
import com.jaguarm.nauvislogistics.transport.ElectricInserterBlock;
import com.jaguarm.nauvislogistics.transport.FastInserterBlock;
import com.jaguarm.nauvislogistics.transport.LongHandedInserterBlock;
import com.jaguarm.nauvislogistics.transport.StackInserterBlock;

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
                    .noOcclusion()
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
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    /**
     * The long arm: the same inserter reaching two blocks instead of one, so a line can be fed
     * over a belt, a walkway or a row of machines.
     *
     * <p>Costs an inserter to build, which is Factorio's recipe and the right shape for what it
     * is - not a faster inserter but a differently placed one.
     */
    public static final DeferredBlock<LongHandedInserterBlock> LONG_HANDED_INSERTER = BLOCKS.registerBlock(
            "long_handed_inserter",
            LongHandedInserterBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    /**
     * The fast inserter: the basic arm at nearly three times the speed, behind {@code fast-inserter}.
     * Two circuits, two plates and an inserter, which is Factorio's recipe.
     */
    public static final DeferredBlock<FastInserterBlock> FAST_INSERTER = BLOCKS.registerBlock(
            "fast_inserter",
            FastInserterBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    /**
     * The stack inserter: the fast inserter's swing with a hand that holds several, and grows with
     * research. Behind {@code stack-inserter}, which is behind the advanced circuit.
     */
    public static final DeferredBlock<StackInserterBlock> STACK_INSERTER = BLOCKS.registerBlock(
            "stack_inserter",
            StackInserterBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
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
     * The bigger box. Steel rather than iron, and half again as much room, which is the gap
     * Factorio has between its two.
     */
    public static final DeferredBlock<SteelChestBlock> STEEL_CHEST = BLOCKS.registerBlock(
            "steel_chest",
            SteelChestBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
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

    /**
     * The red belt: the same block twice as fast, and the only thing that differs is the number
     * on it and the colour of it.
     *
     * <p>Red rather than yellow because that is how a player reads a bus at a glance, and it is
     * identity in the sense non-negotiable #1 means: the colour and the speed are how Factorio's
     * belts are told apart, and both are written down - the speed in {@code data/mapping.json} and
     * the palette in {@code texture-workshop/make_belt_textures.py}.
     */
    public static final DeferredBlock<FastTransportBeltBlock> FAST_TRANSPORT_BELT = BLOCKS.registerBlock(
            "fast_transport_belt",
            FastTransportBeltBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * The splitter: 2x1 multiblock balancing items across two belt tracks.
     */
    public static final DeferredBlock<BasicSplitterBlock> SPLITTER = BLOCKS.registerBlock(
            "splitter",
            BasicSplitterBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /**
     * The red splitter: the same machine at the red belt's speed, and the other half of what
     * {@code logistics-2} unlocks.
     *
     * <p>A red line that split through a yellow splitter would be throttled to half its throughput
     * at every split, which is why Factorio ships a splitter with each belt tier and why this is
     * not optional decoration. It costs one class and this entry - see {@code FastSplitterBlock}
     * for what had to change first.
     */
    public static final DeferredBlock<FastSplitterBlock> FAST_SPLITTER = BLOCKS.registerBlock(
            "fast_splitter",
            FastSplitterBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    private ModBlocks() {}
}
