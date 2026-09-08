package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantBlock;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlock;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlock;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlock;
import com.jaguarm.nauvisfluids.refinery.OilRefineryBlock;
import com.jaguarm.nauvisfluids.tank.StorageTankBlock;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisFluids.MODID);

    /** A length of pipe. What it carries belongs to the run, not the block; see {@code FluidNetwork}. */
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock(
            "pipe",
            PipeBlock::new,
            properties -> properties
                    .noOcclusion()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops());

    /**
     * An oil well: Factorio's {@code crude-oil} resource, as a block of ground a pumpjack stands on.
     *
     * <p>Unbreakable and blast-proof like bedrock, so no tool and no explosion moves it; no loot
     * table, so nothing drops if something does; and pistons are refused, so it cannot be pushed
     * about. It is a solid block and not a fluid, so buckets, water and endermen never had a say.
     * The creative item is the map editor's, and nothing crafts one.
     */
    public static final DeferredBlock<CrudeOilBlock> CRUDE_OIL = BLOCKS.registerBlock(
            "crude_oil",
            CrudeOilBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.MUD)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    /** Three by three over a well, in dark metal. See {@link PumpjackBlock}. */
    public static final DeferredBlock<PumpjackBlock> PUMPJACK = BLOCKS.registerBlock(
            "pumpjack",
            PumpjackBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** The water in every lake and sea the world generates: Factorio's water tile. */
    public static final DeferredBlock<LiquidBlock> WATER = BLOCKS.registerBlock(
            "water",
            properties -> new LiquidBlock(ModFluids.WATER.get(), properties),
            properties -> properties
                    .mapColor(MapColor.WATER)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY));

    /** One by two at the water's edge, in iron. See {@link OffshorePumpBlock}. */
    public static final DeferredBlock<OffshorePumpBlock> OFFSHORE_PUMP = BLOCKS.registerBlock(
            "offshore_pump",
            OffshorePumpBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Three by three, twenty-five thousand of one fluid. See {@link StorageTankBlock}. */
    public static final DeferredBlock<StorageTankBlock> STORAGE_TANK = BLOCKS.registerBlock(
            "storage_tank",
            StorageTankBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Five by five, crude oil in and three oils out. See {@link OilRefineryBlock}. */
    public static final DeferredBlock<OilRefineryBlock> OIL_REFINERY = BLOCKS.registerBlock(
            "oil_refinery",
            OilRefineryBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Three by three, every chemistry recipe. See {@link ChemicalPlantBlock}. */
    public static final DeferredBlock<ChemicalPlantBlock> CHEMICAL_PLANT = BLOCKS.registerBlock(
            "chemical_plant",
            ChemicalPlantBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
