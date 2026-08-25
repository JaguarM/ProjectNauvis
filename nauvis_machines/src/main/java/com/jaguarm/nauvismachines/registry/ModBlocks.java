package com.jaguarm.nauvismachines.registry;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisMachines.MODID);

    /**
     * The first machine, and the point of milestone 1: a chest feeds it, it makes something,
     * a chest takes what comes out.
     *
     * <p>No facing property. A Factorio assembler has no direction — what goes in and what
     * comes out is decided by the inserters around it, not by the machine.
     */
    public static final DeferredBlock<AssemblerBlock> ASSEMBLING_MACHINE_1 = BLOCKS.registerBlock(
            "assembling_machine_1",
            AssemblerBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
