package com.jaguarm.nauvismachines.registry;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine1Block;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine2Block;
import com.jaguarm.nauvismachines.machine.furnace.ElectricFurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.SteelFurnaceBlock;
import com.jaguarm.nauvismachines.machine.furnace.StoneFurnaceBlock;

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
    public static final DeferredBlock<AssemblingMachine1Block> ASSEMBLING_MACHINE_1 = BLOCKS.registerBlock(
            "assembling_machine_1",
            AssemblingMachine1Block::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** The same machine at Factorio's second tier: crafting speed 0.75, 150 kW. Behind automation-2. */
    public static final DeferredBlock<AssemblingMachine2Block> ASSEMBLING_MACHINE_2 = BLOCKS.registerBlock(
            "assembling_machine_2",
            AssemblingMachine2Block::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * The stone furnace: five stone, two by two, and the first machine a new world builds.
     * Stone-hard and stone-sounding, and it gives light while it burns, as vanilla's does.
     */
    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE = BLOCKS.registerBlock(
            "stone_furnace",
            StoneFurnaceBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .sound(SoundType.STONE)
                    .lightLevel(FurnaceBlock::lightLevel)
                    .requiresCorrectToolForDrops());

    /** The steel furnace: the same two by two at twice the speed, in steel. Behind advanced-material-processing. */
    public static final DeferredBlock<SteelFurnaceBlock> STEEL_FURNACE = BLOCKS.registerBlock(
            "steel_furnace",
            SteelFurnaceBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(FurnaceBlock::lightLevel)
                    .requiresCorrectToolForDrops());

    /** The electric furnace: three by three on the grid, no fuel. Behind advanced-material-processing-2. */
    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock(
            "electric_furnace",
            ElectricFurnaceBlock::new,
            properties -> properties
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(FurnaceBlock::lightLevel)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
