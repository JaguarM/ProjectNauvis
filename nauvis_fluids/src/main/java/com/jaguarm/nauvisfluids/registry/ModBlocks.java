package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.pipe.PipeBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisFluids.MODID);

    /**
     * A length of pipe, which carries steam.
     *
     * <p>It began as an ingredient that happened to be placeable - the boiler costs four of them -
     * and PLAN.md put moving fluid in milestone 4. Steam brought it forward, because a boiler and
     * a steam engine that can only be built touching each other is not Factorio's arrangement.
     * What it carries belongs to the run rather than to the block; see {@code FluidNetwork}.
     */
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock(
            "pipe",
            PipeBlock::new,
            properties -> properties
                    .noOcclusion()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
