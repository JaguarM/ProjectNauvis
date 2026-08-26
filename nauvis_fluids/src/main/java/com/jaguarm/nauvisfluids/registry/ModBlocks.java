package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisFluids.MODID);

    /**
     * A length of pipe. It carries nothing.
     *
     * <p>Not a placeholder for a missing feature so much as an ingredient that happens to be
     * placeable: the boiler costs four of these and the steam engine five, and milestone 4 is
     * where a pipe starts moving fluid. Until then it is a plain block with the right id and the
     * right recipe, which is the part that has to be right from the first commit.
     */
    public static final DeferredBlock<Block> PIPE = BLOCKS.registerSimpleBlock(
            "pipe",
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
