package com.jaguarm.nauvisresearch.registry;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.lab.LabBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(NauvisResearch.MODID);

    /**
     * The lab: three tiles by three, the size Factorio made it.
     *
     * <p>No facing property. A Factorio lab has no direction - it is fed from wherever the belt
     * happens to run, and there is no output to point anywhere.
     */
    public static final DeferredBlock<LabBlock> LAB = BLOCKS.registerBlock(
            "lab",
            LabBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
